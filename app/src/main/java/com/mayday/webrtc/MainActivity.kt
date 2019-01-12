package com.mayday.webrtc

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.Button
import org.webrtc.*
import com.mayday.webrtc.webrtc.R
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.VideoRenderer
import org.webrtc.VideoTrack
import org.webrtc.MediaStream
import org.webrtc.MediaConstraints
import org.webrtc.SessionDescription
import org.webrtc.VideoCapturer
import org.webrtc.PeerConnectionFactory
import org.webrtc.CameraVideoCapturer
import org.webrtc.EglBase
import org.webrtc.SurfaceViewRenderer
import org.webrtc.CameraEnumerator
import org.webrtc.Camera1Enumerator




class MainActivity : AppCompatActivity(), View.OnClickListener {
    lateinit var peerConnectionFactory: PeerConnectionFactory
    lateinit var audioConstraints: MediaConstraints
    lateinit var videoConstraints: MediaConstraints
    lateinit var sdpConstraints: MediaConstraints
    lateinit var videoSource: VideoSource
    lateinit var localVideoTrack: VideoTrack
    lateinit var audioSource: AudioSource
    lateinit var localAudioTrack: AudioTrack

    lateinit var localVideoView: SurfaceViewRenderer
    lateinit var remoteVideoView: SurfaceViewRenderer
    lateinit var localRenderer: VideoRenderer
    lateinit var remoteRenderer: VideoRenderer

    var localPeer: PeerConnection? = null
    var remotePeer: PeerConnection? = null
    lateinit var start: Button
    lateinit var call: Button
    lateinit var hangup: Button


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        initVideos()
    }


    private fun initViews() {
        start = findViewById(R.id.start_call)
        call = findViewById(R.id.init_call)
        hangup = findViewById(R.id.end_call)
        localVideoView = findViewById(R.id.local_gl_surface_view)
        remoteVideoView = findViewById(R.id.remote_gl_surface_view)

        start.setOnClickListener(this)
        call.setOnClickListener(this)
        hangup.setOnClickListener(this)
    }

    private fun initVideos() {
        val rootEglBase = EglBase.create()
        localVideoView.init(rootEglBase.eglBaseContext, null)
        remoteVideoView.init(rootEglBase.eglBaseContext, null)
        localVideoView.setZOrderMediaOverlay(true)
        remoteVideoView.setZOrderMediaOverlay(true)
    }


    // Cycle through likely device names for the camera and return the first
    // capturer that works, or crash if none do.
    private fun getVideoCapturer(eventsHandler: CameraVideoCapturer.CameraEventsHandler): VideoCapturer {
        val cameraFacing = arrayOf("front", "back")
        val cameraIndex = intArrayOf(0, 1)
        val cameraOrientation = intArrayOf(0, 90, 180, 270)
        for (facing in cameraFacing) {
            for (index in cameraIndex) {
                for (orientation in cameraOrientation) {
                    val name = "Camera " + index + ", Facing " + facing +
                            ", Orientation " + orientation
                    val capturer = createVideoCapturer()
                    if (capturer != null) {
                        Log.d("Using camera: ", name)
                        return capturer
                    }
                }
            }
        }
        throw RuntimeException("Failed to open capture")
    }


    override fun onClick(v: View) {
        when (v.id) {
            R.id.start_call -> {
                start()
            }
            R.id.init_call -> {
                call()
            }
            R.id.end_call -> {
                hangup()
            }
        }
    }


    fun start() {
        start.isEnabled = false
        call.isEnabled = true
        //Initialize PeerConnectionFactory globals.
        //Params are context, initAudio,initVideo and videoCodecHwAcceleration
        PeerConnectionFactory.initializeAndroidGlobals(this, true, true, true)

        //Create a new PeerConnectionFactory instance.
        val options = PeerConnectionFactory.Options()
        peerConnectionFactory = PeerConnectionFactory(options)


        //Now create a VideoCapturer instance. Callback methods are there if you want to do something! Duh!
        val videoCapturerAndroid = getVideoCapturer(CustomCameraEventsHandler())

        //Create MediaConstraints - Will be useful for specifying video and audio constraints.
        audioConstraints = MediaConstraints()
        videoConstraints = MediaConstraints()

        //Create a VideoSource instance
        videoSource = peerConnectionFactory.createVideoSource(videoCapturerAndroid)
        localVideoTrack = peerConnectionFactory.createVideoTrack("100", videoSource)

        //create an AudioSource instance
        audioSource = peerConnectionFactory.createAudioSource(audioConstraints)
        localAudioTrack = peerConnectionFactory.createAudioTrack("101", audioSource)
        localVideoView.visibility = View.VISIBLE

        //create a videoRenderer based on SurfaceViewRenderer instance
        localRenderer = VideoRenderer(localVideoView)
        // And finally, with our VideoRenderer ready, we
        // can add our renderer to the VideoTrack.
        localVideoTrack.addRenderer(localRenderer)

    }


    private fun call() {
        start.isEnabled = false
        call.isEnabled = false
        hangup.isEnabled = true
        //we already have video and audio tracks. Now create peerconnections
        val iceServers = arrayListOf<PeerConnection.IceServer>()

        //create sdpConstraints
        sdpConstraints = MediaConstraints()
        sdpConstraints.mandatory.add(MediaConstraints.KeyValuePair("offerToReceiveAudio", "true"))
        sdpConstraints.mandatory.add(MediaConstraints.KeyValuePair("offerToReceiveVideo", "true"))

        //creating localPeer
        localPeer = peerConnectionFactory.createPeerConnection(
            iceServers,
            sdpConstraints,
            object : CustomPeerConnectionObserver("localPeerCreation") {
                override fun onIceCandidate(iceCandidate: IceCandidate) {
                    super.onIceCandidate(iceCandidate)
                    onIceCandidateReceived(localPeer, iceCandidate)
                }
            })

        //creating remotePeer
        remotePeer = peerConnectionFactory.createPeerConnection(
            iceServers,
            sdpConstraints,
            object : CustomPeerConnectionObserver("remotePeerCreation") {

                override fun onIceCandidate(iceCandidate: IceCandidate) {
                    super.onIceCandidate(iceCandidate)
                    onIceCandidateReceived(remotePeer, iceCandidate)
                }

                override fun onAddStream(mediaStream: MediaStream) {
                    super.onAddStream(mediaStream)
                    gotRemoteStream(mediaStream)
                }

            })

        //creating local mediastream
        val stream = peerConnectionFactory.createLocalMediaStream("102")
        stream.addTrack(localAudioTrack)
        stream.addTrack(localVideoTrack)
        localPeer!!.addStream(stream)

        //creating Offer
        localPeer!!.createOffer(object : CustomSdpObserver("localCreateOffer") {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                //we have localOffer. Set it as local desc for localpeer and remote desc for remote peer.
                //try to create answer from the remote peer.
                super.onCreateSuccess(sessionDescription)
                localPeer!!.setLocalDescription(CustomSdpObserver("localSetLocalDesc"), sessionDescription)
                remotePeer!!.setRemoteDescription(CustomSdpObserver("remoteSetRemoteDesc"), sessionDescription)
                remotePeer!!.createAnswer(object : CustomSdpObserver("remoteCreateOffer") {
                    override fun onCreateSuccess(sessionDescription: SessionDescription) {
                        //remote answer generated. Now set it as local desc for remote peer and remote desc for local peer.
                        super.onCreateSuccess(sessionDescription)
                        remotePeer!!.setLocalDescription(CustomSdpObserver("remoteSetLocalDesc"), sessionDescription)
                        localPeer!!.setRemoteDescription(CustomSdpObserver("localSetRemoteDesc"), sessionDescription)

                    }
                }, MediaConstraints())
            }
        }, sdpConstraints)
    }


    private fun hangup() {
        localPeer!!.close()
        remotePeer!!.close()
        localPeer = null
        remotePeer = null
        start.isEnabled = true
        call.isEnabled = false
        hangup.isEnabled = false
    }

    private fun gotRemoteStream(stream: MediaStream) {
        //we have remote video stream. add to the renderer.
        val videoTrack = stream.videoTracks.first
        val audioTrack = stream.audioTracks.first
        runOnUiThread {
            try {
                remoteRenderer = VideoRenderer(remoteVideoView)
                remoteVideoView.visibility = View.VISIBLE
                videoTrack.addRenderer(remoteRenderer)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

    }


    fun onIceCandidateReceived(peer: PeerConnection?, iceCandidate: IceCandidate) {
        //we have received ice candidate. We can set it to the other peer.
        if (peer === localPeer) {
            remotePeer!!.addIceCandidate(iceCandidate)
        } else {
            localPeer!!.addIceCandidate(iceCandidate)
        }
    }

    private fun createVideoCapturer(): VideoCapturer? {
        return createCameraCapturer(Camera1Enumerator(false))
    }

    private fun createCameraCapturer(enumerator: CameraEnumerator): VideoCapturer? {
        val deviceNames = enumerator.deviceNames

        // Trying to find a front facing camera!
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                val videoCapturer = enumerator.createCapturer(deviceName, null)

                if (videoCapturer != null) {
                    return videoCapturer
                }
            }
        }

        // We were not able to find a front cam. Look for other cameras
        for (deviceName in deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                val videoCapturer = enumerator.createCapturer(deviceName, null)
                if (videoCapturer != null) {
                    return videoCapturer
                }
            }
        }

        return null
    }


}