package net.olamaelcu.livtet.jigsaw.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PuzzleSoundPlayer(context: Context) {

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val soundIds: MutableMap<PuzzleSound, Int> = mutableMapOf()
    private var loaded: Boolean = false

    suspend fun load() = withContext(Dispatchers.IO) {
        PuzzleSound.entries.forEach { sound ->
            val id = soundPool.load(context, sound.resId, 1)
            soundIds[sound] = id
        }
        loaded = true
    }

    fun play(sound: PuzzleSound, volume: Float = 1f) {
        if (!loaded) return
        val id = soundIds[sound] ?: return
        soundPool.play(id, volume, volume, 1, 0, 1f)
    }

    fun release() { soundPool.release() }
}
