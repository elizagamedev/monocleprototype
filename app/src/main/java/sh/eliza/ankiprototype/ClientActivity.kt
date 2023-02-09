package sh.eliza.ankiprototype

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

private const val TAG = "ClientActivity"

class ClientActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.client_activity)
  }
}
