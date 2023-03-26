package sh.eliza.ankiprototype

import android.content.SharedPreferences
import sh.eliza.ankiprototype.Constants.SERVER_MAC_ADDRESS_KEY

var SharedPreferences.serverMacAddress
  get() = getString(SERVER_MAC_ADDRESS_KEY, null) ?: ""
  set(value) {
    edit().run {
      putString(SERVER_MAC_ADDRESS_KEY, value)
      apply()
    }
  }
