package com.mayday.webrtcz

import com.google.firebase.firestore.*
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import timber.log.Timber

class SignallingClient {

    companion object {
        private const val FIREBASE_COLLECTION_NAME = "dev-stream"
        private const val TAG = "Signaling Client"
        private var instance: SignallingClient? = null
        fun getInstance(): SignallingClient {
            if (instance == null) {
                instance = SignallingClient()
            }
            return instance!!
        }
    }

    private var callId: String = ""
    private val db = FirebaseFirestore.getInstance()
    private lateinit var docRef: ListenerRegistration
    private var isChannelReady = false
    private var isInitiator = false
    var isStarted = false

    private var callback: SignalingInterface? = null

    private var phoneNode = hashMapOf<String,Any?>()
    private var iceCandidates = ArrayList<HashMap<String,String>>()

    fun init(signalingInterface: SignalingInterface) {
        Timber.tag(TAG)
        this.callback = signalingInterface

        Timber.d( "init() called")

        if (callId.isEmpty()) {
            emitInitStatement()
        }
    }

    private fun emitInitStatement() {
        Timber.d( "emitInitStatement() called with: event = create")
        db.collection(FIREBASE_COLLECTION_NAME).add(hashMapOf()).addOnSuccessListener { documentReference ->
            callId = documentReference.id
            // room created
            isInitiator = true
            setupListeners()
            callback?.onRoomReady()
        }
    }

    private fun setupListeners() {
        val query = db.collection(FIREBASE_COLLECTION_NAME).document(callId)
        docRef = query.addSnapshotListener(EventListener<DocumentSnapshot> { snapshot, e ->
            if (e != null) {
                Timber.w( "Listen failed. $e")
                return@EventListener
            }

            if (snapshot != null && snapshot.exists() && snapshot.data != null) {
                val data = snapshot.data!!
                Timber.d( "Current data:  ${snapshot.data}")
                //peer joined event
                if(data.containsKey("phone")){
                    phoneNode = data["phone"] as HashMap<String, Any?>
                }
                if (data.containsKey("dispatcher")) {
                    isChannelReady = true
                    callback?.onNewPeerJoined()
                    val dispatcher = data["dispatcher"] as HashMap<*, *>
                    if (dispatcher.containsKey("answer") && isStarted) {
                        callback?.onAnswerReceived(dispatcher["answer"] as HashMap<String, String>)
                    }

                    if (dispatcher.containsKey("iceCandidates") && isStarted) {
                        callback?.onIceCandidateReceived(dispatcher["iceCandidates"] as HashMap<String, String>)
                    }
                }

            } else {
                Timber.d( "Current data: null")
                callback?.onRemoteHangUp()
            }
        })
    }

    fun emitMessage(message: SessionDescription) {
        Timber.d( "emitMessage() called with: message = [$message]")
        val docData = hashMapOf<String, Any?>()

        val offer = hashMapOf<String, String>()
        offer["type"] = message.type.canonicalForm()
        offer["sdp"] = message.description
        phoneNode.putAll(hashMapOf("offer" to offer))

        docData["phone"] = phoneNode
        Timber.d( "created offer $offer")
        db.collection(FIREBASE_COLLECTION_NAME).document(callId)
            .set(docData).addOnSuccessListener { documentReference ->
                Timber.d( "offer successfully created $documentReference")
            }
    }


    fun emitIceCandidate(iceCandidate: IceCandidate) {
        Timber.d( "emitIceCandidate() called with: iceCandidate = [$iceCandidate]")
        val docData = hashMapOf<String, Any?>()
        val ic = hashMapOf<String, String>()
        ic["label"] = iceCandidate.sdpMLineIndex.toString()
        ic["id"] = iceCandidate.sdpMid
        ic["candidate"] = iceCandidate.sdp

        iceCandidates.add(ic)

        phoneNode.putAll(hashMapOf("iceCandidates" to iceCandidates))

        docData["phone"] = phoneNode
        Timber.d( "created iceCandidate $ic")
        db.collection(FIREBASE_COLLECTION_NAME).document(callId)
            .set(docData).addOnSuccessListener { documentReference ->
                Timber.d( "iceCandidate successfully created $documentReference")
            }
    }

    fun close() {
        callback = null
        instance = null
        isStarted = false
        docRef.remove()
    }


    interface SignalingInterface {
        fun onRemoteHangUp()

        fun onAnswerReceived(data: HashMap<String, String>)

        fun onIceCandidateReceived(data: HashMap<String, String>)

        fun onTryToStart()

        fun onNewPeerJoined()

        fun onRoomReady()
    }
}