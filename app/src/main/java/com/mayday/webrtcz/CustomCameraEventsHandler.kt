package com.mayday.webrtcz

import org.webrtc.CameraVideoCapturer
import timber.log.Timber


class CustomCameraEventsHandler : CameraVideoCapturer.CameraEventsHandler {
    init {
        Timber.tag("CustomCameraHandler")
    }
    override fun onCameraOpening(cameraId: String?) {
        Timber.d("onCameraOpening() called with: cameraId = [$cameraId]")
    }

    override fun onCameraDisconnected() {
        Timber.d("onCameraDisconnect() called")
    }

    override fun onCameraError(s: String) {
        Timber.d("onCameraError() called with: s = [$s]")
    }

    override fun onCameraFreezed(s: String) {
        Timber.d("onCameraFreezed() called with: s = [$s]")
    }

    override fun onFirstFrameAvailable() {
        Timber.d("onFirstFrameAvailable() called")
    }

    override fun onCameraClosed() {
        Timber.d("onCameraClosed() called")
    }
}