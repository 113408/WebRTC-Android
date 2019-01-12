package com.mayday.webrtcz

import android.util.Log
import org.webrtc.SessionDescription
import org.webrtc.SdpObserver


open class CustomSdpObserver(logTag: String) : SdpObserver {


    private var tag = this.javaClass.canonicalName

    init {
        this.tag = "${this.tag} $logTag"
    }


    override fun onCreateSuccess(sessionDescription: SessionDescription) {
        Log.d(tag, "onCreateSuccess() called with: sessionDescription = [$sessionDescription]")
    }

    override fun onSetSuccess() {
        Log.d(tag, "onSetSuccess() called")
    }

    override fun onCreateFailure(s: String) {
        Log.d(tag, "onCreateFailure() called with: s = [$s]")
    }

    override fun onSetFailure(s: String) {
        Log.d(tag, "onSetFailure() called with: s = [$s]")
    }

}