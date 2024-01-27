package com.mrousavy.camera.core

import android.content.Context
import android.util.Log
import android.util.Size
import android.view.Surface
import com.facebook.common.statfs.StatFsHelper
import com.mrousavy.camera.extensions.getRecommendedBitRate
import com.mrousavy.camera.types.Orientation
import com.mrousavy.camera.types.RecordVideoOptions
import com.mrousavy.camera.utils.FileUtils
import java.io.File

class RecordingSession(
  context: Context,
  val cameraId: String,
  val size: Size,
  private val enableAudio: Boolean,
  private val fps: Int? = null,
  private val hdr: Boolean = false,
  private val orientation: Orientation,
  private val options: RecordVideoOptions,
  private val callback: (video: Video) -> Unit,
  private val onError: (error: CameraError) -> Unit
) {
  companion object {
    private const val TAG = "RecordingSession"

    private const val AUDIO_SAMPLING_RATE = 44_100
    private const val AUDIO_BIT_RATE = 16 * AUDIO_SAMPLING_RATE
    private const val AUDIO_CHANNELS = 1
  }

  data class Video(val path: String, val durationMs: Long, val size: Size)

  private val outputPath = File.createTempFile("mrousavy", options.fileType.toExtension(), context.cacheDir)

  private val bitRate = getBitRate()
  private val recordingManager = ChunkedRecordingManager.fromParams(
    size, enableAudio, fps, orientation, options, outputPath
  )
  private val recorder: ChunkedRecorder = ChunkedRecorder(recordingManager)
  private var startTime: Long? = null
  val surface: Surface
    get() {
      return recordingManager.surface
    }

  fun start() {
    synchronized(this) {
      Log.i(TAG, "Starting RecordingSession..")
      startTime = System.currentTimeMillis()
    }
  }

  fun stop() {
    synchronized(this) {
      Log.i(TAG, "Stopping RecordingSession..")
      try {
        recorder.sendShutdown()
      } catch (e: Error) {
        Log.e(TAG, "Failed to stop MediaRecorder!", e)
      }

      val stopTime = System.currentTimeMillis()
      val durationMs = stopTime - (startTime ?: stopTime)
      //callback(Video(outputFile.absolutePath, durationMs, size))
    }
  }

  fun pause() {
    synchronized(this) {
      Log.i(TAG, "Pausing Recording Session..")
      // TODO: Implement pausing
    }
  }

  fun resume() {
    synchronized(this) {
      Log.i(TAG, "Resuming Recording Session..")
      // TODO: Implement pausing
    }
  }

  /**
   * Get the bit-rate to use, in bits per seconds.
   * This can either be overridden, multiplied, or just left at the recommended value.
   */
  private fun getBitRate(): Int {
    var bitRate = getRecommendedBitRate(fps ?: 30, options.videoCodec, hdr)
    options.videoBitRateOverride?.let { override ->
      // Mbps -> bps
      bitRate = (override * 1_000_000).toInt()
    }
    options.videoBitRateMultiplier?.let { multiplier ->
      // multiply by 1.2, 0.8, ...
      bitRate = (bitRate * multiplier).toInt()
    }
    return bitRate
  }

  private fun getMaxFileSize(): Long {
    val statFs = StatFsHelper.getInstance()
    val availableStorage = statFs.getAvailableStorageSpace(StatFsHelper.StorageType.INTERNAL)
    Log.i(TAG, "Maximum available storage space: ${availableStorage / 1_000} kB")
    return availableStorage
  }

  override fun toString(): String {
    val audio = if (enableAudio) "with audio" else "without audio"
    return "${size.width} x ${size.height} @ $fps FPS ${options.videoCodec} ${options.fileType} " +
      "$orientation ${bitRate / 1_000_000.0} Mbps RecordingSession ($audio)"
  }

  fun onFrame() {
    recorder.sendFrameAvailable()
  }
}
