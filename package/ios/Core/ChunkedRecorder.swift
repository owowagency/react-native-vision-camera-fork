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
  
  enum ChunkType {
    case initialization
    case data(index: UInt64)
  }

  struct Chunk {
    let url: URL
    let type: ChunkType
  }
  
  let outputURL: URL
  let onChunkReady: ((Chunk) -> Void)
  
  private var chunkIndex: UInt64 = 0
  
  init(url: URL, onChunkReady: @escaping ((Chunk) -> Void)) throws {
    outputURL = url
    self.onChunkReady = onChunkReady
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
    let url = outputURL.appendingPathComponent("init.mp4")
    save(data: data, url: url)
    onChunkReady(url: url, type: .initialization)
  }
  
  private func saveSegment(_ data: Data) {
    let name = "\(chunkIndex).mp4"
    let url = outputURL.appendingPathComponent(name)
    save(data: data, url: url)
    onChunkReady(url: url, type: .data(index: chunkIndex))
    chunkIndex += 1
  }
  
  private func save(data: Data, url: URL) {
    do {
      try data.write(to: url)
    } catch {
      ReactLogger.log(level: .error, message: "Unable to write \(url): \(error.localizedDescription)")
    }
  }
  
  private func onChunkReady(url: URL, type: ChunkType) {
    onChunkReady(Chunk(url: url, type: type))
  }
  
}
