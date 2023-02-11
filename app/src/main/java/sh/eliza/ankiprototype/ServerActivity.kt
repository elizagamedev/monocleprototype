package sh.eliza.ankiprototype

import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity

private const val TAG = "ServerActivity"

class ServerActivity : AppCompatActivity() {
  private lateinit var serverHelper: ServerHelper

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.server_activity)

    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

    serverHelper = ServerHelper(this)
  }

  override fun onDestroy() {
    serverHelper.close()
    super.onDestroy()
  }
}
