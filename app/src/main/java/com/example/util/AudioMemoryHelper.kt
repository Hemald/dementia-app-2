package com.example.util

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import java.io.IOException

object AudioMemoryHelper {
    private const val TAG = "AudioMemoryHelper"
    private var activeRecorder: MediaRecorder? = null
    private var activePlayer: MediaPlayer? = null
    private var currentRecordingFile: File? = null

    fun getVoiceNotesDirectory(context: Context): File {
        val dir = File(context.filesDir, "voice_notes")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Starts recording a voice note to internal storage.
     * Returns the target File if recording successfully started, or null on error.
     */
    fun startRecording(context: Context): File? {
        stopRecording() // Stop any previous session
        stopPlayback()

        return try {
            val dir = getVoiceNotesDirectory(context)
            val file = File(dir, "memory_voice_${System.currentTimeMillis()}.m4a")
            currentRecordingFile = file

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(96000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

            activeRecorder = recorder
            file
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio recording", e)
            currentRecordingFile?.delete()
            currentRecordingFile = null
            activeRecorder?.release()
            activeRecorder = null
            null
        }
    }

    /**
     * Stops active recording and returns the final saved file path, or null.
     */
    fun stopRecording(): String? {
        val recorder = activeRecorder ?: return currentRecordingFile?.absolutePath
        return try {
            recorder.stop()
            recorder.release()
            activeRecorder = null
            val path = currentRecordingFile?.absolutePath
            currentRecordingFile = null
            path
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio recorder", e)
            activeRecorder?.release()
            activeRecorder = null
            currentRecordingFile = null
            null
        }
    }

    fun isRecording(): Boolean {
        return activeRecorder != null
    }

    /**
     * Plays a recorded voice note with a completion callback.
     */
    fun playVoiceNote(
        filePath: String,
        onComplete: () -> Unit = {},
        onError: (String) -> Unit = {}
    ): Boolean {
        stopPlayback()
        val file = File(filePath)
        if (!file.exists()) {
            onError("Audio recording file not found")
            return false
        }

        return try {
            val player = MediaPlayer().apply {
                setDataSource(filePath)
                prepare()
                setOnCompletionListener {
                    stopPlayback()
                    onComplete()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    stopPlayback()
                    onError("Playback error ($what)")
                    true
                }
                start()
            }
            activePlayer = player
            true
        } catch (e: IOException) {
            Log.e(TAG, "Failed to play audio file", e)
            stopPlayback()
            onError("Could not play audio file")
            false
        }
    }

    fun stopPlayback() {
        activePlayer?.let { player ->
            try {
                if (player.isPlaying) {
                    player.stop()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping media player", e)
            } finally {
                player.release()
                activePlayer = null
            }
        }
    }

    fun isPlaying(): Boolean {
        return try {
            activePlayer?.isPlaying == true
        } catch (_: Exception) {
            false
        }
    }

    fun deleteVoiceNote(filePath: String?) {
        if (filePath.isNullOrBlank()) return
        try {
            val file = File(filePath)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete voice note: $filePath", e)
        }
    }
}
