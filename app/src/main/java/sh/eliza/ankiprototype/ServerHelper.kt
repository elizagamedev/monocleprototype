package sh.eliza.ankiprototype

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.ParcelUuid
import android.util.Log
import sh.eliza.ankiprototype.Constants.SERVICE_RX_CHARACTERISTIC_UUID
import sh.eliza.ankiprototype.Constants.SERVICE_UUID

private const val TAG = "ServerHelper"

class ServerHelper(private val context: Context) : AutoCloseable {
  private val bluetoothManager =
    context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

  private var bluetoothGattServer: BluetoothGattServer? = null
  private var connectedDevice: BluetoothDevice? = null

  private val service =
    BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY).apply {
      addCharacteristic(
        BluetoothGattCharacteristic(
          SERVICE_RX_CHARACTERISTIC_UUID,
          BluetoothGattCharacteristic.PROPERTY_WRITE,
          BluetoothGattCharacteristic.PERMISSION_WRITE
        )
      )
    }

  private val bluetoothReceiver =
    object : BroadcastReceiver() {
      override fun onReceive(context: Context, intent: Intent) {
        val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF)

        when (state) {
          BluetoothAdapter.STATE_ON -> {
            startAdvertising()
            startServer()
          }
          BluetoothAdapter.STATE_OFF -> {
            stopServer()
            stopAdvertising()
          }
        }
      }
    }

  /** Callback to receive information about the advertisement process. */
  private val advertiseCallback =
    object : AdvertiseCallback() {
      override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
        Log.i(TAG, "LE Advertise Started.")
      }

      override fun onStartFailure(errorCode: Int) {
        Log.w(TAG, "LE Advertise Failed: $errorCode")
      }
    }

  /**
   * Callback to handle incoming requests to the GATT server. All read/write requests for
   * characteristics and descriptors are handled here.
   */
  private val gattServerCallback =
    object : BluetoothGattServerCallback() {
      override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
        if (newState == BluetoothProfile.STATE_CONNECTED) {
          Log.i(TAG, "BluetoothDevice CONNECTED: $device")
          connectedDevice?.let { bluetoothGattServer?.cancelConnection(device) }
          connectedDevice = device
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
          Log.i(TAG, "BluetoothDevice DISCONNECTED: $device")
          if (device == connectedDevice) {
            connectedDevice = null
          }
        }
      }

      override fun onCharacteristicWriteRequest(
        device: BluetoothDevice,
        requestId: Int,
        characteristic: BluetoothGattCharacteristic,
        preparedWrite: Boolean,
        responseNeeded: Boolean,
        offset: Int,
        value: ByteArray
      ) {
        when (characteristic.uuid) {
          SERVICE_RX_CHARACTERISTIC_UUID -> {
            if (responseNeeded) {
              bluetoothGattServer?.sendResponse(
                device,
                requestId,
                BluetoothGatt.GATT_SUCCESS,
                0,
                null
              )
            }
          }
          else -> {
            Log.w(TAG, "Unknown descriptor write request")
            if (responseNeeded) {
              bluetoothGattServer?.sendResponse(
                device,
                requestId,
                BluetoothGatt.GATT_FAILURE,
                0,
                null
              )
            }
          }
        }
      }
    }

  init {
    val bluetoothAdapter = bluetoothManager.adapter
    // We can't continue without proper Bluetooth support
    checkBluetoothSupport(context, bluetoothAdapter)

    // Register for system Bluetooth events
    context.registerReceiver(bluetoothReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
    if (bluetoothAdapter.isEnabled) {
      Log.d(TAG, "Bluetooth enabled...starting services")
      startAdvertising()
      startServer()
    }
  }

  override fun close() {
    if (bluetoothManager.adapter.isEnabled) {
      stopServer()
      stopAdvertising()
    }

    context.unregisterReceiver(bluetoothReceiver)
  }

  /**
   * Begin advertising over Bluetooth that this device is connectable and supports the Current Time
   * Service.
   */
  private fun startAdvertising() {
    bluetoothManager.adapter.bluetoothLeAdvertiser?.let {
      it.startAdvertising(
        AdvertiseSettings.Builder().run {
          setConnectable(true)
          setTimeout(0)
          build()
        },
        AdvertiseData.Builder().run {
          setIncludeDeviceName(true)
          addServiceUuid(ParcelUuid(SERVICE_UUID))
          build()
        },
        advertiseCallback
      )
    }
      ?: Log.w(TAG, "Failed to create advertiser")
  }

  /** Stop Bluetooth advertisements. */
  private fun stopAdvertising() {
    bluetoothManager.adapter.bluetoothLeAdvertiser?.let { it.stopAdvertising(advertiseCallback) }
      ?: Log.w(TAG, "Failed to create advertiser")
  }

  /** Initialize the GATT server instance with the services/characteristics. */
  private fun startServer() {
    bluetoothGattServer = bluetoothManager.openGattServer(context, gattServerCallback)

    bluetoothGattServer?.addService(service) ?: Log.w(TAG, "Unable to create GATT server")
  }

  /** Shut down the GATT server. */
  private fun stopServer() {
    bluetoothGattServer?.close()
    bluetoothGattServer = null
  }
}

/**
 * Verify the level of Bluetooth support provided by the hardware.
 * @param bluetoothAdapter System [BluetoothAdapter].
 * @return true if Bluetooth is properly supported, false otherwise.
 */
private fun checkBluetoothSupport(context: Context, bluetoothAdapter: BluetoothAdapter?) {
  if (bluetoothAdapter == null) {
    throw RuntimeException("Bluetooth is not supported")
  }

  if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
    throw RuntimeException("Bluetooth LE is not supported")
  }
}
