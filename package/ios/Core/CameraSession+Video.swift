//
//  CameraSession+Video.swift
//  VisionCamera
//
//  Created by Marc Rousavy on 11.10.23.
//  Copyright © 2023 mrousavy. All rights reserved.
//

import AVFoundation
import Foundation
import UIKit

extension CameraSession {
  /**
   Starts a video + audio recording with a custom Asset Writer.
   */
  func startRecording(options: RecordVideoOptions,
                      filePath: String,
                      onVideoRecorded: @escaping (_ video: Video) -> Void,
                      onError: @escaping (_ error: CameraError) -> Void) {
    // Run on Camera Queue
    CameraQueues.cameraQueue.async {
      let start = DispatchTime.now()
      ReactLogger.log(level: .info, message: "Starting Video recording...")

      // Get Video Output
      guard let videoOutput = self.videoOutput else {
        if self.configuration?.video == .disabled {
          onError(.capture(.videoNotEnabled))
        } else {
          onError(.session(.cameraNotReady))
        }
        return
      }

      let enableAudio = self.configuration?.audio != .disabled

      // Callback for when new chunks are ready
      let onChunkReady: (ChunkedRecorder.Chunk) -> Void = { chunk in
        guard let delegate = self.delegate else {
          return
        }
        delegate.onVideoChunkReady(chunk: chunk)
      }

      // Callback for when the recording ends
      let onFinish = { (recordingSession: RecordingSession, status: AVAssetWriter.Status, error: Error?) in
        defer {
          // Disable Audio Session again
          if enableAudio {
            CameraQueues.audioQueue.async {
              self.deactivateAudioSession()
            }
          }
        }

        self.isRecording = false
        self.recordingSession = nil
        ReactLogger.log(level: .info, message: "RecordingSession finished with status \(status.descriptor).")

        if let error = error as NSError? {
          ReactLogger.log(level: .error, message: "RecordingSession Error \(error.code): \(error.description)")
          // Something went wrong, we have an error
          if error.domain == "capture/aborted" {
            onError(.capture(.aborted))
          } else if error.code == -11807 {
            onError(.capture(.insufficientStorage))
          } else {
            onError(.capture(.unknown(message: "An unknown recording error occured! \(error.code) \(error.description)")))
          }
        } else {
          if status == .completed {
            // Recording was successfully saved
            let video = Video(path: recordingSession.outputDiretory.absoluteString,
                              duration: recordingSession.duration,
                              size: recordingSession.size ?? CGSize.zero)
            onVideoRecorded(video)
          } else {
            // Recording wasn't saved and we don't have an error either.
            onError(.unknown(message: "AVAssetWriter completed with status: \(status.descriptor)"))
          }
        }
      }

      if !FileManager.default.fileExists(atPath: filePath) {
        do {
          try FileManager.default.createDirectory(atPath: filePath, withIntermediateDirectories: true)
        } catch {
          onError(.capture(.createRecordingDirectoryError(message: error.localizedDescription)))
          return
        }
      }

      ReactLogger.log(level: .info, message: "Will record to temporary file: \(filePath)")

      do {
        // Create RecordingSession for the temp file
        let recordingSession = try RecordingSession(outputDiretory: filePath,
                                                    fileType: options.fileType,
                                                    onChunkReady: onChunkReady,
                                                    completion: onFinish)

        // Init Audio + Activate Audio Session (optional)
        if enableAudio,
           let audioOutput = self.audioOutput,
           let audioInput = self.audioDeviceInput {
          ReactLogger.log(level: .trace, message: "Enabling Audio for Recording...")
          // Activate Audio Session asynchronously
          CameraQueues.audioQueue.async {
            do {
              try self.activateAudioSession()
            } catch {
              self.onConfigureError(error)
            }
          }

          // Initialize audio asset writer
          let audioSettings = audioOutput.recommendedAudioSettingsForAssetWriter(writingTo: options.fileType)
          recordingSession.initializeAudioWriter(withSettings: audioSettings,
                                                 format: audioInput.device.activeFormat.formatDescription)
        }

        // Init Video
        let videoSettings = try videoOutput.recommendedVideoSettings(forOptions: options)
        recordingSession.initializeVideoWriter(withSettings: videoSettings)

        // start recording session with or without audio.
        // Use Video [AVCaptureSession] clock as a timebase - all other sessions (here; audio) have to be synced to that Clock.
        try recordingSession.start(clock: self.captureSession.clock)
        self.recordingSession = recordingSession
        self.isRecording = true

        let end = DispatchTime.now()
        ReactLogger.log(level: .info, message: "RecordingSesssion started in \(Double(end.uptimeNanoseconds - start.uptimeNanoseconds) / 1_000_000)ms!")
      } catch let error as NSError {
        if let error = error as? CameraError {
          onError(error)
        } else {
          onError(.capture(.createRecorderError(message: "RecordingSession failed with unknown error: \(error.description)")))
        }
        return
      }
    }
  }

  /**
   Stops an active recording.
   */
  func stopRecording(promise: Promise) {
    CameraQueues.cameraQueue.async {
      withPromise(promise) {
        guard let recordingSession = self.recordingSession else {
          throw CameraError.capture(.noRecordingInProgress)
        }
        // Use Video [AVCaptureSession] clock as a timebase - all other sessions (here; audio) have to be synced to that Clock.
        recordingSession.stop(clock: self.captureSession.clock)
        // There might be late frames, so maybe we need to still provide more Frames to the RecordingSession. Let's keep isRecording true for now.
        return nil
      }
    }
  }

  /**
   Pauses an active recording.
   */
  func pauseRecording(promise: Promise) {
    CameraQueues.cameraQueue.async {
      withPromise(promise) {
        guard self.recordingSession != nil else {
          // there's no active recording!
          throw CameraError.capture(.noRecordingInProgress)
        }
        self.isRecording = false
        return nil
      }
    }
  }

  /**
   Resumes an active, but paused recording.
   */
  func resumeRecording(promise: Promise) {
    CameraQueues.cameraQueue.async {
      withPromise(promise) {
        guard self.recordingSession != nil else {
          // there's no active recording!
          throw CameraError.capture(.noRecordingInProgress)
        }
        self.isRecording = true
        return nil
      }
    }
  }

  func lockCurrentExposure(promise: Promise) {
    CameraQueues.cameraQueue.async {
      withPromise(promise) {
        guard let captureDevice = AVCaptureDevice.default(for: .video) else {
          print("No capture device available")
          return
        }

        guard captureDevice.isExposureModeSupported(.custom) else {
          ReactLogger.log(level: .info, message: "Custom exposure mode not supported")
          return
        }
        do {
          // Lock the device for configuration
          try captureDevice.lockForConfiguration()

          // Get the current exposure duration and ISO
          let currentExposureDuration = captureDevice.exposureDuration
          let currentISO = captureDevice.iso

          // Check if the device supports custom exposure settings
          if captureDevice.isExposureModeSupported(.custom) {
            // Lock the current exposure and ISO by setting custom exposure mode
            captureDevice.setExposureModeCustom(duration: currentExposureDuration, iso: currentISO, completionHandler: nil)
            ReactLogger.log(level: .info, message: "Exposure and ISO locked at current values")
          } else {
            ReactLogger.log(level: .info, message:"Custom exposure mode not supported")
          }

          // Unlock the device after configuration
          captureDevice.unlockForConfiguration()

        } catch {
          ReactLogger.log(level: .warning, message:"Error locking exposure: \(error)")
        }

        return nil
      }
    }
  }

  func unlockCurrentExposure(promise: Promise) {
    CameraQueues.cameraQueue.async {
      withPromise(promise) {
        guard let captureDevice = AVCaptureDevice.default(for: .video) else {
          print("No capture device available")
          return
        }

        do {
          if captureDevice.isExposureModeSupported(.autoExpose) {
            try captureDevice.lockForConfiguration()
            captureDevice.exposureMode = .continuousAutoExposure
            captureDevice.unlockForConfiguration()
          }
        } catch {
          ReactLogger.log(level: .warning, message:"Error unlocking exposure: \(error)")
        }

        return nil
      }
    }
  }
}
