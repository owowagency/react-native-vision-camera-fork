package com.mrousavy.camera.types

import android.media.MediaRecorder
import android.media.MediaFormat

enum class VideoCodec(override val unionValue: String) : JSUnionValue {
  H264("h264"),
  H265("h265");

  fun toVideoEncoder(): Int =
    when (this) {
      H264 -> MediaRecorder.VideoEncoder.H264
      H265 -> MediaRecorder.VideoEncoder.HEVC
    }

  fun toMimeType(): String =
    when (this) {
      H264 -> MediaFormat.MIMETYPE_VIDEO_AVC
      H265 -> MediaFormat.MIMETYPE_VIDEO_HEVC
    }

  companion object : JSUnionValue.Companion<VideoCodec> {
    override fun fromUnionValue(unionValue: String?): VideoCodec =
      when (unionValue) {
        "h264" -> H264
        "h265" -> H265
        else -> H264
      }
  }
}
