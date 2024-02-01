import React, { useCallback, useRef, useState } from 'react'
import { Button, StyleSheet, Text, View } from 'react-native'
import {
  Camera,
  useCameraPermission,
  useCameraDevice,
  useCameraFormat,
  PhotoFile,
  VideoFile,
  CameraRuntimeError,
  Orientation,
  CameraDevice,
} from 'react-native-vision-camera'
import { RecordingButton } from './capture-button'
import { useIsForeground } from './is-foreground'

export default function CameraScreen() {
  const camera = useRef<Camera>(null)
  const { hasPermission, requestPermission } = useCameraPermission()
  const [isCameraInitialized, setIsCameraInitialized] = useState<boolean>(false)

  const isForeground: boolean = useIsForeground()
  const isActive: boolean = isForeground // Should be combined with isFocused hook

  const onError = useCallback((error: CameraRuntimeError) => {
    console.error(error)
  }, [])

  const onInitialized = useCallback(() => {
    console.log('Camera initialized!')
    setIsCameraInitialized(true)
  }, [])

  const onMediaCaptured = useCallback((media: PhotoFile | VideoFile) => {
    console.log(`Media captured! ${JSON.stringify(media)}`)
  }, [])

  if (!hasPermission) requestPermission()
  // Error handling in case they refuse to give permission

  const device = useCameraDevice('back')
  const format = useCameraFormat(device, [{ videoResolution: { width: 3048, height: 2160 } }, { fps: 60 }]) // this sets as a target

  //Orientation detection
  const [orientation, setOrientation] = useState<Orientation>('portrait')

  const toggleOrientation = () => {
    setOrientation(
      (currentOrientation) => (currentOrientation === 'landscape-left' ? 'portrait' : 'landscape-left'), // Can adjust this and the type to match what we want
    )
  }

  // eslint-disable-next-line @typescript-eslint/no-unnecessary-condition
  if (device === null) return <Text>Camera not available. Does user have permissions: {hasPermission}</Text>

  return (
    hasPermission && (
      <View style={styles.container}>
        <Camera
          ref={camera}
          style={StyleSheet.absoluteFill}
          device={device as CameraDevice}
          format={format}
          onInitialized={onInitialized}
          onError={onError}
          video={true}
          orientation={orientation} // TODO: #60
          isActive={isActive}
        />
        <RecordingButton
          style={[styles.captureButton, orientation === 'portrait' ? styles.portrait : styles.landscape]}
          camera={camera}
          onMediaCaptured={onMediaCaptured}
          enabled={isCameraInitialized}
        />
        <View style={[styles.button, orientation === 'portrait' ? styles.togglePortrait : styles.toggleLandscape]}>
          <Button title="Toggle Orientation" onPress={toggleOrientation} color="#841584" accessibilityLabel="Toggle camera orientation" />
        </View>
      </View>
    )
  )
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: 'black',
  },
  captureButton: {
    position: 'absolute',
    alignSelf: 'center',
  },
  button: {
    position: 'absolute',
    alignSelf: 'center',
  },
  togglePortrait: {
    bottom: 110, // needs refined
  },
  toggleLandscape: {
    transform: [{ rotate: '90deg' }],
    bottom: '43%', // Should come from SafeAreaProvider, hardcoded right now, should roughly appear above the button
    left: 50, // needs refined
  },
  portrait: {
    bottom: 20, // needs refined
  },
  landscape: {
    bottom: '40%', // Should come from SafeAreaProvider
    left: 20, // needs refined
  },
})
