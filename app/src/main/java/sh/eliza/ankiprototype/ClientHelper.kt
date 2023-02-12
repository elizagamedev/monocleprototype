package sh.eliza.ankiprototype

import android.bluetooth.BluetoothManager
import android.content.Context

private const val TAG = "ClientHelper"

class ClientHelper(private val context: Context) : AutoCloseable {
  private val bluetoothManager =
    context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

  override fun close() {}
}
