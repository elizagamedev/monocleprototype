package sh.eliza.ankiprototype

import android.Manifest
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

private const val TAG = "ServerActivity"

class ServerActivity : AppCompatActivity() {
  private var serverHelper: ServerHelper? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.server_activity)

    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

    val instructionsText = findViewById<TextView>(R.id.instructions)

    if (isPermissionGranted(Manifest.permission.BLUETOOTH_ADVERTISE)) {
      serverHelper = ServerHelper(this)
      instructionsText.text = getString(R.string.server_instructions_running)
    } else {
      instructionsText.text = getString(R.string.server_instructions_not_running)
    }
  }

  override fun onDestroy() {
    serverHelper?.close()
    super.onDestroy()
  }
}
