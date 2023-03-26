package sh.eliza.ankiprototype

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import sh.eliza.ankiprototype.Constants.SERVER_MAC_ADDRESS_KEY

private const val TAG = "ClientService"
private const val CHANNEL_ID = "ankiprototype:ClientService"

class ClientService : Service() {
  private val handler = Handler(Looper.getMainLooper())

  private lateinit var powerManager: PowerManager
  private lateinit var volumeKeyHelper: VolumeKeyHelper

  private var wakeLock: PowerManager.WakeLock? = null
  private var clientHelper: ClientHelper? = null

  override fun onCreate() {
    super.onCreate()

    powerManager = getSystemService(POWER_SERVICE) as PowerManager
    volumeKeyHelper =
      VolumeKeyHelper(this) {
        clientHelper?.write(
          "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum. Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do ei".toByteArray(),
          handler,
          object : ClientHelper.WriteCallback {
            override fun onWriteResult() {}
            override fun onWriteFailed() {}
          }
        )
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
        powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ankiprototype:MainActivity")
          .apply { acquire() }
    }

    val serverMacAddress = intent?.extras?.getString(SERVER_MAC_ADDRESS_KEY) ?: ""

    if (clientHelper?.serverMacAddress != serverMacAddress) {
      clientHelper?.close()
      Log.i(TAG, "Starting service for server device name `$serverMacAddress'")
      clientHelper = ClientHelper(this, serverMacAddress)
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
}
