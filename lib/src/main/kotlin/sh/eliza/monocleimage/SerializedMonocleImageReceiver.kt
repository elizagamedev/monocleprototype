package sh.eliza.monocleimage

import java.util.Arrays
import java.util.BitSet

class AwaitedRowsBitSets(
  val all: BitSet,
  val y: BitSet,
)

/** Keeps track of receiving a serialized monocle image over the wire. */
class SerializedMonocleImageReceiver {
  enum class Status {
    /** Can't display anything. */
    NEEDS_MORE_DATA,
    NEEDS_COLOR,
    DONE,
  }

  var status = Status.NEEDS_MORE_DATA
    private set

  var awaitedRowsBitSets: AwaitedRowsBitSets? = null
  private val receivedRows = mutableMapOf<Int, MonocleImage.Row>()
  private val receivedRowIndicesBitSet = BitSet(MonocleImage.HEIGHT * 2)

  val confirmationResponse
    get() = receivedRowIndicesBitSet.toByteArray()

  /** Process the next data packet received from the peer. */
  fun push(payload: ByteArray, isResponseNeeded: Boolean) {
    if (isResponseNeeded) {
      // Completely reset.
      awaitedRowsBitSets =
        AwaitedRowsBitSets(
          all = BitSet.valueOf(payload),
          y = BitSet.valueOf(Arrays.copyOfRange(payload, 0, MonocleImage.HEIGHT * 2 / 8)),
        )
      receivedRows.clear()
      receivedRowIndicesBitSet.clear()
      status = Status.NEEDS_MORE_DATA
      return
    }

    // Parse all rows out of the packet.
    val numberOfRows = payload[0].toInt() and 0xFF
    var o = SerializedMonocleImage.METADATA_SIZE
    for (i in 0 until numberOfRows) {
      val header =
        (payload[o + 0].toInt() and 0xFF) or
          ((payload[o + 1].toInt() and 0xFF) shl 8) or
          ((payload[o + 2].toInt() and 0xFF) shl 16) or
          ((payload[o + 3].toInt() and 0xFF) shl 24)
      o += SerializedMonocleImage.ROW_HEADER_SIZE

      val isCompressed = (header and 0x80000000.toInt()) != 0
      val index = (header shr 18) and 0x7FF
      val offset = (header shr 9) and 0x1FF
      val size = (header shr 0) and 0x1FF
      val data = payload.slice(o until (o + size))
      o += size

      receivedRows[index] = MonocleImage.Row(offset, data, isCompressed)
      receivedRowIndicesBitSet.set(index)
    }
    require(o == payload.size) { "Payload contains extra data." }

    awaitedRowsBitSets?.let { bitSets ->
      status =
        when {
          bitSets.all == receivedRowIndicesBitSet -> Status.DONE
          bitSets.y.isSubset(receivedRowIndicesBitSet) -> Status.NEEDS_COLOR
          else -> Status.NEEDS_MORE_DATA
        }
    }
  }

  // TODO: throw exception if not done?
  fun toMonocleImage() =
    MonocleImage(
      yRows =
        receivedRows
          .entries
          .mapNotNull { (index, row) ->
            if (index < MonocleImage.HEIGHT * 2) {
              Pair(index, row)
            } else {
              null
            }
          }
          .toMap(),
      uvRows =
        receivedRows
          .entries
          .mapNotNull { (index, row) ->
            if (index >= MonocleImage.HEIGHT * 2) {
              Pair(index - MonocleImage.HEIGHT * 2, row)
            } else {
              null
            }
          }
          .toMap(),
    )
}
