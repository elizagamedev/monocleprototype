package sh.eliza.ankiprototype

import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

private const val TAG = "ServerActivity"

class ServerActivity : AppCompatActivity() {
  private lateinit var powerManager: PowerManager

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.server_activity)

    powerManager = getSystemService(POWER_SERVICE) as PowerManager
  }

  override fun onResume() {
    super.onResume()
    Log.i(TAG, "onResume")
    stopService(Intent(this, VolumeKeyService::class.java))
  }

  override fun onPause() {
    if (!powerManager.isInteractive()) {
      Log.i(TAG, "onPause, screen off: starting service")
      startForegroundService(Intent(this, VolumeKeyService::class.java))
    }
    super.onPause()
  }
}
