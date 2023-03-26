package sh.eliza.monocleimageprototype

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

private const val TAG = "ServerActivity"

class ServerActivity : AppCompatActivity() {
  private val handler = Handler(Looper.getMainLooper())

  private var serverHelper: ServerHelper? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.server_activity)

    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

    val instructionsText = findViewById<TextView>(R.id.instructions)
    val messageText = findViewById<TextView>(R.id.message)

    Log.i(TAG, "thread: ${Thread.currentThread()}")

    if (hasBluetoothSupport()) {
      serverHelper =
        ServerHelper(
          this,
          handler,
        ) { messageText.text = String(it) }
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
