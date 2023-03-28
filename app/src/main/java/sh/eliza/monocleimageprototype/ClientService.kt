package sh.eliza.monocleimageprototype

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.annotation.IdRes
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import sh.eliza.monocleimage.MonocleImage
import sh.eliza.monocleimage.SerializedMonocleImage
import sh.eliza.monocleimage.SerializedMonocleImageSender
import sh.eliza.monocleimageprototype.Constants.MTU
import sh.eliza.monocleimageprototype.Constants.SERVER_MAC_ADDRESS_KEY

private const val TAG = "ClientService"
private const val CHANNEL_ID = "monocleimageprototype:ClientService"

class ClientService : Service() {
  private val handler = Handler(Looper.getMainLooper())

  private lateinit var powerManager: PowerManager
  private lateinit var volumeKeyHelper: VolumeKeyHelper

  private var wakeLock: PowerManager.WakeLock? = null
  private var clientHelper: ClientHelper? = null

  private val imageSenderLock = ReentrantLock()
  private var imageSender: SerializedMonocleImageSender? = null

  override fun onCreate() {
    super.onCreate()

    val volumeUpImage = getMonocleImageResource(R.drawable.blackandwhite)
    val volumeDownImage = getMonocleImageResource(R.drawable.color)

    powerManager = getSystemService(POWER_SERVICE) as PowerManager
    volumeKeyHelper =
      VolumeKeyHelper(this) {
        val image =
          when (it) {
            VolumeKeyHelper.Event.UP -> volumeUpImage
            VolumeKeyHelper.Event.DOWN -> volumeDownImage
          }
        startSendingImage(image)
      }

    val notificationManager =
      getSystemService(NotificationManager::class.java) as NotificationManager
    notificationManager.createNotificationChannel(
      NotificationChannel(
        CHANNEL_ID,
        getString(R.string.service_notification_channel),
        NotificationManager.IMPORTANCE_LOW
      )
    )
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    super.onStartCommand(intent, flags, startId)

    startForeground(
      R.string.app_name,
      Notification.Builder(this, CHANNEL_ID).run {
        setSmallIcon(R.drawable.ic_launcher_background)
        build()
      }
    )

    if (wakeLock === null) {
      wakeLock =
        powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "monocleimageprototype:MainActivity"
          )
          .apply { acquire() }
    }

    val serverMacAddress = intent?.extras?.getString(SERVER_MAC_ADDRESS_KEY) ?: ""

    if (clientHelper?.serverMacAddress != serverMacAddress) {
      clientHelper?.close()
      Log.i(TAG, "Starting service for server device name `$serverMacAddress'")
      clientHelper = ClientHelper(this, MTU, serverMacAddress)
    }

    return START_STICKY
  }

  override fun onBind(intent: Intent) = null

  override fun onDestroy() {
    volumeKeyHelper.close()
    clientHelper?.close()
    wakeLock?.release()
    super.onDestroy()
  }

  private fun startSendingImage(image: MonocleImage) {
    val payload =
      imageSenderLock.withLock {
        if (imageSender !== null) {
          return
        }
        val sender =
          SerializedMonocleImageSender(SerializedMonocleImage.createFromMonocleImage(image, MTU))
        imageSender = sender
        sender.next()
      }
    writePayload(payload)
  }

  private fun writePayload(payload: SerializedMonocleImageSender.Payload) {
    Log.i(TAG, "writePayload(payload.kind = ${payload.kind})")
    val clientHelper = clientHelper
    check(clientHelper !== null)
    clientHelper!!

    when (payload.kind) {
      SerializedMonocleImageSender.PayloadKind.NEW_IMAGE,
      SerializedMonocleImageSender.PayloadKind.DATA ->
        clientHelper.write(
          payload.data.toByteArray(),
          when (payload.kind) {
            SerializedMonocleImageSender.PayloadKind.NEW_IMAGE ->
              BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            SerializedMonocleImageSender.PayloadKind.DATA ->
              BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            else -> throw IllegalStateException()
          },
          object : ClientHelper.WriteCallback {
            override fun onWriteResult() {
              val nextPayload = imageSenderLock.withLock { imageSender!!.next() }
              writePayload(nextPayload)
            }

            override fun onWriteFailed() {
              Log.i(TAG, "onWriteFailed()")
              imageSender = null
            }
          }
        )
      SerializedMonocleImageSender.PayloadKind.CONFIRMATION -> {
        clientHelper.read(
          object : ClientHelper.ReadCallback {
            override fun onReadResult(data: ByteArray) {
              Log.i(TAG, "onReadResult()")
              imageSenderLock
                .withLock {
                  val imageSender = imageSender!!
                  imageSender.onConfirmationResponse(data)
                  if (imageSender.isDone) {
                    this@ClientService.imageSender = null
                    null
                  } else {
                    imageSender.next()
                  }
                }
                ?.let { writePayload(it) }
            }

            override fun onReadFailed() {
              Log.i(TAG, "onReadFailed()")
              imageSender = null
            }
          }
        )
      }
    }
  }

  private fun getMonocleImageResource(@IdRes id: Int): MonocleImage {
    val bitmap =
      BitmapFactory.decodeResource(
        resources,
        id,
        BitmapFactory.Options().apply { inScaled = false }
      )
    return MonocleImage.createFromRgb { row, col -> bitmap.getPixel(col, row) }
  }
}
