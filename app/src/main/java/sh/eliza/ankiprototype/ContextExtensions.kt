package sh.eliza.ankiprototype

import android.content.Context
import android.content.pm.PackageManager

fun Context.isPermissionGranted(permission: String) =
  checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
