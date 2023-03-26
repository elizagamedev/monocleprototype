package sh.eliza.monocleimageprototype

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private const val TAG = "VolumeKeyHelper"

private const val STREAM = AudioManager.STREAM_MUSIC
private const val ACTION_VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"

class VolumeKeyHelper(private val context: Context, private val listener: (Event) -> Unit) :
  AutoCloseable {

  enum class Event {
    DOWN,
    UP
  }

  private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
  private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
  private val mediaSession =
    MediaSession(context, TAG).apply {
      setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 0, 0f).build())
      setActive(true)
    }

  private val minVolume = audioManager.getStreamMinVolume(STREAM)
  private val maxVolume = audioManager.getStreamMaxVolume(STREAM)

  private var clampedVolume: Int? = null

  private var volume
    get() = audioManager.getStreamVolume(STREAM)
    set(value) {
      audioManager.setStreamVolume(STREAM, value, /*flags=*/ 0)
    }

  init {
    recalculateClampedVolume()
  }

  private val broadcastReceiver =
    object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
          onReceiveBroadcast(intent)
        }
      }
      .apply {
        context.registerReceiver(
          this,
          IntentFilter().apply {
            addAction(ACTION_VOLUME_CHANGED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
          }
        )
      }

  override fun close() {
    mediaSession.release()
    context.unregisterReceiver(broadcastReceiver)
  }

  private fun onReceiveBroadcast(intent: Intent) {
    when (intent.action) {
      Intent.ACTION_SCREEN_ON -> {
        clampedVolume = null
      }
      Intent.ACTION_SCREEN_OFF -> {
        recalculateClampedVolume()
      }
      ACTION_VOLUME_CHANGED -> {
        if (intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1) != STREAM) {
          return
        }
        val clampedVolume = clampedVolume ?: return
        val oldVolume = intent.getIntExtra("android.media.EXTRA_PREV_VOLUME_STREAM_VALUE", 0)
        if (oldVolume != clampedVolume) {
          return
        }
        val newVolume = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_VALUE", 0)
        if (newVolume < oldVolume) {
          listener(Event.DOWN)
        } else {
          listener(Event.UP)
        }
        volume = clampedVolume
      }
    }
  }

  private fun recalculateClampedVolume() {
    val volume = volume
    clampedVolume =
      when {
        volume <= minVolume -> {
          Log.i(TAG, "Muted, disabling volume buttons")
          null
        }
        volume >= maxVolume -> {
          Log.i(TAG, "Max volume, reducing by one")
          this.volume = maxVolume - 1
          maxVolume - 1
        }
        else -> volume
      }
  }

  companion object {
    fun createFlow(context: Context): Flow<Event> = callbackFlow {
      val helper = VolumeKeyHelper(context, this::trySendBlocking)
      awaitClose { helper.close() }
    }
  }
}
