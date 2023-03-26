package sh.eliza.ankiprototype

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
import android.os.Handler
import android.os.ParcelUuid
import android.util.Log
import sh.eliza.ankiprototype.Constants.SERVICE_RX_CHARACTERISTIC_UUID
import sh.eliza.ankiprototype.Constants.SERVICE_UUID

private const val TAG = "ClientHelper"

private class WrappedWriteCallback(
  private val handler: Handler,
  private val callback: ClientHelper.WriteCallback
) {
  fun onWriteResult() {
    handler.post { callback.onWriteResult() }
  }

  fun onWriteFailed() {
    handler.post { callback.onWriteFailed() }
  }
}

class ClientHelper(private val context: Context, val serverMacAddress: String) : AutoCloseable {
  interface WriteCallback {
    fun onWriteResult()
    fun onWriteFailed()
  }

  private val bluetoothManager =
    context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
  private val bluetoothAdapter = bluetoothManager.adapter
  private val bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner

  private var isStarted = false
  private var server: BluetoothGatt? = null
  private var rxCharacteristic: BluetoothGattCharacteristic? = null

  private var writeCallback: WrappedWriteCallback? = null

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
              gatt.requestMtu(517)
            }
            BluetoothProfile.STATE_DISCONNECTED -> {
              Log.i(TAG, "Disconnected. address=${gatt.device.address}")
              gatt.close()
              if (gatt == server) {
                server = null
                if (isStarted) {
                  startScanning()
                }
              }
            }
          }
        } catch (t: Throwable) {
          Log.w(TAG, "Unhandled exception during `onConnectionStateChange'")
          stop()
        }
      }

      override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        try {
          if (gatt != server) {
            Log.w(TAG, "onServicesDiscovered() called for device other than server.")
            return
          }
          if (status == BluetoothGatt.GATT_SUCCESS) {
            Log.i(TAG, "Services discovered.")
            gatt.getService(SERVICE_UUID)?.getCharacteristic(SERVICE_RX_CHARACTERISTIC_UUID)?.let {
              rxCharacteristic = it
              Log.i(TAG, "Ready to write!")
            }
              ?: Log.w(TAG, "RX service characteristic not found.")
          } else {
            Log.w(TAG, "Failed to discover services. status=$status")
          }
        } catch (t: Throwable) {
          Log.w(TAG, "Unhandled exception during `onServicesDiscovered'")
          stop()
        }
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
          if (characteristic != rxCharacteristic) {
            Log.w(TAG, "onCharacteristicWrite() called for unknown characteristic.")
            return
          }
          writeCallback?.let {
            if (status == BluetoothGatt.GATT_SUCCESS) {
              Log.i(TAG, "Write completed.")
              it.onWriteResult()
            } else {
              Log.w(TAG, "Write failed. status=$status")
              it.onWriteFailed()
            }
            writeCallback = null
          }
            ?: Log.w(TAG, "Write callback not set.")
        } catch (t: Throwable) {
          Log.w(TAG, "Unhandled exception during `onCharacteristicWrite'")
          stop()
        }
      }

      override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
        Log.i(TAG, "ATT MTU changed. mtu=$mtu, status=$status")
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
          rxCharacteristic = null
          server =
            result.device.connectGatt(
              context,
              /*autoConnect=*/ false,
              gattCallback,
              BluetoothDevice.TRANSPORT_LE
            )
        } catch (t: Throwable) {
          Log.w(TAG, "Unhandled exception during `onCharacteristicWrite'")
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

    if (bluetoothAdapter.isEnabled) {
      stop()
    }
    context.unregisterReceiver(bluetoothReceiver)
  }

  fun write(value: ByteArray, handler: Handler, writeCallback: WriteCallback) {
    server?.let { server ->
      rxCharacteristic?.let { rxCharacteristic ->
        val wrappedWriteCallback = WrappedWriteCallback(handler, writeCallback)
        if (this.writeCallback !== null) {
          Log.w(TAG, "Previous write still in progress.")
          wrappedWriteCallback.onWriteFailed()
          return
        }
        if (value.size > 512) {
          // 512 is the maximum size, even with prepared writes. Pixel 5a's Bluetooth just
          // straight-up crashes in this case.
          Log.e(TAG, "Payload size exceeds limit.")
          wrappedWriteCallback.onWriteFailed()
          return
        }
        Log.i(TAG, "Writing to characteristic...")
        val result =
          server.writeCharacteristic(
            rxCharacteristic,
            value,
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
          )
        if (result == BluetoothGatt.GATT_SUCCESS) {
          this.writeCallback = wrappedWriteCallback
        } else {
          Log.i(TAG, "Write failed. code=$result")
          wrappedWriteCallback.onWriteFailed()
        }
      }
        ?: Log.w(TAG, "Tried to write without server characteristic.")
    }
      ?: Log.w(TAG, "Tried to write without connection to server.")
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
    server = null
    rxCharacteristic = null
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
