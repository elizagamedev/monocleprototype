package sh.eliza.ankiprototype

import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

private const val TAG = "ClientActivity"

class ClientActivity : AppCompatActivity() {
  private lateinit var powerManager: PowerManager

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.client_activity)

    powerManager = getSystemService(POWER_SERVICE) as PowerManager

    val preferences = getPreferences(MODE_PRIVATE)

    findViewById<EditText>(R.id.server_mac_address)
      .addTextChangedListener(
        object : TextWatcher {
          override fun afterTextChanged(s: Editable) {}
          override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
          override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            preferences.serverMacAddress = s.toString()
          }
        }
      )
  }

  override fun onResume() {
    super.onResume()
    Log.i(TAG, "onResume")
    stopService(Intent(this, ClientService::class.java))
  }

  override fun onPause() {
    if (!powerManager.isInteractive()) {
      Log.i(TAG, "onPause, screen off: starting service")
      startForegroundService(Intent(this, ClientService::class.java))
    }
    super.onPause()
  }
}
