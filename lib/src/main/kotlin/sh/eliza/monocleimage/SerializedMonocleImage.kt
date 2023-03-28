package sh.eliza.monocleimage

class SerializedMonocleImage
private constructor(
  val payloads: List<Payload>,
) {
  data class Payload(
    val rowIndices: Set<Int>,
    val data: List<Byte>,
  )

  val payloadByIndex =
    payloads.flatMap { payload -> payload.rowIndices.map { Pair(it, payload) } }.toMap()

  val allRowIndices = payloads.flatMap { it.rowIndices }.toSet()

  companion object {
    // 1 byte for number of rows in this payload.
    const val METADATA_SIZE = 1
    // 1 bit for compression status, 11 bits for row index, 9 bits for offset, 9 bits for size, 2
    // bits left over.
    const val ROW_HEADER_SIZE = 4
    // Must support metadata byte + row header + an interlaced row.
    const val MIN_MTU_SIZE = MonocleImage.WIDTH / 2 + METADATA_SIZE + ROW_HEADER_SIZE

    fun createFromMonocleImage(image: MonocleImage, mtu: Int): SerializedMonocleImage {
      require(mtu >= MIN_MTU_SIZE) { "MTU must be at least $MIN_MTU_SIZE" }

      // Bin pack, first fit.
      val payloads = mutableListOf<PayloadBuffer>()
      val rows =
        image.yRows.entries.map { (number, row) -> Pair(number, row) } +
          image.uvRows.entries.map { (number, row) -> Pair(number + MonocleImage.HEIGHT * 2, row) }
      for ((index, row) in rows) {
        val payloadToFill =
          payloads.firstOrNull { it.canFit(row) }
            ?: run {
              val newPayload = PayloadBuffer(mtu)
              payloads.add(newPayload)
              newPayload
            }
        payloadToFill.push(index, row)
      }

      return SerializedMonocleImage(payloads.map { it.toPayload() })
    }
  }
}

private class PayloadBuffer(
  private val mtu: Int,
) {
  var byteCount = SerializedMonocleImage.METADATA_SIZE
    private set

  val remainingCapacity
    get() = mtu - byteCount

  private val rows = mutableListOf<Pair<Int, MonocleImage.Row>>()

  fun canFit(row: MonocleImage.Row) =
    remainingCapacity >= row.data.size + SerializedMonocleImage.ROW_HEADER_SIZE && rows.size < 127

  fun push(index: Int, row: MonocleImage.Row) {
    val count = row.data.size + SerializedMonocleImage.ROW_HEADER_SIZE
    require(canFit(row)) {
      "Row does not fit within remaining MTU capacity ($count / $remainingCapacity)"
    }
    require(row.data.size < 512) { "Row size does not fit within 9 bits." }
    require(row.offset < 512) { "Row offset does not fit within 9 bits." }
    byteCount += count
    rows.add(Pair(index, row))
  }

  fun toPayload() =
    SerializedMonocleImage.Payload(
      rowIndices = rows.map { (index, _) -> index }.toSet(),
      data =
        listOf(rows.size.toByte()) +
          rows
            .flatMap { (index, row) ->
              val isCompressedBit =
                if (row.isCompressed) {
                  0x80000000.toInt()
                } else {
                  0x00000000
                }
              val header =
                isCompressedBit or
                  ((index and 0x7FF) shl 18) or
                  ((row.offset and 0x1FF) shl 9) or
                  ((row.data.size and 0x1FF) shl 0)
              listOf(
                (header and 0xFF).toByte(),
                ((header shr 8) and 0xFF).toByte(),
                ((header shr 16) and 0xFF).toByte(),
                ((header shr 24) and 0xFF).toByte(),
              ) + row.data
            }
            .toList()
    )
}
