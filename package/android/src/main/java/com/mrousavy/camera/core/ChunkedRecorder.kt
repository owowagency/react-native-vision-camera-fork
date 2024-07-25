package com.mrousavy.camera.core

import android.media.MediaCodec
import android.media.MediaCodec.BufferInfo
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import android.util.Size
import android.view.Surface
import com.mrousavy.camera.types.Orientation
import com.mrousavy.camera.types.RecordVideoOptions
import java.io.File
import java.nio.ByteBuffer

class ChunkedRecordingManager(private val encoder: MediaCodec, private val outputDirectory: File, private val orientationHint: Int, private val iFrameInterval: Int, private val callbacks: CameraSession.Callback) :
  MediaCodec.Callback() {
  companion object {
    private const val TAG = "ChunkedRecorder"

    fun fromParams(
      callbacks: CameraSession.Callback,
      size: Size,
      enableAudio: Boolean,
      fps: Int? = null,
      cameraOrientation: Orientation,
      bitRate: Int,
      options: RecordVideoOptions,
      outputDirectory: File,
      iFrameInterval: Int = 5
    ): ChunkedRecordingManager {
      val mimeType = options.videoCodec.toMimeType()
      val cameraOrientationDegrees = cameraOrientation.toDegrees()
      val recordingOrientationDegrees = (options.orientation ?: Orientation.PORTRAIT).toDegrees();
      val (width, height) = if (cameraOrientation.isLandscape()) {
        size.height to size.width
      } else {
        size.width to size.height
      }

      val format = MediaFormat.createVideoFormat(mimeType, width, height)

      val codec = MediaCodec.createEncoderByType(mimeType)

      // Set some properties. Failing to specify some of these can cause the MediaCodec
      // configure() call to throw an unhelpful exception.
      format.setInteger(
        MediaFormat.KEY_COLOR_FORMAT,
        MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
      )
      fps?.apply {
        format.setInteger(MediaFormat.KEY_FRAME_RATE, this)
      }
      // TODO: Pull this out into configuration
      format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iFrameInterval)
      format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate)

      Log.d(TAG, "Video Format: $format, camera orientation $cameraOrientationDegrees, recordingOrientation: $recordingOrientationDegrees")
      // Create a MediaCodec encoder, and configure it with our format.  Get a Surface
      // we can use for input and wrap it with a class that handles the EGL work.
      codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
      return ChunkedRecordingManager(
        codec, outputDirectory, recordingOrientationDegrees, iFrameInterval, callbacks
      )
    }
  }

  // In flight details
  private var currentFrameNumber: Int = 0
  private var chunkIndex = -1
  private var encodedFormat: MediaFormat? = null
  private var recording = false;

  private val targetDurationUs = iFrameInterval * 1000000

  val surface: Surface = encoder.createInputSurface()

  init {
    if (!this.outputDirectory.exists()) {
      this.outputDirectory.mkdirs()
    }
    encoder.setCallback(this)
  }

  // Muxer specific
  private class MuxerContext(val muxer: MediaMuxer, val filepath: File, val chunkIndex: Int, startTimeUs: Long, encodedFormat: MediaFormat, val callbacks: CameraSession.Callback,) {
    val videoTrack: Int = muxer.addTrack(encodedFormat)
    val startTimeUs: Long = startTimeUs

    init {
      muxer.start()
    }


    fun finish() {
      muxer.stop()
      muxer.release()
      callbacks.onVideoChunkReady(filepath, chunkIndex)
    }
  }

  private var muxerContext: MuxerContext? = null

  private fun createNextMuxer(bufferInfo: BufferInfo) {
    muxerContext?.finish()
    chunkIndex++

    val newFileName = "$chunkIndex.mp4"
    val newOutputFile = File(this.outputDirectory, newFileName)
    Log.i(TAG, "Creating new muxer for file: $newFileName")
    val muxer = MediaMuxer(
      newOutputFile.absolutePath,
      MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
    )
    muxer.setOrientationHint(orientationHint)
    muxerContext = MuxerContext(
        muxer, newOutputFile, chunkIndex, bufferInfo.presentationTimeUs, this.encodedFormat!!, this.callbacks
    )
  }

  private fun atKeyframe(bufferInfo: BufferInfo): Boolean {
    return (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
  }

  private fun chunkLengthUs(bufferInfo: BufferInfo): Long {
    return bufferInfo.presentationTimeUs - muxerContext!!.startTimeUs
  }

  fun start() {
    encoder.start()
    recording = true
  }

  fun finish() {
    synchronized(this) {
      muxerContext?.finish()
      recording = false
      muxerContext = null
      encoder.stop()
    }
  }

  // MediaCodec.Callback methods
  override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
  }

  override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, bufferInfo: MediaCodec.BufferInfo) {
    synchronized(this) {
      if (!recording) {
        return
      }
      val encodedData: ByteBuffer = encoder.getOutputBuffer(index)
        ?: throw RuntimeException("getOutputBuffer  was null")

      if (muxerContext == null || (atKeyframe(bufferInfo) && chunkLengthUs(bufferInfo) >= targetDurationUs)) {
        this.createNextMuxer(bufferInfo)
      }
      muxerContext!!.muxer.writeSampleData(muxerContext!!.videoTrack, encodedData, bufferInfo)
      encoder.releaseOutputBuffer(index, false)
    }
  }

  override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
    // Implement error handling
    Log.e(TAG, "Codec error: ${e.message}")
  }

  override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
    encodedFormat = format
  }
}
