package com.codewithkael.webrtcprojectforrecord

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView.VERTICAL
import com.codewithkael.webrtcprojectforrecord.adapter.RecyclerViewAdapter
import com.codewithkael.webrtcprojectforrecord.databinding.ActivityCallBinding
import com.codewithkael.webrtcprojectforrecord.models.IceCandidateModel
import com.codewithkael.webrtcprojectforrecord.models.MessageModel
import com.codewithkael.webrtcprojectforrecord.models.SessionDescriptionModel
import com.codewithkael.webrtcprojectforrecord.utils.NewMessageInterface
import com.codewithkael.webrtcprojectforrecord.utils.PeerConnectionObserver
import com.codewithkael.webrtcprojectforrecord.utils.RTCAudioManager
import com.google.gson.Gson
import org.webrtc.AudioTrack
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.RtpReceiver
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack

class CallActivity : AppCompatActivity(), NewMessageInterface {


    lateinit var binding: ActivityCallBinding
    private var userName: String? = null
    private var socketRepository: SocketRepository? = null
    private var rtcClient: RTCClient? = null
    private val TAG = "CallActivity"
    private var target: String = ""
    private val gson = Gson()
    private var isMute = false
    private var isCameraPause = false
    private val rtcAudioManager by lazy { RTCAudioManager.create(this) }
    private var isSpeakerMode = true
    private var gridViewAdapter: RecyclerViewAdapter? = null
    private var isLocalVideoInitialized = false
    private var isHost = true
    private var audioTracks: ArrayList<AudioTrack> = arrayListOf()
    private var videoTracks: ArrayList<VideoTrack> = arrayListOf()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)
        init()


    }

    private fun init() {
        userName = intent.getStringExtra("username")

        socketRepository = SocketRepository(this)
        userName?.let { socketRepository?.initSocket(it) }
        rtcClient = RTCClient(application, socketRepository!!, userName!!)
        rtcAudioManager.setDefaultAudioDevice(RTCAudioManager.AudioDevice.SPEAKER_PHONE)


        binding.apply {
            gridViewAdapter = RecyclerViewAdapter(this@CallActivity, arrayListOf())
            recyclerView.adapter = gridViewAdapter
            recyclerView.layoutManager = GridLayoutManager(this@CallActivity, 2, VERTICAL, false)


            callBtn.setOnClickListener {
                socketRepository?.sendMessageToSocket(
                    MessageModel(
                        "start_call", userName, targetUserNameEt.text.toString(), null
                    )
                )
                target = targetUserNameEt.text.toString()
            }

            switchCameraButton.setOnClickListener {
                rtcClient?.switchCamera()
            }

            micButton.setOnClickListener {
                if (isMute) {
                    isMute = false
                    micButton.setImageResource(R.drawable.ic_baseline_mic_off_24)
                } else {
                    isMute = true
                    micButton.setImageResource(R.drawable.ic_baseline_mic_24)
                }
                rtcClient?.toggleAudio(isMute)
            }

            videoButton.setOnClickListener {
                if (isCameraPause) {
                    isCameraPause = false
                    videoButton.setImageResource(R.drawable.ic_baseline_videocam_off_24)
                } else {
                    isCameraPause = true
                    videoButton.setImageResource(R.drawable.ic_baseline_videocam_24)
                }
                rtcClient?.toggleCamera(isCameraPause)
            }

            broadcastButton.setOnClickListener {
//                if (isSpeakerMode) {
//                    isSpeakerMode = false
//                    audioOutputButton.setImageResource(R.drawable.ic_baseline_hearing_24)
//                    rtcAudioManager.setDefaultAudioDevice(RTCAudioManager.AudioDevice.EARPIECE)
//                } else {
//                    isSpeakerMode = true
//                    audioOutputButton.setImageResource(R.drawable.ic_baseline_speaker_up_24)
//                    rtcAudioManager.setDefaultAudioDevice(RTCAudioManager.AudioDevice.SPEAKER_PHONE)
//
//                }
                rtcClient?.broadCast(audioTracks, videoTracks)


            }
            endCallButton.setOnClickListener {
                setCallLayoutGone()
                setWhoToCallLayoutVisible()
                setIncomingCallLayoutGone()
                rtcClient?.endCall()
            }
        }

    }

    override fun onNewMessage(message: MessageModel) {
        Log.d(TAG, "onNewMessage: $message")
        when (message.type) {
            "call_response" -> {
                if (message.data == "user is not online") {
                    //user is not reachable
                    runOnUiThread {
                        Toast.makeText(this, "user is not reachable", Toast.LENGTH_LONG).show()

                    }
                } else {
                    //we are ready for call, we started a call
                    runOnUiThread {
                        setWhoToCallLayoutGone()
                        setCallLayoutVisible()
                        binding.apply {
                            initPeerConnection(targetUserNameEt.text.toString())
                            if (!isLocalVideoInitialized) {
                                rtcClient?.initializeSurfaceView(localView)
                                rtcClient?.startLocalVideoAndAudio(localView)
                                rtcClient?.addStreamToPeerConnection(targetUserNameEt.text.toString())
                                isLocalVideoInitialized = true
                            }
                            rtcClient?.call(targetUserNameEt.text.toString())
                        }


                    }

                }
            }
            "answer_received" -> {
                val sessionModel = gson.fromJson(
                    gson.toJson(message.data),
                    SessionDescriptionModel::class.java
                )
                val session = SessionDescription(
                    SessionDescription.Type.valueOf(sessionModel.type),
                    sessionModel.sdp
                )
                rtcClient?.onRemoteSessionReceived(message.name!!, session)
                runOnUiThread {
                    binding.remoteViewLoading.visibility = View.GONE
                }
            }
            "offer_received" -> {
                val sessionModel = gson.fromJson(
                    gson.toJson(message.data),
                    SessionDescriptionModel::class.java
                )
                val session = SessionDescription(
                    SessionDescription.Type.valueOf(sessionModel.type),
                    sessionModel.sdp
                )

                runOnUiThread {
                    setIncomingCallLayoutGone()
                    setCallLayoutVisible()
                    setWhoToCallLayoutGone()

                    binding.apply {
                        initPeerConnection(message.name!!)
                        if (!isLocalVideoInitialized) {
                            rtcClient?.initializeSurfaceView(localView)
                            rtcClient?.startLocalVideoAndAudio(localView)
                            isLocalVideoInitialized = true
                        }
                    }
                    rtcClient?.broadcastStreams(
                        message.name!!,
                        gridViewAdapter!!.remoteStreams.map { it.second }.toList()
                    )
                    rtcClient?.onRemoteSessionReceived(
                        message.name!!,
                        session
                    )
                    rtcClient?.answer(message.name!!)
                    rtcClient?.printStatus(message.name)

                    target = message.name!!
                    binding.remoteViewLoading.visibility = View.GONE

                }

            }
            "renegotiate" -> {
                Log.d("Renegotiate", "Renegotiate initialized from $userName  with $target")
                rtcClient?.call(target)
            }

            "ice_candidate" -> {
                try {
                    initPeerConnection(message.name!!)
                    val receivingCandidate = gson.fromJson(
                        gson.toJson(message.data),
                        IceCandidateModel::class.java
                    )
                    rtcClient?.addIceCandidate(
                        IceCandidate(
                            receivingCandidate.sdpMid,
                            Math.toIntExact(receivingCandidate.sdpMLineIndex.toLong()),
                            receivingCandidate.sdpCandidate
                        ),
                        message.name!!
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun initPeerConnection(targetUserNameEt: String) {
        rtcClient?.initPeerConnection(targetUserNameEt,
            object : PeerConnectionObserver() {
                override fun onIceCandidate(p0: IceCandidate?) {
                    super.onIceCandidate(p0)

                    rtcClient?.addIceCandidate(p0, targetUserNameEt)
                    val candidate = hashMapOf(
                        "sdpMid" to p0?.sdpMid,
                        "sdpMLineIndex" to p0?.sdpMLineIndex,
                        "sdpCandidate" to p0?.sdp
                    )

                    socketRepository?.sendMessageToSocket(
                        MessageModel(
                            "ice_candidate",
                            userName,
                            targetUserNameEt,
                            candidate
                        )
                    )

                }

                override fun onAddStream(p0: MediaStream?) {
                    super.onAddStream(p0)
                    Log.d("OnAddStream", "Stream added")
                    runOnUiThread {
                        gridViewAdapter!!.addItem(targetUserNameEt, p0!!)
                    }
                }

                override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {
                    super.onAddTrack(p0, p1)
                    Log.d("OnAddTrack", "Stream length ${p1?.size} -- ${p0?.track()}")
                    val track = p0?.track()
                    if (track is VideoTrack) {

                        videoTracks.add(track)
                    } else {
                        (track as AudioTrack).apply {
                            audioTracks.add(this)
                            track.setVolume(10.toDouble())
                        }
                    }
//                    if (videoTracks?.size != null && videoTracks.size > 0) {
//                        runOnUiThread {
//                            gridViewAdapter!!.addItem(targetUserNameEt, p1[0]!!)
//                        }
//                    }

                }

                override fun onRenegotiationNeeded() {
//                    if (rtcClient!!.isConnectionStable(targetUserNameEt))
////                    {
//                    Log.d("Renegotiation", "Renegotiation from $userName with $targetUserNameEt")
//                    if (isHost) {
//                        socketRepository?.sendMessageToSocket(
//                            MessageModel(
//                                "renegotiation_required",
//                                target = targetUserNameEt
//                            )
//                        )
//                    } else {
//                        rtcClient?.call(targetUserNameEt)
//                    }
//                    }
                }
            })
    }

    private fun setIncomingCallLayoutGone() {
        binding.incomingCallLayout.visibility = View.GONE
    }

    private fun setIncomingCallLayoutVisible() {
        binding.incomingCallLayout.visibility = View.VISIBLE
    }

    private fun setCallLayoutGone() {
        binding.callLayout.visibility = View.GONE
    }

    private fun setCallLayoutVisible() {
        binding.callLayout.visibility = View.VISIBLE
    }

    private fun setWhoToCallLayoutGone() {
        binding.whoToCallLayout.visibility = View.GONE
    }

    private fun setWhoToCallLayoutVisible() {
        binding.whoToCallLayout.visibility = View.VISIBLE
    }
}