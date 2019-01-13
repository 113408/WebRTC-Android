package com.mayday.webrtcz

import android.os.Bundle
import android.support.annotation.UiThread
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.Button
import android.widget.Toast
import org.json.JSONException
import org.webrtc.*
import timber.log.Timber

class MainActivity : AppCompatActivity(), View.OnClickListener, SignallingClient.SignalingInterface {

    private lateinit var peerConnectionFactory : PeerConnectionFactory
    private lateinit var audioConstraints : MediaConstraints
    private lateinit var videoConstraints : MediaConstraints
    private lateinit var sdpConstraints : MediaConstraints
    private lateinit var videoSource : VideoSource
    private lateinit var localVideoTrack : VideoTrack
    private lateinit var audioSource : AudioSource
    private lateinit var localAudioTrack : AudioTrack

    private lateinit var localVideoView : SurfaceViewRenderer

    private lateinit var hangup : Button
    private lateinit var call : Button
    private lateinit var start : Button

    private var localPeer : PeerConnection? = null
    private var peerIceServers = arrayListOf(PeerConnection.IceServer("stun:stun.l.google.com:19302"))
    private lateinit var rootEglBase : EglBase

    private var gotUserMedia : Boolean = false

    private lateinit var localRenderer: VideoRenderer


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        Timber.tag(TAG)
        initViews()
        initVideos()
        initSdpConstraints()
        start()
    }

    private fun initSdpConstraints() {
        sdpConstraints = MediaConstraints()
        sdpConstraints.mandatory.add(
            MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
        sdpConstraints.mandatory.add(MediaConstraints.KeyValuePair(
            "OfferToReceiveVideo", "false"))
    }


    private fun initViews() {
        hangup = findViewById(R.id.end_call)
        call = findViewById(R.id.init_call)
        start = findViewById(R.id.start_call)
        localVideoView = findViewById(R.id.local_gl_surface_view)
        hangup.setOnClickListener(this)
        call.setOnClickListener(this)
        start.setOnClickListener(this)
    }

    private fun initVideos() {
        rootEglBase = EglBase.create()
        localVideoView.init(rootEglBase.eglBaseContext, null)
        localVideoView.setZOrderMediaOverlay(true)
    }


    fun start() {
        //Initialize PeerConnectionFactory globals.
        PeerConnectionFactory.initializeAndroidGlobals(this,true)
        //Create a  PeerConnectionFactory instance - using Hardware encoder and decoder.
        val options = PeerConnectionFactory.Options()
        val defaultVideoEncoderFactory = HardwareVideoEncoderFactory(
            rootEglBase.eglBaseContext, /* enableIntelVp8Encoder */true, /* enableH264HighProfile */true
        )
        val defaultVideoDecoderFactory = HardwareVideoDecoderFactory(rootEglBase.eglBaseContext)
        peerConnectionFactory = PeerConnectionFactory(options, defaultVideoEncoderFactory, defaultVideoDecoderFactory)

        //Now create a VideoCapturer instance.
        val videoCapturerAndroid = createCameraCapturer(Camera1Enumerator(false))


        //Create MediaConstraints - Will be useful for specifying video and audio constraints.
        audioConstraints = MediaConstraints()
        videoConstraints = MediaConstraints()

        //Create a VideoSource instance
        if (videoCapturerAndroid != null) {
            videoSource = peerConnectionFactory.createVideoSource(videoCapturerAndroid)
        }
        localVideoTrack = peerConnectionFactory.createVideoTrack("100", videoSource)

        //create an AudioSource instance
        audioSource = peerConnectionFactory.createAudioSource(audioConstraints)
        localAudioTrack = peerConnectionFactory.createAudioTrack("101", audioSource)


        videoCapturerAndroid?.startCapture(1024, 720, 30)

        localVideoView.visibility = View.VISIBLE
        // And finally, with our VideoRenderer ready, we
        // can add our renderer to the VideoTrack.
        localRenderer = VideoRenderer(localVideoView)
        localVideoTrack.addRenderer(localRenderer)

        localVideoView.setMirror(true)

        gotUserMedia = true
    }


    /**
     * This method will be called directly by the app when it is the initiator and has got the local media
     * or when the remote peer sends a message through socket that it is ready to transmit AV data
     */
    @UiThread
    override  fun onTryToStart() {
        call.isEnabled = false
        hangup.isEnabled = true
        createPeerConnection()
        SignallingClient.getInstance().isStarted = true
        doCall()
    }


    /**
     * Creating the local peerconnection instance
     */
    private fun createPeerConnection() {
        localPeer = peerConnectionFactory.createPeerConnection(
            peerIceServers,
            sdpConstraints,
            object : CustomPeerConnectionObserver() {
                override fun onIceCandidate(iceCandidate: IceCandidate) {
                    super.onIceCandidate(iceCandidate)
                    onIceCandidateReceived(iceCandidate)
                }
            })
        addStreamToLocalPeer()
    }

    /**
     * Adding the stream to the localpeer
     */
    private fun addStreamToLocalPeer() {
        //creating local mediastream
        val stream = peerConnectionFactory.createLocalMediaStream("102")
        stream.addTrack(localAudioTrack)
        stream.addTrack(localVideoTrack)
        localPeer?.addStream(stream)
    }

    /**
     * This method is called when the app is initiator - We generate the offer and send it over through socket
     * to remote peer
     */
    private fun doCall() {
        localPeer?.createOffer(object : CustomSdpObserver("localCreateOffer") {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                super.onCreateSuccess(sessionDescription)
                localPeer?.setLocalDescription( CustomSdpObserver("localSetLocalDesc"), sessionDescription)
                Timber.d("onCreateSuccess SignallingClient emit ")
                SignallingClient.getInstance().emitMessage(sessionDescription)
            }
        }, sdpConstraints)
    }


    /**
     * Received local ice candidate. Send it to remote peer through signalling for negotiation
     */
    fun onIceCandidateReceived(iceCandidate: IceCandidate) {
        //we have received ice candidate. We can set it to the other peer.
        SignallingClient.getInstance().emitIceCandidate(iceCandidate)
    }

    override fun onNewPeerJoined() {
        showToast("Remote Peer Joined")
    }

    override fun onRemoteHangUp() {
        showToast("Remote Peer hungup")
        runOnUiThread(this::hangup)
    }

    /**
     * SignallingCallback - Called when remote peer sends answer to your offer
     */
    override fun onAnswerReceived(data: HashMap<String,String>) {
        showToast("Received Answer")
        try {
            localPeer?.setRemoteDescription( CustomSdpObserver("localSetRemote"),  SessionDescription(SessionDescription.Type.fromCanonicalForm(data["type"]!!.toLowerCase()), data["sdp"]))
        } catch (e:JSONException) {
            e.printStackTrace()
        }
    }

    /**
     * Remote IceCandidate received
     */
    override fun onIceCandidateReceived(data: HashMap<String,String>) {
        try {
            localPeer?.addIceCandidate( IceCandidate(data["id"], data["label"]!!.toInt(), data["candidate"]))
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    override fun onRoomReady() {
        call.isEnabled = true
        hangup.isEnabled = false
        start.isEnabled = false
    }


    /**
     * Closing up
     */
    override fun onClick(v: View) {
        when(v.id) {
            R.id.end_call -> {
                hangup()
            }
            R.id.init_call -> {
                onTryToStart()
            }
            R.id.start_call -> {
                setup()
            }
        }
    }

    private fun hangup() {
        try {
            localPeer?.close()
            localPeer = null
            SignallingClient.getInstance().close()
            start.isEnabled = true
            call.isEnabled = false
            hangup.isEnabled = false
        } catch (e:Exception) {
            e.printStackTrace()
        }
    }

    private fun setup() {
        SignallingClient.getInstance().init(this)
        start.isEnabled = false
        call.isEnabled = false
        hangup.isEnabled = false
    }

    @UiThread
    fun showToast(msg:String) {
        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
    }

    private fun createCameraCapturer(enumerator: CameraEnumerator) : VideoCapturer? {
        val deviceNames = enumerator.deviceNames

        // First, try to find front facing camera
        Logging.d(TAG, "Looking for front facing cameras.")
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating front facing camera capturer.")
                val videoCapturer = enumerator.createCapturer(deviceName, null)

                if (videoCapturer != null) {
                    return videoCapturer
                }
            }
        }

        // Front facing camera not found, try something else
        Logging.d(TAG, "Looking for other cameras.")
        for (deviceName in deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating other camera capturer.")
                val videoCapturer = enumerator.createCapturer(deviceName, null)

                if (videoCapturer != null) {
                    return videoCapturer
                }
            }
        }

        return null
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}