package sh.eliza.monocleimageprototype

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.ParcelUuid
import android.util.Log
import sh.eliza.monocleimageprototype.Constants.SERVICE_RX_CHARACTERISTIC_UUID
import sh.eliza.monocleimageprototype.Constants.SERVICE_TX_CHARACTERISTIC_UUID
import sh.eliza.monocleimageprototype.Constants.SERVICE_UUID

private const val TAG = "ClientHelper"

private class WrappedWriteCallback(private val callback: ClientHelper.WriteCallback) {
  fun onWriteResult() {
    try {
      callback.onWriteResult()
    } catch (t: Throwable) {
      Log.i(TAG, "Unhandled exception in `onWriteResult'.", t)
    }
  }

  fun onWriteFailed() {
    try {
      callback.onWriteFailed()
    } catch (t: Throwable) {
      Log.i(TAG, "Unhandled exception in `onWriteFailed'.", t)
    }
  }
}

private class WrappedReadCallback(private val callback: ClientHelper.ReadCallback) {
  fun onReadResult(data: ByteArray) {
    try {
      callback.onReadResult(data)
    } catch (t: Throwable) {
      Log.i(TAG, "Unhandled exception in `onReadResult'.", t)
    }
  }

  fun onReadFailed() {
    try {
      callback.onReadFailed()
    } catch (t: Throwable) {
      Log.i(TAG, "Unhandled exception in `onReadFailed'.", t)
    }
  }
}

private class Characteristics(
  val rx: BluetoothGattCharacteristic,
  val tx: BluetoothGattCharacteristic,
)

class ClientHelper(
  private val context: Context,
  val mtu: Int,
  val serverMacAddress: String,
) : AutoCloseable {
  interface WriteCallback {
    fun onWriteResult()
    fun onWriteFailed()
  }

  interface ReadCallback {
    fun onReadResult(data: ByteArray)
    fun onReadFailed()
  }

  private val bluetoothManager =
    context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
  private val bluetoothAdapter = bluetoothManager.adapter
  private val bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner

  private var isStarted = false
  private var server: BluetoothGatt? = null
  private var characteristics: Characteristics? = null
  private var isReadyForIo = false

  private var writeCallback: WrappedWriteCallback? = null
  private var readCallback: WrappedReadCallback? = null

  private val scanSettings =
    ScanSettings.Builder().run {
      setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
      setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)
      setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH)
      build()
    }

  private val scanFilter =
    ScanFilter.Builder().run {
      if (!serverMacAddress.isEmpty()) {
        setDeviceAddress(serverMacAddress)
      }
      setServiceUuid(ParcelUuid(SERVICE_UUID))
      build()
    }

  private val gattCallback =
    object : BluetoothGattCallback() {
      override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        try {
          when (newState) {
            BluetoothProfile.STATE_CONNECTED -> {
              Log.i(TAG, "Connected. address=${gatt.device.address}")
              stopScanning()
              gatt.discoverServices()
            }
            BluetoothProfile.STATE_DISCONNECTED -> {
              Log.i(TAG, "Disconnected. address=${gatt.device.address}")
              gatt.close()
              if (gatt == server) {
                server = null
                isReadyForIo = false
                if (isStarted) {
                  startScanning()
                }
              }
            }
          }
        } catch (t: Throwable) {
          Log.w(TAG, "Unhandled exception during `onConnectionStateChange'", t)
          stop()
        }
      }

      override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        try {
          if (gatt != server) {
            Log.w(TAG, "onServicesDiscovered() called for device other than server.")
            return
          }
          if (status != BluetoothGatt.GATT_SUCCESS) {
            Log.w(TAG, "Failed to discover services. status=$status")
            stop()
            return
          }

          Log.i(TAG, "Services discovered.")
          val service = gatt.getService(SERVICE_UUID)
          if (service === null) {
            Log.w(TAG, "Failed to get image service.")
            stop()
            return
          }

          val rxCharacteristic = service.getCharacteristic(SERVICE_RX_CHARACTERISTIC_UUID)
          val txCharacteristic = service.getCharacteristic(SERVICE_TX_CHARACTERISTIC_UUID)

          if (rxCharacteristic === null || txCharacteristic === null) {
            Log.w(TAG, "Service characteristics not found.")
            stop()
            return
          }

          characteristics = Characteristics(rxCharacteristic, txCharacteristic)

          gatt.requestMtu(mtu + 3)
        } catch (t: Throwable) {
          Log.w(TAG, "Unhandled exception during `onServicesDiscovered'", t)
          stop()
        }
      }

      override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
        Log.i(TAG, "ATT MTU changed. mtu=$mtu, status=$status")
        isReadyForIo = true
        Log.i(TAG, "Ready for IO!")
      }

      override fun onCharacteristicWrite(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int
      ) {
        try {
          if (gatt != server) {
            Log.w(TAG, "onCharacteristicWrite() called for device other than server.")
            return
          }
          val rxCharacteristic = characteristics?.rx
          if (rxCharacteristic == null) {
            Log.w(TAG, "onCharacteristicWrite() called, but no rx characteristic found.")
            return
          }
          if (characteristic != rxCharacteristic) {
            Log.w(TAG, "onCharacteristicWrite() called for unknown characteristic.")
            return
          }
          writeCallback?.let {
            writeCallback = null
            if (status == BluetoothGatt.GATT_SUCCESS) {
              it.onWriteResult()
            } else {
              Log.w(TAG, "Write failed. status=$status")
              it.onWriteFailed()
            }
          }
            ?: Log.w(TAG, "Write callback not set.")
        } catch (t: Throwable) {
          Log.w(TAG, "Unhandled exception during `onCharacteristicWrite'", t)
          stop()
        }
      }

      override fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        status: Int
      ) {
        try {
          if (gatt != server) {
            Log.w(TAG, "onCharacteristicRead() called for device other than server.")
            return
          }
          val txCharacteristic = characteristics?.tx
          if (txCharacteristic == null) {
            Log.w(TAG, "onCharacteristicRead() called, but no rx characteristic found.")
            return
          }
          if (characteristic != txCharacteristic) {
            Log.w(TAG, "onCharacteristicRead() called for unknown characteristic.")
            return
          }
          readCallback?.let {
            readCallback = null
            if (status == BluetoothGatt.GATT_SUCCESS) {
              it.onReadResult(value)
            } else {
              Log.w(TAG, "Read failed. status=$status")
              it.onReadFailed()
            }
          }
            ?: Log.w(TAG, "Read callback not set.")
        } catch (t: Throwable) {
          Log.w(TAG, "Unhandled exception during `onCharacteristicRead'", t)
          stop()
        }
      }
    }

  private val scanCallback =
    object : ScanCallback() {
      override fun onScanResult(callbackType: Int, result: ScanResult) {
        try {
          Log.i(
            TAG,
            "Found. deviceName=${result.scanRecord?.deviceName}, address=${result.device.address}"
          )
          server?.disconnect()
          writeCallback?.let {
            it.onWriteFailed()
            writeCallback = null
          }
          characteristics = null
          server =
            result.device.connectGatt(
              context,
              /*autoConnect=*/ false,
              gattCallback,
              BluetoothDevice.TRANSPORT_LE
            )
        } catch (t: Throwable) {
          Log.w(TAG, "Unhandled exception during `onScanResult'", t)
          stop()
        }
      }

      override fun onScanFailed(errorCode: Int) {
        Log.w(TAG, "BLE scan failed. errorCode=$errorCode")
      }
    }

  private val bluetoothReceiver =
    object : BroadcastReceiver() {
      override fun onReceive(context: Context, intent: Intent) {
        val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF)

        when (state) {
          BluetoothAdapter.STATE_ON -> {
            Log.i(TAG, "Bluetooth turned on.")
            startScanning()
          }
          BluetoothAdapter.STATE_OFF -> {
            Log.i(TAG, "Bluetooth turned off.")
            isStarted = false
            isReadyForIo = false
            stop()
          }
        }
      }
    }

  init {
    check(context.hasBluetoothSupport())
    context.registerReceiver(bluetoothReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))

    if (bluetoothAdapter.isEnabled) {
      isStarted = true
      startScanning()
    }
  }

  override fun close() {
    isStarted = false
    isReadyForIo = false

    if (bluetoothAdapter.isEnabled) {
      stop()
    }
    context.unregisterReceiver(bluetoothReceiver)
  }

  fun write(value: ByteArray, writeType: Int, writeCallback: WriteCallback) {
    val wrappedWriteCallback = WrappedWriteCallback(writeCallback)
    if (!isReadyForIo) {
      Log.w(TAG, "Tried to write before connection was ready.")
      wrappedWriteCallback.onWriteFailed()
      return
    }
    val server = server
    if (server === null) {
      Log.w(TAG, "Tried to write without connection to server.")
      wrappedWriteCallback.onWriteFailed()
      return
    }
    val rxCharacteristic = characteristics?.rx
    if (rxCharacteristic === null) {
      Log.w(TAG, "Tried to write without rx characteristic.")
      wrappedWriteCallback.onWriteFailed()
      return
    }
    if (this.writeCallback !== null) {
      Log.w(TAG, "Previous write still in progress.")
      wrappedWriteCallback.onWriteFailed()
      return
    }
    if (value.size > 512) {
      // 512 is the maximum size, even with prepared writes. Pixel 5a's Bluetooth just
      // straight-up crashes in this case.
      Log.w(TAG, "Payload size exceeds limit.")
      wrappedWriteCallback.onWriteFailed()
      return
    }

    this.writeCallback = wrappedWriteCallback
    val result = server.writeCharacteristic(rxCharacteristic, value, writeType)
    if (result != BluetoothGatt.GATT_SUCCESS) {
      Log.w(TAG, "Write failed. code=$result")
      this.writeCallback = null
      wrappedWriteCallback.onWriteFailed()
      return
    }
  }

  fun read(readCallback: ReadCallback) {
    val wrappedReadCallback = WrappedReadCallback(readCallback)
    if (!isReadyForIo) {
      Log.w(TAG, "Tried to read before connection was ready.")
      wrappedReadCallback.onReadFailed()
      return
    }
    val server = server
    if (server === null) {
      Log.w(TAG, "Tried to read without connection to server.")
      wrappedReadCallback.onReadFailed()
      return
    }
    val txCharacteristic = characteristics?.tx
    if (txCharacteristic === null) {
      Log.w(TAG, "Tried to read without tx characteristic.")
      wrappedReadCallback.onReadFailed()
      return
    }

    if (this.readCallback !== null) {
      Log.w(TAG, "Previous read still in progress.")
      wrappedReadCallback.onReadFailed()
      return
    }

    this.readCallback = wrappedReadCallback
    if (!server.readCharacteristic(txCharacteristic)) {
      Log.w(TAG, "Read failed.")
      this.readCallback = null
      wrappedReadCallback.onReadFailed()
      return
    }
  }

  /** Called when close()ing or when BT disabled. */
  private fun stop() {
    stopScanning()
    server?.disconnect()
    server?.close()
    writeCallback?.let {
      it.onWriteFailed()
      writeCallback = null
    }
    readCallback?.let {
      it.onReadFailed()
      writeCallback = null
    }
    server = null
    characteristics = null
  }

  private fun startScanning() {
    Log.i(TAG, "Starting scan... address=$serverMacAddress, service=$SERVICE_UUID")
    bluetoothLeScanner.startScan(listOf(scanFilter), scanSettings, scanCallback)
  }

  private fun stopScanning() {
    Log.i(TAG, "Stopping scan...")
    bluetoothLeScanner.stopScan(scanCallback)
  }
}
