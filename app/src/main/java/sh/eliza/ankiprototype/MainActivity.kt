package sh.eliza.ankiprototype

import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

private const val TAG = "MainActivity"

class MainActivity : AppCompatActivity() {
  private lateinit var powerManager: PowerManager
  private lateinit var textView: TextView

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.main_activity)
    textView = findViewById<TextView>(R.id.text)

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

  override fun onDestroy() {
    super.onDestroy()
  }
}
