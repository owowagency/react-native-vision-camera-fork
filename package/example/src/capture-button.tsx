import React, { useCallback, useRef, useState } from 'react'
import { TouchableOpacity, StyleSheet, View, StyleProp, ViewStyle } from 'react-native'
import { Camera, VideoFile } from 'react-native-vision-camera'

interface RecordingButtonProps {
  style: StyleProp<ViewStyle>
  camera: React.RefObject<Camera>
  onMediaCaptured: (media: VideoFile, mediaType: string) => void
  enabled: boolean
}

export const RecordingButton: React.FC<RecordingButtonProps> = ({ style, camera, onMediaCaptured, enabled }) => {
  const isRecording = useRef(false)
  // UseRef won't trigger a re-render
  const [, setRecordingState] = useState(false)

  const onStoppedRecording = useCallback(() => {
    isRecording.current = false
    setRecordingState(false)
    console.log('stopped recording video!')
  }, [])

  const stopRecording = useCallback(async () => {
    try {
      if (camera.current === null) throw new Error('Camera ref is null!') // Error handling could be more graceful

      console.log('calling stopRecording()...')
      await camera.current.stopRecording()
      console.log('called stopRecording()!')
    } catch (e) {
      console.error('failed to stop recording!', e)
    }
  }, [camera])

  const startRecording = useCallback(() => {
    console.log('press')
    try {
      if (camera.current === null) throw new Error('Camera ref is null!') // Error handling could be more graceful

      console.log('calling startRecording()...')
      camera.current.startRecording({
        onRecordingError: (error) => {
          console.error('Recording failed!', error)
          onStoppedRecording()
        },
        onRecordingFinished: (video) => {
          onMediaCaptured(video, 'video')
          onStoppedRecording()
        },
      })
      console.log('called startRecording()!')
      isRecording.current = true
      setRecordingState(true)
    } catch (e) {
      console.error('failed to start recording!', e, 'camera')
    }
  }, [camera, onMediaCaptured, onStoppedRecording])

  const handlePress = () => {
    if (isRecording.current) stopRecording()
    else startRecording()
  }

  return (
    <TouchableOpacity style={[styles.captureButton, style]} onPress={handlePress} disabled={!enabled}>
      <View style={isRecording.current ? styles.recordingSquare : styles.innerCircle} />
    </TouchableOpacity>
  )
}

const styles = StyleSheet.create({
  captureButton: {
    height: 80,
    width: 80,
    borderRadius: 40,
    borderWidth: 3,
    borderColor: 'white',
    backgroundColor: 'transparent',
    justifyContent: 'center',
    alignItems: 'center',
  },
  innerCircle: {
    height: 70,
    width: 70,
    borderRadius: 35,
    backgroundColor: '#FF3B30',
  },
  recordingSquare: {
    height: 40,
    width: 40,
    borderRadius: 10,
    backgroundColor: '#FF3B30',
  },
})

export default RecordingButton