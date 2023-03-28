package sh.eliza.monocleimageprototype

import java.util.UUID

object Constants {
  val SERVICE_UUID = UUID(0x99d07f567cead38eu.toLong(), 0x17c2375c9a985129u.toLong())
  val SERVICE_RX_CHARACTERISTIC_UUID =
    UUID(0x420329460c3a4021u.toLong(), 0x5b390c43473b0747u.toLong())
  val SERVICE_TX_CHARACTERISTIC_UUID =
    UUID(0x1ce681c84ba561d4u.toLong(), 0xbeed06dd34848207u.toLong())
  const val MTU = 512
  const val SERVER_MAC_ADDRESS_KEY = "serverMacAddress"
}
