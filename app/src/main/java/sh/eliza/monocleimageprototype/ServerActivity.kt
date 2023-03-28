package sh.eliza.monocleimageprototype

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import sh.eliza.monocleimage.MonocleImage
import sh.eliza.monocleimage.SerializedMonocleImageReceiver

private const val TAG = "ServerActivity"

class ServerActivity : AppCompatActivity() {
  private val handler = Handler(Looper.getMainLooper())

  private var serverHelper: ServerHelper? = null

  private val imageReceiverLock = ReentrantLock()
  private val imageReceiver = SerializedMonocleImageReceiver()
  private var imageReceiverStatus = SerializedMonocleImageReceiver.Status.NEEDS_MORE_DATA

  private lateinit var imageView: ImageView

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.server_activity)

    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

    val instructionsText = findViewById<TextView>(R.id.instructions)
    imageView = findViewById<ImageView>(R.id.image)

    if (hasBluetoothSupport()) {
      serverHelper =
        ServerHelper(
          this,
          { data, isResponseNeeded ->
            imageReceiverLock.withLock {
              imageReceiver.push(data, isResponseNeeded)
              if (imageReceiver.status != imageReceiverStatus) {
                Log.i(TAG, "Status changed. ${imageReceiver.status}")
                imageReceiverStatus = imageReceiver.status
                if (imageReceiver.status != SerializedMonocleImageReceiver.Status.NEEDS_MORE_DATA) {
                  val image = imageReceiver.toMonocleImage()
                  handler.post { onImageRecieved(image) }
                }
              }
            }
          },
          { imageReceiverLock.withLock { imageReceiver.confirmationResponse } }
        )

      instructionsText.text = getString(R.string.server_instructions_running)
    } else {
      instructionsText.text = getString(R.string.server_instructions_not_running)
    }
  }

  override fun onDestroy() {
    serverHelper?.close()
    super.onDestroy()
  }

  private fun onImageRecieved(monocleImage: MonocleImage) {
    Log.i(TAG, "onImageReceived()")
    val bitmap =
      Bitmap.createBitmap(
        MonocleImage.WIDTH,
        MonocleImage.HEIGHT,
        Bitmap.Config.ARGB_8888,
        /*hasAlpha=*/ false,
      )
    monocleImage.toRgb { row, col, color -> bitmap.setPixel(col, row, color or 0xFF000000.toInt()) }
    imageView.setImageBitmap(bitmap)
    imageView.setAdjustViewBounds(true)
  }
}
