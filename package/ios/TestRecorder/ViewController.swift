//
//  ViewController.swift
//  TestRecorder
//
//  Created by Rafael Bastos on 11/07/2024.
//  Copyright © 2024 mrousavy. All rights reserved.
//

import UIKit
import AVFoundation

class ViewController: UIViewController {
    
    @IBOutlet weak var recordButton: UIButton!
    
    let cameraView = CameraView()

    override func viewDidLoad() {
        super.viewDidLoad()
        
        cameraView.translatesAutoresizingMaskIntoConstraints = false;
        view.insertSubview(cameraView, at: 0)
        NSLayoutConstraint.activate([
            cameraView.topAnchor.constraint(equalTo: view.topAnchor),
            cameraView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            cameraView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            cameraView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
        
        recordButton.isHidden = true
        cameraView.onInitialized = { _ in
            DispatchQueue.main.async {
                self.recordButton.isHidden = false
            }
        }
        
        Task { @MainActor in
            await requestAuthorizations()
            
            cameraView.photo = true
            cameraView.video = true
            cameraView.audio = true
            cameraView.isActive = true
            cameraView.cameraId = getCameraDeviceId() as NSString?
            cameraView.didSetProps([])
        }
    }
    
    func isAuthorized(for mediaType: AVMediaType) async -> Bool {
        let status = AVCaptureDevice.authorizationStatus(for: mediaType)
        var isAuthorized = status == .authorized
        if status == .notDetermined {
            isAuthorized = await AVCaptureDevice.requestAccess(for: mediaType)
        }
        return isAuthorized
    }


    func requestAuthorizations() async {
        guard await isAuthorized(for: .video) else { return }
        guard await isAuthorized(for: .audio) else { return }
        // Set up the capture session.
    }
    
    private func getCameraDeviceId() -> String? {
        let deviceTypes: [AVCaptureDevice.DeviceType] = [
            .builtInWideAngleCamera
        ]
        let discoverySession = AVCaptureDevice.DiscoverySession(deviceTypes: deviceTypes, mediaType: .video, position: .back)
        
        let device = discoverySession.devices.first
        
        return device?.uniqueID
    }
    
    @IBAction
    func toggleRecord(_ button: UIButton) {
        if button.title(for: .normal) == "Stop" {
            
            cameraView.stopRecording(promise: Promise(
                resolver: { result in
                    print("result")
                }, rejecter: { code, message, cause in
                    print("error")
                }))
            
            button.setTitle("Record", for: .normal)
            button.configuration = .filled()
            
        } else {
            cameraView.startRecording(
                options: [
                    "videoCodec": "h265",
                ]) { callback in
                    print("callback", callback)
                }
            
            button.setTitle("Stop", for: .normal)
            button.configuration = .bordered()
        }
    }

}

