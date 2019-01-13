package com.mayday.webrtcz

import org.webrtc.SessionDescription
import org.webrtc.SdpObserver
import timber.log.Timber


open class CustomSdpObserver(logTag: String) : SdpObserver {

    init {
        Timber.tag("SdpOBVS $logTag")
    }


    override fun onCreateSuccess(sessionDescription: SessionDescription) {
        Timber.d("onCreateSuccess() called with: sessionDescription = [$sessionDescription]")
    }

    override fun onSetSuccess() {
        Timber.d("onSetSuccess() called")
    }

    override fun onCreateFailure(s: String) {
        Timber.d("onCreateFailure() called with: s = [$s]")
    }

    override fun onSetFailure(s: String) {
        Timber.d("onSetFailure() called with: s = [$s]")
    }

}