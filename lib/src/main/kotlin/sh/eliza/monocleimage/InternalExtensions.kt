package sh.eliza.monocleimage

import java.util.BitSet
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.ulp

internal fun Double.approximatelyEquals(other: Double) =
  abs(this - other) < kotlin.math.max(ulp, other.ulp) * 2

internal fun BitSet.toSet(): Set<Int> {
  val result = mutableSetOf<Int>()
  var i = nextSetBit(0)
  while (i >= 0) {
    result.add(i)
    i = nextSetBit(i + 1)
  }
  return result.toSet()
}

internal fun Set<Int>.toBitSet(nbits: Int = 0) =
  BitSet(nbits)
    .also { bitSet ->
      for (value in this) {
        bitSet.set(value)
      }
    }
    .toByteArray()
    .toList()

internal fun BitSet.isSubset(other: BitSet) = (clone() as BitSet).apply { and(other) } == this
