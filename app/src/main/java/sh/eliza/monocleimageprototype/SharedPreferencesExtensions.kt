package sh.eliza.monocleimageprototype

import android.content.SharedPreferences
import sh.eliza.monocleimageprototype.Constants.SERVER_MAC_ADDRESS_KEY

var SharedPreferences.serverMacAddress
  get() = getString(SERVER_MAC_ADDRESS_KEY, null) ?: ""
  set(value) {
    edit().run {
      putString(SERVER_MAC_ADDRESS_KEY, value)
      apply()
    }
  }
