package sh.eliza.monocleimage

import heatshrink.HsInputStream
import heatshrink.HsOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

private const val HEATSHRINK_WINDOW_SIZE = 9
private const val HEATSHRINK_LOOKAHEAD_SIZE = 8

// Conversions to/from full-range YCbCr.
// Source: https://web.archive.org/web/20180421030430/http://www.equasys.de/colorconversion.html

private fun rgb2y(r: Int, g: Int, b: Int) = (0.299 * r + 0.587 * g + 0.114 * b).coerceIn(0.0, 255.0)

private fun rgb2u(r: Int, g: Int, b: Int) =
  (128.0 - 0.169 * r - 0.331 * g + 0.500 * b).coerceIn(0.0, 255.0)

private fun rgb2v(r: Int, g: Int, b: Int) =
  (128.0 + 0.500 * r - 0.419 * g - 0.081 * b).coerceIn(0.0, 255.0)

private fun yuv2r(y: Int, u: Int, v: Int) =
  (1.000 * y + 0.0000 * (u - 128.0) + 1.400 * (v - 128.0)).coerceIn(0.0, 255.0)

private fun yuv2g(y: Int, u: Int, v: Int) =
  (1.000 * y + -0.343 * (u - 128.0) + -0.711 * (v - 128.0)).coerceIn(0.0, 255.0)

private fun yuv2b(y: Int, u: Int, v: Int) =
  (1.000 * y + 1.765 * (u - 128.0) + 0.000 * (v - 128.0)).coerceIn(0.0, 255.0)

private fun compress(input: List<Byte>): List<Byte> {
  val byteStream = ByteArrayOutputStream(input.size)
  val stream = HsOutputStream(byteStream, HEATSHRINK_WINDOW_SIZE, HEATSHRINK_LOOKAHEAD_SIZE)
  stream.write(input.toByteArray())
  stream.flush()
  return byteStream.toByteArray().toList()
}

private fun decompress(input: List<Byte>): List<Byte> {
  val result = mutableListOf<Byte>()
  val buffer = ByteArray(input.size)

  HsInputStream(
      ByteArrayInputStream(input.toByteArray()),
      HEATSHRINK_WINDOW_SIZE,
      HEATSHRINK_LOOKAHEAD_SIZE
    )
    .use { stream ->
      while (true) {
        val count = stream.read(buffer)
        if (count <= 0) {
          break
        }
        result.addAll(buffer.take(count))
      }
    }

  return result.toList()
}

private class RowBuffer(
  private val emptyValue: Double,
) {
  private val emptyValueByte = emptyValue.toInt().toByte()

  var offset: Int? = null
  var emptyPairCount = 0
  var data = mutableListOf<Byte>()

  fun push(colPair: Int, b1: Double, b2: Double) {
    if (b1.approximatelyEquals(emptyValue) && b2.approximatelyEquals(emptyValue)) {
      emptyPairCount++
      return
    }

    if (offset === null) {
      offset = colPair
    } else if (emptyPairCount > 0) {
      // Backfill with skipped blank pairs.
      data.addAll(generateSequence { emptyValueByte }.take(emptyPairCount * 2))
    }
    emptyPairCount = 0

    data.add(b1.toInt().toByte())
    data.add(b2.toInt().toByte())
  }

  fun toRow() =
    offset?.let { offset ->
      val compressedData = compress(data)
      val isCompressed = compressedData.size < data.size
      MonocleImage.Row(
        offset,
        if (isCompressed) {
          compressedData
        } else {
          data
        },
        isCompressed
      )
    }
}

data class MonocleImage(
  val yRows: Map<Int, Row>,
  val uvRows: Map<Int, Row>,
) {
  data class Row(
    val offset: Int,
    val data: List<Byte>,
    val isCompressed: Boolean,
  ) {
    fun decompress() =
      if (isCompressed) {
        decompress(data)
      } else {
        data
      }
  }

  fun toRgb(setRgb: (row: Int, col: Int, rgb: Int) -> Unit) {
    val yEmptyPair = Pair(0, 0)
    val uvEmptyPair = Pair(0x80, 0x80)

    for (row in (yRows.keys + uvRows.keys).distinct()) {
      val deinterlacedRow = row / 2
      val deinterlacedColOffset = (row % 2) * WIDTH / 2

      val yRow = yRows[row]
      val uvRow = uvRows[row]

      val yData = yRow?.decompress() ?: emptyList()
      val uvData = uvRow?.decompress() ?: emptyList()

      fun Row.pairAt(
        colPair: Int,
        decompressedData: List<Byte>,
        emptyPair: Pair<Int, Int>
      ): Pair<Int, Int> {
        val index = (colPair - offset) * 2
        return if (index in decompressedData.indices) {
          Pair(
            decompressedData[index + 0].toInt() and 0xFF,
            decompressedData[index + 1].toInt() and 0xFF
          )
        } else {
          emptyPair
        }
      }

      for (colPair in
        min(yRow?.offset ?: WIDTH / 4, uvRow?.offset ?: WIDTH / 4) until
          max((yRow?.offset ?: 0) + yData.size / 2, (uvRow?.offset ?: 0) + uvData.size / 2)) {
        val deinterlacedCol = deinterlacedColOffset + colPair * 2

        val (y1, y2) = yRow?.pairAt(colPair, yData, yEmptyPair) ?: yEmptyPair
        val (u, v) = uvRow?.pairAt(colPair, uvData, uvEmptyPair) ?: uvEmptyPair

        val r1 = yuv2r(y1, u, v).toInt()
        val g1 = yuv2g(y1, u, v).toInt()
        val b1 = yuv2b(y1, u, v).toInt()
        val r2 = yuv2r(y2, u, v).toInt()
        val g2 = yuv2g(y2, u, v).toInt()
        val b2 = yuv2b(y2, u, v).toInt()

        val rgb1 = (r1 shl 16) or (g1 shl 8) or (b1 shl 0)
        val rgb2 = (r2 shl 16) or (g2 shl 8) or (b2 shl 0)

        setRgb(deinterlacedRow, deinterlacedCol + 0, rgb1)
        setRgb(deinterlacedRow, deinterlacedCol + 1, rgb2)
      }
    }
  }

  companion object {
    const val WIDTH = 640
    const val HEIGHT = 400

    fun createFromRgb(getRgb: (row: Int, col: Int) -> Int): MonocleImage {
      val yRows = mutableMapOf<Int, Row>()
      val uvRows = mutableMapOf<Int, Row>()

      for (row in 0 until HEIGHT * 2) {
        val deinterlacedRow = row / 2
        val deinterlacedColOffset = (row % 2) * WIDTH / 2

        val yBuffer = RowBuffer(0.0)
        val uvBuffer = RowBuffer(128.0)

        for (colPair in 0 until WIDTH / 4) {
          val deinterlacedCol = deinterlacedColOffset + colPair * 2

          val rgb1 = getRgb(deinterlacedRow, deinterlacedCol + 0)
          val rgb2 = getRgb(deinterlacedRow, deinterlacedCol + 1)

          val r1 = (rgb1 shr 16) and 0xFF
          val g1 = (rgb1 shr 8) and 0xFF
          val b1 = (rgb1 shr 0) and 0xFF
          val r2 = (rgb2 shr 16) and 0xFF
          val g2 = (rgb2 shr 8) and 0xFF
          val b2 = (rgb2 shr 0) and 0xFF

          val y1 = rgb2y(r1, g1, b1)
          val u1 = rgb2u(r1, g1, b1)
          val v1 = rgb2v(r1, g1, b1)
          val y2 = rgb2y(r2, g2, b2)
          val u2 = rgb2u(r2, g2, b2)
          val v2 = rgb2v(r2, g2, b2)

          val u = ((u1 + u2) / 2.0)
          val v = ((v1 + v2) / 2.0)

          yBuffer.push(colPair, y1, y2)
          uvBuffer.push(colPair, u, v)
        }

        yBuffer.toRow()?.let { yRows[row] = it }
        uvBuffer.toRow()?.let { uvRows[row] = it }
      }

      return MonocleImage(
        yRows.toMap(),
        uvRows.toMap(),
      )
    }
  }
}
