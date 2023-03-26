package sh.eliza.ankiprototype

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.PowerManager
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import sh.eliza.ankiprototype.Constants.SERVER_MAC_ADDRESS_KEY

private const val TAG = "ClientActivity"

class ClientActivity : AppCompatActivity() {
  private lateinit var powerManager: PowerManager
  private lateinit var preferences: SharedPreferences

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.client_activity)

    powerManager = getSystemService(POWER_SERVICE) as PowerManager
    preferences = getPreferences(MODE_PRIVATE)

    findViewById<EditText>(R.id.server_mac_address).run {
      setText(preferences.serverMacAddress, TextView.BufferType.EDITABLE)
      addTextChangedListener(
        object : TextWatcher {
          override fun afterTextChanged(s: Editable) {}
          override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
          override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            preferences.serverMacAddress = s.toString()
          }
        }
      )
    }
  }

  override fun onResume() {
    super.onResume()
    stopClientService()
  }

  override fun onPause() {
    if (!powerManager.isInteractive()) {
      startClientService()
    }
    super.onPause()
  }

  private fun startClientService() {
    if (hasBluetoothSupport()) {
      Log.i(TAG, "Starting client service...")
      startForegroundService(
        Intent(this, ClientService::class.java).apply {
          putExtra(SERVER_MAC_ADDRESS_KEY, preferences.serverMacAddress)
        }
      )
    } else {
      Log.i(TAG, "Cannot start service due to lack of BLE support.")
    }
  }

  private fun stopClientService() {
    Log.i(TAG, "Stopping client service...")
    stopService(Intent(this, ClientService::class.java))
  }
}
