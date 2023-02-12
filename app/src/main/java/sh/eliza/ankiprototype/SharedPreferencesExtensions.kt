package sh.eliza.ankiprototype

import android.content.SharedPreferences

private const val SERVER_MAC_ADDRESS_KEY = "serverMacAddress"

var SharedPreferences.serverMacAddress
  get() = getString(SERVER_MAC_ADDRESS_KEY, "")
  set(value) {
    edit().run {
      putString(SERVER_MAC_ADDRESS_KEY, value)
      apply()
    }
  }
