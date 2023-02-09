package sh.eliza.ankiprototype

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

private const val TAG = "MainActivity"

class MainActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.main_activity)

    findViewById<Button>(R.id.server).setOnClickListener {
      startActivity(Intent(this, ServerActivity::class.java))
    }
    findViewById<Button>(R.id.client).setOnClickListener {
      startActivity(Intent(this, ClientActivity::class.java))
    }
  }
}
