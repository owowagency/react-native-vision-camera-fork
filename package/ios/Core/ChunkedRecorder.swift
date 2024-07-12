//
//  ChunkedRecorder.swift
//  VisionCamera
//
//  Created by Rafael Bastos on 12/07/2024.
//  Copyright © 2024 mrousavy. All rights reserved.
//

import Foundation
import AVFoundation


class ChunkedRecorder: NSObject {
  
  let outputURL: URL
  
  private var initSegment: Data?
  private var index: Int = 0
  
  init(url: URL) throws {
    outputURL = url
    
    guard FileManager.default.fileExists(atPath: outputURL.path) else {
      throw CameraError.unknown(message: "output directory does not exist at: \(outputURL.path)", cause: nil)
    }
  }
  
}

extension ChunkedRecorder: AVAssetWriterDelegate {
  
  func assetWriter(_ writer: AVAssetWriter, 
                   didOutputSegmentData segmentData: Data,
                   segmentType: AVAssetSegmentType,
                   segmentReport: AVAssetSegmentReport?) {
    
    switch segmentType {
    case .initialization:
      saveInitSegment(segmentData)
    case .separable:
      saveSegment(segmentData)
    @unknown default:
      fatalError("Unknown AVAssetSegmentType!")
    }
  }
  
  private func saveInitSegment(_ data: Data) {     
    initSegment = data
  }
  
  private func saveSegment(_ data: Data) {
    guard let initSegment else {
      print("missing init segment")
      return
    }
    
    let file = String(format: "%06d.mp4", index)
    index += 1
    let url = outputURL.appendingPathComponent(file)
    
    do {
      let outputData = initSegment + data
      try outputData.write(to: url)
      print("writing", data.count, "to", url)
    } catch {
      print("Error--->", error)
    }
  }
  
}
