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
import android.os.Environment
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
class RecordingSession(
  context: Context,
  val cameraId: String,
  val size: Size,
  private val enableAudio: Boolean,
  private val fps: Int? = null,
  private val hdr: Boolean = false,
  private val cameraOrientation: Orientation,
  private val options: RecordVideoOptions,
  private val callback: (video: Video) -> Unit,
  private val onError: (error: CameraError) -> Unit,
  private val allCallbacks: CameraSession.Callback,
) {
  companion object {
    private const val TAG = "RecordingSession"

    private const val AUDIO_SAMPLING_RATE = 44_100
    private const val AUDIO_BIT_RATE = 16 * AUDIO_SAMPLING_RATE
    private const val AUDIO_CHANNELS = 1
  }

  data class Video(val path: String, val durationMs: Long, val size: Size)

  private val outputPath = run {
    val videoDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
    val sdf = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.US)
    val videoFileName = "VID_${sdf.format(Date())}"
    File(videoDir!!, videoFileName)
  }

  private val bitRate = getBitRate()
  private val recorder = ChunkedRecordingManager.fromParams(
    allCallbacks,
    size,
    enableAudio,
    fps,
    cameraOrientation,
    bitRate,
    options,
    outputPath
  )
  private var startTime: Long? = null
  val surface: Surface
    get() {
      return recorder.surface
    }

  fun start() {
    synchronized(this) {
      Log.i(TAG, "Starting RecordingSession..")
      startTime = System.currentTimeMillis()
      recorder.start()
    }
  }

  fun stop() {
    synchronized(this) {
      Log.i(TAG, "Stopping RecordingSession..")
      try {
        recorder.finish()
      } catch (e: Error) {
        Log.e(TAG, "Failed to stop MediaRecorder!", e)
      }

      val stopTime = System.currentTimeMillis()
      val durationMs = stopTime - (startTime ?: stopTime)
      Log.i(TAG, "Finished recording video at $outputPath")
      callback(Video(outputPath.absolutePath, durationMs, size))
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
      "$cameraOrientation ${bitRate / 1_000_000.0} Mbps RecordingSession ($audio)"
  }

  fun onFrame() {
  }
}
