package com.mayday.webrtcz

import com.google.firebase.firestore.*
import okhttp3.ResponseBody
import org.json.JSONObject
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
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
    private var incidentId: String = ""
    private val db = FirebaseFirestore.getInstance()
    private lateinit var docRef: ListenerRegistration
    var isAnswerSet = false

    private var callback: SignalingInterface? = null

    private var phoneNode = hashMapOf<String,Any?>()
    private var localIceCandidates = ArrayList<HashMap<String,String>>()
    private var remoteIceCandidates = ArrayList<HashMap<String,Any>>()

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
            generateFakeIncident()
            // room created
            setupListeners()
            callback?.onRoomReady()
        }
    }

    private fun generateFakeIncident(){
        val retrofit = Retrofit.Builder()
            .addConverterFactory(GsonConverterFactory.create())
            .baseUrl("http://may-day.org/")
            .build()
        retrofit.callbackExecutor()
        val service = retrofit.create(EmergencyService::class.java)
        val call = service.fakeEmergency(callId)
        call.enqueue( object: Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (response.isSuccessful) {
                    incidentId = JSONObject(response.body()!!.string())["id"].toString()
                    Timber.i("Fake emergency created")
                } else {
                    val statusCode = response.code()
                    val errorBody = response.errorBody()
                    Timber.e("There was an error while creating fake emergency. Error :$errorBody.. Status Code: $statusCode")
                }
            }


            override fun onFailure(fail: Call<ResponseBody>, t: Throwable) {
                Timber.e("request to reset password failed $t")
            }
        })
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
                    callback?.onNewPeerJoined()
                    val dispatcher = data["dispatcher"] as HashMap<*, *>
                    if (dispatcher.containsKey("answer") && !isAnswerSet) {
                        isAnswerSet = true
                        callback?.onAnswerReceived(dispatcher["answer"] as HashMap<String, String>)
                    }

                    if (dispatcher.containsKey("iceCandidates")) {
                        val iceCandidates = dispatcher["iceCandidates"] as ArrayList<HashMap<String,Any>>
                        val newIceCandidates = iceCandidates.filter { !remoteIceCandidates.contains(it) }
                        for(ic in newIceCandidates){
                            callback?.onIceCandidateReceived(ic)
                        }
                        remoteIceCandidates = iceCandidates
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

        localIceCandidates.add(ic)

        phoneNode.putAll(hashMapOf("iceCandidates" to localIceCandidates))

        docData["phone"] = phoneNode
        Timber.d( "created iceCandidate $ic")
        db.collection(FIREBASE_COLLECTION_NAME).document(callId)
            .set(docData).addOnSuccessListener { documentReference ->
                Timber.d( "iceCandidate successfully created $documentReference")
            }
    }

    fun close() {
        db.collection("incidents").document(incidentId).delete()
        db.collection(FIREBASE_COLLECTION_NAME).document(callId).delete()
        callback = null
        instance = null
        docRef.remove()
        localIceCandidates = ArrayList()
        remoteIceCandidates = ArrayList()
    }

    interface EmergencyService{
        @FormUrlEncoded
        @POST("fake/emergency")
        fun fakeEmergency(@Field("streamId") streamId: String): Call<ResponseBody>
    }


    interface SignalingInterface {
        fun onRemoteHangUp()

        fun onAnswerReceived(data: HashMap<String, String>)

        fun onIceCandidateReceived(data: HashMap<String, Any>)

        fun onTryToStart()

        fun onNewPeerJoined()

        fun onRoomReady()
    }
}