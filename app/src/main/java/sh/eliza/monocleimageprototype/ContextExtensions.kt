package sh.eliza.monocleimageprototype

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager

fun Context.isPermissionGranted(permission: String) =
  checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

fun Context.hasBluetoothSupport() =
  packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) &&
    isPermissionGranted(Manifest.permission.BLUETOOTH_ADVERTISE) &&
    isPermissionGranted(Manifest.permission.BLUETOOTH_CONNECT) &&
    isPermissionGranted(Manifest.permission.BLUETOOTH_SCAN) &&
    isPermissionGranted(Manifest.permission.BLUETOOTH)
