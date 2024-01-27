package com.mrousavy.camera.core

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import android.util.Size
import android.view.Surface
import java.io.File
import com.mrousavy.camera.types.Orientation
import com.mrousavy.camera.types.RecordVideoOptions
import java.nio.ByteBuffer

class ChunkedRecordingManager(
  private val encoder: MediaCodec,
  private val outputDirectory: File,
  private val orientationHint: Int,
) {
  companion object {
    private const val TAG = "ChunkedRecorder"
    private const val targetDurationUs = 10 * 1000000

    fun fromParams(
      size: Size,
      enableAudio: Boolean,
      fps: Int? = null,
      orientation: Orientation,
      options: RecordVideoOptions,
      outputDirectory: File,
    ): ChunkedRecordingManager {

      val mimeType = options.videoCodec.toMimeType()
      val format = MediaFormat.createVideoFormat(mimeType, size.width, size.height)

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
      format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)


      Log.d(TAG, "Video Format: $format")
      // Create a MediaCodec encoder, and configure it with our format.  Get a Surface
      // we can use for input and wrap it with a class that handles the EGL work.
      codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
      return ChunkedRecordingManager(codec, outputDirectory, orientation.toDegrees())
    }
  }

  // In flight details
  private val bufferInfo = MediaCodec.BufferInfo()
  private var currentFrameNumber: Int = 0
  private var chunkIndex = 0
  private var encodedFormat: MediaFormat? = null

  val surface: Surface = encoder.createInputSurface()

  init {
    if (!this.outputDirectory.exists()) {
      this.outputDirectory.mkdirs()
    }
  }

  // Muxer specific
  private class MuxerContext(
    muxer: MediaMuxer,
    startTimeUs: Long,
    encodedFormat: MediaFormat
  ) {
    val muxer = muxer
    val videoTrack: Int = muxer.addTrack(encodedFormat)
    val startTimeUs: Long = startTimeUs

    init {
      muxer.start()
    }

    fun finish() {
      muxer.stop()
      muxer.release()
    }
  }

  private lateinit var muxerContext: MuxerContext

  private fun createNextMuxer() {
    if (::muxerContext.isInitialized) {
      muxerContext.finish()
      chunkIndex++
    }

    val newFileName = "$chunkIndex.mp4"
    val newOutputFile = File(this.outputDirectory, newFileName)
    Log.d(TAG, "Creating new muxer for file: $newFileName")
    val muxer = MediaMuxer(
      newOutputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
    )
    muxerContext = MuxerContext(
      muxer, bufferInfo.presentationTimeUs, this.encodedFormat!!
    )
    muxer.setOrientationHint(orientationHint)
  }

  private fun atKeyframe(): Boolean {
    return (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
  }

  private fun chunkLengthUs(): Long {
    return bufferInfo.presentationTimeUs - muxerContext.startTimeUs
  }

  fun drainEncoder(): Boolean {
    val timeout: Long = 0
    var frameWasEncoded = false

    while (true) {
      var encoderStatus: Int = encoder.dequeueOutputBuffer(bufferInfo, timeout)

      if (encoderStatus < 0) {
        Log.w(
          TAG, "Unexpected result from encoder.dequeueOutputBuffer: $encoderStatus"
        )
      }

      when (encoderStatus) {
        MediaCodec.INFO_TRY_AGAIN_LATER -> break;
        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
          // Should happen before receiving buffers, and should only happen once. The MediaFormat
          // contains the csd-0 and csd-1 keys, which we'll need for MediaMuxer. It's unclear what
          // else MediaMuxer might want, so rather than extract the codec-specific data and
          // reconstruct a new MediaFormat later, we just grab it here and keep it around.
          encodedFormat = encoder.outputFormat
          Log.d(TAG, "encoder output format changed: $encodedFormat")
        }
        else -> {
          var encodedData: ByteBuffer = encoder.getOutputBuffer(encoderStatus)
              ?: throw RuntimeException("encoderOutputBuffer $encoderStatus was null")

          if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            // The codec config data was pulled out when we got the
            // INFO_OUTPUT_FORMAT_CHANGED status.  The MediaMuxer won't accept
            // a single big blob -- it wants separate csd-0/csd-1 chunks --
            // so simply saving this off won't work.
            Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG")
              bufferInfo.size = 0
          }

          if (bufferInfo.size != 0) {
            // adjust the ByteBuffer values to match BufferInfo (not needed?)
            encodedData.position(bufferInfo.offset)
            encodedData.limit(bufferInfo.offset + bufferInfo.size)

            if (!::muxerContext.isInitialized || (atKeyframe() && chunkLengthUs() >= targetDurationUs)) {
              this.createNextMuxer()
            }

            // TODO: we should probably add the presentation time stamp
            // mEncBuffer.add(encodedData, bufferInfo.flags, bufferInfo.presentationTimeUs)

            muxerContext.muxer.writeSampleData(muxerContext.videoTrack, encodedData, bufferInfo)
            frameWasEncoded = true
          }

          encoder.releaseOutputBuffer(encoderStatus, false)

          if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            Log.w(TAG, "reached end of stream unexpectedly")
            break
          }
        }
      }
    }
    return frameWasEncoded
  }

  fun finish() {
    if (::muxerContext.isInitialized) {
      muxerContext.finish()
    }
  }
}

class ChunkedRecorder(private val manager: ChunkedRecordingManager) {
    private val messageChannel = Channel<Message>()

    init {
        CoroutineScope(Dispatchers.Default).launch {
            for (msg in messageChannel) {
                when (msg) {
                    is Message.FrameAvailable -> manager.drainEncoder()
                    is Message.Shutdown -> manager.finish()
                }
            }
        }
    }

    fun sendFrameAvailable() {
        messageChannel.trySend(Message.FrameAvailable)
    }

    fun sendShutdown() {
        messageChannel.trySend(Message.Shutdown)
    }

    sealed class Message {
        object FrameAvailable : Message()
        object Shutdown : Message()
    }
}
