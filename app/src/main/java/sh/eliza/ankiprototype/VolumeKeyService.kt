package sh.eliza.ankiprototype

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.PowerManager
import android.util.Log

private const val TAG = "VolumeKeyService"
private const val CHANNEL_ID = "VolumeKeyService"

class VolumeKeyService : Service() {
  private lateinit var powerManager: PowerManager
  private lateinit var volumeKeyHelper: VolumeKeyHelper

  private var wakeLock: PowerManager.WakeLock? = null

  override fun onCreate() {
    super.onCreate()

    powerManager = getSystemService(POWER_SERVICE) as PowerManager
    volumeKeyHelper = VolumeKeyHelper(this) { Log.i(TAG, "event: $it") }

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

    return START_STICKY
  }

  override fun onBind(intent: Intent) = null

  override fun onDestroy() {
    volumeKeyHelper.close()
    wakeLock?.release()
    wakeLock = null
    super.onDestroy()
  }
}
