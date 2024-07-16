//
//  ReactStubs.swift
//  TestRecorder
//
//  Created by Rafael Bastos on 11/07/2024.
//  Copyright © 2024 mrousavy. All rights reserved.
//

import UIKit


enum RCTLogLevel: String {
  case trace
  case info
  case warning
  case error
}

enum RCTLogSource {
  case native
}

func RCTDefaultLogFunction(_ level: RCTLogLevel, _ source: RCTLogSource, _ file: String, _ line: NSNumber, _ message: String) {
  print(level.rawValue, "-", message)
}

typealias RCTDirectEventBlock = (Any?) -> Void
typealias RCTPromiseResolveBlock = (Any?) -> Void
typealias RCTPromiseRejectBlock = (String, String, NSError?) -> Void
typealias RCTResponseSenderBlock = (Any) -> Void

func NSNull() -> [String: String] {
  return [:]
}


func makeReactError(_ cameraError: CameraError, cause: NSError?) -> [String: Any] {
  var causeDictionary: [String: Any]?
  if let cause = cause {
    causeDictionary = [
      "cause": "\(cause.domain): \(cause.code) \(cause.description)",
      "userInfo": cause.userInfo
    ]
  }
  return [
    "error": "\(cameraError.code): \(cameraError.message)",
    "extra": [
      "code": cameraError.code,
      "message": cameraError.message,
      "cause": causeDictionary ?? NSNull(),
    ]
  ]
}

func makeReactError(_ cameraError: CameraError) -> [String: Any] {
  return makeReactError(cameraError, cause: nil)
}


class RCTFPSGraph: UIView {
  convenience init(frame: CGRect, color: UIColor) {
    self.init(frame: frame)
  }
  
  func onTick(_ tick: CFTimeInterval) {
    
  }
}

func RCTTempFilePath(_ ext: String, _ error: ErrorPointer) -> String? {
  let directory = NSTemporaryDirectory().appending("ReactNative")
  let fm = FileManager.default
  if fm.fileExists(atPath: directory) {
    try! fm.removeItem(atPath: directory)
  }
  if !fm.fileExists(atPath: directory) {
    try! fm.createDirectory(atPath: directory, withIntermediateDirectories: true)
  }
  return directory
    .appending("/").appending(UUID().uuidString)
    .appending(".").appending(ext)
}
