package com.mayday.webrtcz

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.Button
import com.google.gson.Gson
import okhttp3.ResponseBody
import org.webrtc.*
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.VideoRenderer
import org.webrtc.VideoTrack
import org.webrtc.MediaStream
import org.webrtc.MediaConstraints
import org.webrtc.SessionDescription
import org.webrtc.VideoCapturer
import org.webrtc.PeerConnectionFactory
import org.webrtc.EglBase
import org.webrtc.SurfaceViewRenderer
import org.webrtc.CameraEnumerator
import org.webrtc.Camera1Enumerator
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*


class MainActivity : AppCompatActivity(), View.OnClickListener {
    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private lateinit var audioConstraints: MediaConstraints
    private lateinit var sdpConstraints: MediaConstraints
    private lateinit var videoSource: VideoSource
    private lateinit var localVideoTrack: VideoTrack
    private lateinit var audioSource: AudioSource
    private lateinit var localAudioTrack: AudioTrack

    private lateinit var localVideoView: SurfaceViewRenderer
    private lateinit var remoteVideoView: SurfaceViewRenderer
    private lateinit var localRenderer: VideoRenderer
    private lateinit var remoteRenderer: VideoRenderer

    var localPeer: PeerConnection? = null
    var remotePeer: PeerConnection? = null
    lateinit var start: Button
    private lateinit var call: Button
    private lateinit var hangup: Button
    private lateinit var rootEglBase: EglBase

    private var videoCapturerAndroid: VideoCapturer? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        initVideos()
        requestPermission()
    }

    private fun requestPermission(){
        if(ContextCompat.checkSelfPermission(
                this@MainActivity,
                Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this@MainActivity,
                Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ){
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO),30)
        }
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
        rootEglBase = EglBase.create()
        localVideoView.init(rootEglBase.eglBaseContext, null)
        remoteVideoView.init(rootEglBase.eglBaseContext, null)
        localVideoView.setZOrderMediaOverlay(true)
        remoteVideoView.setZOrderMediaOverlay(true)
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
        //Initialize PeerConnectionFactory globals.

        PeerConnectionFactory.initializeAndroidGlobals(this,true)
        //Create a new PeerConnectionFactory instance - using Hardware encoder and decoder.
        val options = PeerConnectionFactory.Options()
        val defaultVideoEncoderFactory = HardwareVideoEncoderFactory(rootEglBase.eglBaseContext, /* enableIntelVp8Encoder */true, /* enableH264HighProfile */true
        )
        val defaultVideoDecoderFactory = HardwareVideoDecoderFactory(rootEglBase.eglBaseContext)
        peerConnectionFactory = PeerConnectionFactory(options, defaultVideoEncoderFactory, defaultVideoDecoderFactory)



        //Now create a VideoCapturer instance. Callback methods are there if you want to do something! Duh!
        videoCapturerAndroid = createVideoCapturer()

        //Create MediaConstraints - Will be useful for specifying video and audio constraints.
        audioConstraints = MediaConstraints()

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

        //we will start capturing the video from the camera
        //width,height and fps
        videoCapturerAndroid?.startCapture(1000, 1000, 30)
        //we already have video and audio tracks. Now create peerconnections
        val iceServers = arrayListOf(PeerConnection.IceServer("stun:stun.l.google.com:19302"))

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
                localPeer
                sendOffer()
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

    private fun sendOffer() {
        val retrofit = Retrofit.Builder()
            .addConverterFactory(GsonConverterFactory.create())
            .baseUrl("https://us-central1-mayday-ios.cloudfunctions.net/")
            .build()
        retrofit.callbackExecutor()
        val service = retrofit.create(WebRTCService::class.java)
        val call = service.createOffer(localPeer?.localDescription!!)
        call.enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (response.isSuccessful) {
                    Log.i("WebRTC - Firebase", "offer created successfully")

                } else {
                    val statusCode = response.code()
                    val errorBody = response.errorBody()
                    Log.e(
                        "WebRTC - Firebase",
                        "There was an error while creating the offer on firebase. Error :${errorBody?.string()} . Status Code: $statusCode"
                    )
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                Log.e("WebRTC - Firebase", "error while connecting to firebase cloud function", t)
            }

        })
    }


    private fun hangup() {
        localPeer!!.close()
        remotePeer!!.close()
        localPeer = null
        remotePeer = null
        videoCapturerAndroid?.stopCapture()
        localVideoView.clearImage()
        remoteVideoView.clearImage()
        start.isEnabled = true
        call.isEnabled = false
        hangup.isEnabled = false
    }

    private fun gotRemoteStream(stream: MediaStream) {
        //we have remote video stream. add to the renderer.
        val videoTrack = stream.videoTracks.first
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
                val videoCapturer = enumerator.createCapturer(deviceName, CustomCameraEventsHandler())
                if (videoCapturer != null) {
                    return videoCapturer
                }
            }
        }

        return null
    }


    interface WebRTCService {
        @POST("getice")
        fun createOffer(@Body body: SessionDescription) : Call<ResponseBody>
    }
}