package sh.eliza.monocleimage

import java.util.BitSet

/** Keeps track of sending a serialized monocle image over the wire. */
class SerializedMonocleImageSender(
  val image: SerializedMonocleImage,
) {
  enum class PayloadKind {
    /** Starting to send a new image. */
    NEW_IMAGE,
    /** Sending unreliable row data. */
    DATA,
    /** Need to request confirmation of received rows. */
    CONFIRMATION,
  }

  data class Payload(
    val kind: PayloadKind,
    /** Payload data. */
    val data: List<Byte>,
  )

  var isDone = false
    private set

  private var isFirstPayload = true

  private val toSendQueue = ArrayDeque<SerializedMonocleImage.Payload>(image.payloads)

  /** Return the next payload to be sent over the wire. */
  fun next(): Payload {
    check(!isDone) { "Nothing left to send." }
    if (isFirstPayload) {
      // Start a new image stream.
      isFirstPayload = false
      return Payload(PayloadKind.NEW_IMAGE, image.allRowIndices.toBitSet(MonocleImage.HEIGHT * 2))
    }
    if (toSendQueue.isEmpty()) {
      // Ask the peer to confirm what they have.
      return Payload(PayloadKind.CONFIRMATION, emptyList())
    }
    // Send the next item in the queue.
    return Payload(PayloadKind.DATA, toSendQueue.removeFirst().data)
  }

  /** Process confirmation from peer. */
  fun onConfirmationResponse(data: ByteArray) {
    val confirmed = BitSet.valueOf(data).toSet()
    val difference = image.allRowIndices - confirmed
    if (difference.isEmpty()) {
      isDone = true
      return
    }
    val sending = mutableSetOf<Int>()
    for (index in difference) {
      if (index !in sending) {
        val payload = image.payloadByIndex[index]!!
        sending.addAll(payload.rowIndices)
        toSendQueue.addLast(payload)
      }
    }
  }
}
