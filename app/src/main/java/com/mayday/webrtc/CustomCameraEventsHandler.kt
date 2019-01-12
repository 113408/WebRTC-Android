package com.mayday.webrtc

import android.util.Log
import org.webrtc.CameraVideoCapturer


class CustomCameraEventsHandler : CameraVideoCapturer.CameraEventsHandler {
    override fun onCameraOpening(cameraId: String?) {
        Log.d(logTag, "onCameraOpening() called with: cameraId = [$cameraId]")
    }

    override fun onCameraDisconnected() {
        Log.d(logTag, "onCameraDisconnect() called")
    }

    private val logTag = this.javaClass.canonicalName


    override fun onCameraError(s: String) {
        Log.d(logTag, "onCameraError() called with: s = [$s]")
    }

    override fun onCameraFreezed(s: String) {
        Log.d(logTag, "onCameraFreezed() called with: s = [$s]")
    }

    fun onCameraOpening(i: Int) {

    }

    override fun onFirstFrameAvailable() {
        Log.d(logTag, "onFirstFrameAvailable() called")
    }

    override fun onCameraClosed() {
        Log.d(logTag, "onCameraClosed() called")
    }
}