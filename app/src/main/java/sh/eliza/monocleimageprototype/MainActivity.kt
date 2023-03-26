package sh.eliza.monocleimageprototype

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

private const val TAG = "MainActivity"

class MainActivity : AppCompatActivity() {
  private lateinit var bluetoothManager: BluetoothManager

  private lateinit var readinessStatusText: TextView
  private lateinit var enablePermissionsButton: Button

  private val enableBluetoothActivityResultLauncher =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
      updateReadinessText()
    }

  private val enableConnectPermission =
    registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
      if (isGranted && !bluetoothManager.adapter.isEnabled) {
        enableBluetoothActivityResultLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
      } else {
        updateReadinessText()
      }
    }

  private val enableAdvertisePermission =
    registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
      if (isGranted) {
        enableConnectPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
      } else {
        updateReadinessText()
      }
    }

  private val enableScanPermission =
    registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
      if (isGranted) {
        enableAdvertisePermission.launch(Manifest.permission.BLUETOOTH_ADVERTISE)
      } else {
        updateReadinessText()
      }
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.main_activity)

    bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager

    findViewById<Button>(R.id.server).setOnClickListener {
      startActivity(Intent(this, ServerActivity::class.java))
    }
    findViewById<Button>(R.id.client).setOnClickListener {
      startActivity(Intent(this, ClientActivity::class.java))
    }

    readinessStatusText = findViewById<TextView>(R.id.readiness_status)

    enablePermissionsButton =
      findViewById<Button>(R.id.enable_permissions).apply {
        setOnClickListener { enableScanPermission.launch(Manifest.permission.BLUETOOTH_SCAN) }
      }

    updateReadinessText()
  }

  override fun onResume() {
    super.onResume()
    updateReadinessText()
  }

  private fun updateReadinessText() {
    val bluetoothEnabled = bluetoothManager.adapter.isEnabled
    val connectPermissionGranted = isPermissionGranted(Manifest.permission.BLUETOOTH_CONNECT)
    val advertisePermissionGranted = isPermissionGranted(Manifest.permission.BLUETOOTH_ADVERTISE)
    val message =
      when {
        !connectPermissionGranted -> R.string.connect_permission_not_enabled
        !advertisePermissionGranted -> R.string.advertise_permission_not_enabled
        !bluetoothEnabled -> R.string.bluetooth_not_enabled
        else -> R.string.all_permissions_granted
      }
    readinessStatusText.text = getString(message)
  }
}
