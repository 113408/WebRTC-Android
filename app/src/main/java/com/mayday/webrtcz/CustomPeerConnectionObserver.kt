package com.mayday.webrtcz

import org.webrtc.*
import timber.log.Timber


open class CustomPeerConnectionObserver : PeerConnection.Observer {
    init {
        Timber.tag("PeerConnectionObserver")
    }
    override fun onAddTrack(rtpReceiver: RtpReceiver, mediaStreams: Array<out MediaStream>?) {
        Timber.d("onAddTrack() called with: rtpReceiver = [$rtpReceiver] and mediaStreams= [$mediaStreams]")
    }

    override fun onSignalingChange(signalingState: PeerConnection.SignalingState) {
        Timber.d("onSignalingChange() called with: signalingState = [$signalingState]")
    }

    override fun onIceConnectionChange(iceConnectionState: PeerConnection.IceConnectionState) {
        Timber.d("onIceConnectionChange() called with: iceConnectionState = [$iceConnectionState]")
    }

    override fun onIceConnectionReceivingChange(b: Boolean) {
        Timber.d("onIceConnectionReceivingChange() called with: b = [$b]")
    }

    override fun onIceGatheringChange(iceGatheringState: PeerConnection.IceGatheringState) {
        Timber.d("onIceGatheringChange() called with: iceGatheringState = [$iceGatheringState]")
    }

    override fun onIceCandidate(iceCandidate: IceCandidate) {
        Timber.d("onIceCandidate() called with: iceCandidate = [$iceCandidate]")
    }

    override fun onIceCandidatesRemoved(iceCandidates: Array<IceCandidate>) {
        Timber.d("onIceCandidatesRemoved() called with: iceCandidates = [$iceCandidates]")
    }

    override fun onAddStream(mediaStream: MediaStream) {
        Timber.d("onAddStream() called with: mediaStream = [$mediaStream]")
    }

    override fun onRemoveStream(mediaStream: MediaStream) {
        Timber.d("onRemoveStream() called with: mediaStream = [$mediaStream]")
    }

    override fun onDataChannel(dataChannel: DataChannel) {
        Timber.d("onDataChannel() called with: dataChannel = [$dataChannel]")
    }

    override fun onRenegotiationNeeded() {
        Timber.d("onRenegotiationNeeded() called")
    }
}