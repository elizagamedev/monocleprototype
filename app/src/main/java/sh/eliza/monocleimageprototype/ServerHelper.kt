package sh.eliza.monocleimageprototype

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
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
import android.os.Handler
import android.os.ParcelUuid
import android.util.Log
import java.io.ByteArrayOutputStream
import sh.eliza.monocleimageprototype.Constants.SERVICE_RX_CHARACTERISTIC_UUID
import sh.eliza.monocleimageprototype.Constants.SERVICE_UUID

private const val TAG = "ServerHelper"

class ServerHelper(
  private val context: Context,
  private val callbackHandler: Handler,
  private val onReceive: (ByteArray) -> Unit
) : AutoCloseable {
  private val bluetoothManager =
    context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
  private val bluetoothAdapter = bluetoothManager.adapter
  private val bluetoothLeAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser

  private val rxBuffer = ByteArrayOutputStream()

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
        Log.i(TAG, "Advertising started.")
      }

      override fun onStartFailure(errorCode: Int) {
        Log.w(TAG, "Advertising failed. errorCode=$errorCode")
      }
    }

  /**
   * Callback to handle incoming requests to the GATT server. All read/write requests for
   * characteristics and descriptors are handled here.
   */
  private val gattServerCallback =
    object : BluetoothGattServerCallback() {
      override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
        try {
          when (newState) {
            BluetoothProfile.STATE_CONNECTED -> {
              Log.i(TAG, "Connected. device=$device")
              connectedDevice?.let { bluetoothGattServer?.cancelConnection(device) }
              connectedDevice = device
              rxBuffer.reset()
              stopAdvertising()
            }
            BluetoothProfile.STATE_DISCONNECTED -> {
              Log.i(TAG, "Disconnected. device=$device")
              if (device == connectedDevice) {
                connectedDevice = null
                rxBuffer.reset()
                startAdvertising()
              }
            }
          }
        } catch (t: Throwable) {
          Log.e(TAG, "Unhandled exception during `onConnectionStateChange'", t)
          stopAdvertising()
          stopServer()
        }
      }

      override fun onCharacteristicReadRequest(
        device: BluetoothDevice,
        requestId: Int,
        offset: Int,
        characteristic: BluetoothGattCharacteristic
      ) {
        try {
          Log.w(TAG, "Unknown characteristic read request.")
          bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
        } catch (t: Throwable) {
          Log.e(TAG, "Unhandled exception during `onCharacteristicReadRequest'", t)
          stopAdvertising()
          stopServer()
        }
      }

      override fun onDescriptorReadRequest(
        device: BluetoothDevice,
        requestId: Int,
        offset: Int,
        characteristic: BluetoothGattDescriptor
      ) {
        try {
          Log.w(TAG, "Unknown descriptor read request.")
          bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
        } catch (t: Throwable) {
          Log.e(TAG, "Unhandled exception during `onDescriptorReadRequest'", t)
          stopAdvertising()
          stopServer()
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
        try {
          when (characteristic.uuid) {
            SERVICE_RX_CHARACTERISTIC_UUID -> {
              if (preparedWrite) {
                Log.i(TAG, "Prepared protocol write request.")
                if (offset != rxBuffer.size()) {
                  Log.w(TAG, "offset ($offset) != size (${rxBuffer.size()})")
                }
                rxBuffer.write(value)
              } else {
                Log.i(TAG, "Protocol write request.")
                callbackHandler.post { onReceive(value) }
              }
              if (responseNeeded) {
                bluetoothGattServer?.sendResponse(
                  device,
                  requestId,
                  BluetoothGatt.GATT_SUCCESS,
                  0,
                  value
                )
              }
            }
            else -> {
              Log.w(TAG, "Unknown characteristic write request.")
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
        } catch (t: Throwable) {
          Log.e(TAG, "Unhandled exception during `onCharacteristicWriteRequest'", t)
          stopAdvertising()
          stopServer()
        }
      }

      override fun onDescriptorWriteRequest(
        device: BluetoothDevice,
        requestId: Int,
        descriptor: BluetoothGattDescriptor,
        preparedWrite: Boolean,
        responseNeeded: Boolean,
        offset: Int,
        value: ByteArray
      ) {
        try {
          Log.w(TAG, "Unknown descriptor write request.")
          if (responseNeeded) {
            bluetoothGattServer?.sendResponse(
              device,
              requestId,
              BluetoothGatt.GATT_FAILURE,
              0,
              null
            )
          }
        } catch (t: Throwable) {
          Log.e(TAG, "Unhandled exception during `onDescriptorWriteRequest'", t)
          stopAdvertising()
          stopServer()
        }
      }

      override fun onExecuteWrite(device: BluetoothDevice, requestId: Int, execute: Boolean) {
        try {
          if (execute) {
            Log.i(TAG, "Executing write.")
            callbackHandler.post { onReceive(rxBuffer.toByteArray()) }
          } else {
            Log.i(TAG, "Canceling write.")
          }
          rxBuffer.reset()
          bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
        } catch (t: Throwable) {
          Log.e(TAG, "Unhandled exception during `onExecuteWrite'", t)
          stopAdvertising()
          stopServer()
        }
      }
    }

  init {
    check(context.hasBluetoothSupport())
    context.registerReceiver(bluetoothReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))

    if (bluetoothAdapter.isEnabled) {
      startAdvertising()
      startServer()
    }
  }

  override fun close() {
    if (bluetoothAdapter.isEnabled) {
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
    Log.i(TAG, "Starting advertising...")
    bluetoothLeAdvertiser.startAdvertising(
      AdvertiseSettings.Builder().run {
        setConnectable(true)
        setTimeout(0)
        build()
      },
      AdvertiseData.Builder().run {
        addServiceUuid(ParcelUuid(SERVICE_UUID))
        setIncludeTxPowerLevel(true)
        build()
      },
      AdvertiseData.Builder().run {
        setIncludeDeviceName(true)
        setIncludeTxPowerLevel(true)
        build()
      },
      advertiseCallback
    )
  }

  /** Stop Bluetooth advertisements. */
  private fun stopAdvertising() {
    Log.i(TAG, "Stopping advertising...")
    bluetoothLeAdvertiser.stopAdvertising(advertiseCallback)
  }

  /** Initialize the GATT server instance with the services/characteristics. */
  private fun startServer() {
    Log.i(TAG, "Starting server...")
    bluetoothGattServer = bluetoothManager.openGattServer(context, gattServerCallback)

    bluetoothGattServer?.addService(service) ?: Log.w(TAG, "Unable to create GATT server.")
  }

  /** Shut down the GATT server. */
  private fun stopServer() {
    Log.i(TAG, "Stopping server...")
    bluetoothGattServer?.close()
    bluetoothGattServer = null
    rxBuffer.reset()
  }
}
