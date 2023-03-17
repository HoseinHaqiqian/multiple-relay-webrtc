package com.codewithkael.webrtcprojectforrecord

import android.app.Application
import android.util.Log
import com.codewithkael.webrtcprojectforrecord.models.MessageModel
import org.webrtc.*
import org.webrtc.PeerConnection.Observer
import java.util.UUID

class RTCClient(
    private val application: Application,
    private val socketRepository: SocketRepository,
    private val localUsername: String
) {

    init {
        initPeerConnectionFactory(application)
    }

    companion object {
        val eglContext = EglBase.create()
    }

    private val peerConnectionFactory by lazy { createPeerConnectionFactory() }
    private val iceServer = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("turn:77.73.67.64:3478")
            .setUsername("turn-demo-server")
            .setPassword("coiansliocuna89s7ca")
            .createIceServer(),

        )
    private val peerConnections: ArrayList<Pair<String, PeerConnection>> = arrayListOf()

    //    private val peerConnection by lazy { createPeerConnection(observer) }
    private val localVideoSource by lazy { peerConnectionFactory.createVideoSource(false) }
    private val localAudioSource by lazy { peerConnectionFactory.createAudioSource(MediaConstraints()) }
    private var videoCapturer: CameraVideoCapturer? = null
    private var localAudioTrack: AudioTrack? = null
    private var localVideoTrack: VideoTrack? = null
    private var localStream: MediaStream? = null

    private fun initPeerConnectionFactory(application: Application) {
        val peerConnectionOption = PeerConnectionFactory.InitializationOptions.builder(application)
            .setEnableInternalTracer(true)
            .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
            .createInitializationOptions()

        PeerConnectionFactory.initialize(peerConnectionOption)
    }

    private fun createPeerConnectionFactory(): PeerConnectionFactory {
        return PeerConnectionFactory.builder()
            .setVideoEncoderFactory(
                DefaultVideoEncoderFactory(
                    eglContext.eglBaseContext,
                    true,
                    true
                )
            ).setVideoDecoderFactory(DefaultVideoDecoderFactory(eglContext.eglBaseContext))
            .setOptions(PeerConnectionFactory.Options().apply {
                disableEncryption = true
                disableNetworkMonitor = true
            }).createPeerConnectionFactory()
    }

    private fun createPeerConnection(observer: PeerConnection.Observer): PeerConnection? {
        return peerConnectionFactory.createPeerConnection(iceServer, observer)
    }

    fun initializeSurfaceView(surface: SurfaceViewRenderer) {
        surface.run {
            setEnableHardwareScaler(true)
            setMirror(true)
            init(eglContext.eglBaseContext, null)
        }
    }

    fun startLocalVideoAndAudio(surface: SurfaceViewRenderer) {
        val surfaceTextureHelper =
            SurfaceTextureHelper.create(Thread.currentThread().name, eglContext.eglBaseContext)
        videoCapturer = getVideoCapturer(application)
        videoCapturer?.initialize(
            surfaceTextureHelper,
            surface.context,
            localVideoSource.capturerObserver
        )
        videoCapturer?.startCapture(320, 240, 30)
        localVideoTrack =
            peerConnectionFactory.createVideoTrack("${localUsername}_video_track", localVideoSource)
        localVideoTrack?.addSink(surface)
        localAudioTrack =
            peerConnectionFactory.createAudioTrack("${localUsername}_audio_track", localAudioSource)
        localStream = peerConnectionFactory.createLocalMediaStream(UUID.randomUUID().toString())
        localStream!!.addTrack(localVideoTrack)
        localStream!!.addTrack(localAudioTrack)
    }

    fun addStreamToPeerConnection(target: String) {
        getPeerConnection(target)?.addStream(localStream)
    }

    fun broadcastStreams(target: String, streamList: List<MediaStream>) {
        val peerConnection = getPeerConnection(target)
        if(streamList.isEmpty())return
        val stream = streamList[0]

        peerConnection?.addTrack(stream.audioTracks[0], listOf(stream.id))
        peerConnection?.addTrack(stream.videoTracks[0], listOf(stream.id))
    }

    fun initPeerConnection(username: String, observer: Observer) {
        val foundPeerC = peerConnections.find { it.first == username }
        Log.d("InitPeerConnection", "Peer Connection already init = ${foundPeerC != null}")
        if (foundPeerC != null) return
        val peerConnection = createPeerConnection(observer)!!

        peerConnections.add(Pair(username, peerConnection))
    }

    private fun getVideoCapturer(application: Application): CameraVideoCapturer {
        return Camera2Enumerator(application).run {
            deviceNames.find {
                isFrontFacing(it)
            }?.let {
                createCapturer(it, null)
            } ?: throw IllegalStateException()
        }
    }

    fun call(target: String) {
        val mediaConstraints = MediaConstraints()
        mediaConstraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        mediaConstraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        val peerConnection = getPeerConnection(target)
        Log.d("CreateOffer", "Create offer $peerConnection")
        peerConnection!!.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                peerConnection.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {

                    }

                    override fun onSetSuccess() {
                        val offer = hashMapOf(
                            "sdp" to desc?.description,
                            "type" to desc?.type
                        )

                        socketRepository.sendMessageToSocket(
                            MessageModel(
                                "create_offer", localUsername, target, offer
                            )
                        )
                    }

                    override fun onCreateFailure(p0: String?) {
                        Log.d("LocalDescriptionFailure", p0.toString())
                    }

                    override fun onSetFailure(p0: String?) {
                        Log.d("LocalDescriptionFailure", p0.toString())
                    }
                }, desc)

            }

            override fun onSetSuccess() {
            }

            override fun onCreateFailure(p0: String?) {
                Log.d("OfferCreateFailure", p0.toString())
            }

            override fun onSetFailure(p0: String?) {
                Log.d("LocalDescriptionFailure", p0.toString())
            }
        }, mediaConstraints)
    }

    fun onRemoteSessionReceived(target: String, session: SessionDescription) {
        val peerConnection = getPeerConnection(target)
        if (peerConnection?.signalingState() == PeerConnection.SignalingState.STABLE && peerConnection.remoteDescription?.type == SessionDescription.Type.ANSWER) return
        getPeerConnection(target)!!.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {

            }

            override fun onSetSuccess() {
                Log.d("RemoteDescription", "Remote desc is set successfully")
            }

            override fun onCreateFailure(p0: String?) {
                Log.d("RemoteDescriptionFail", p0.toString())
            }

            override fun onSetFailure(p0: String?) {
                Log.d("RemoteDescriptionFail", p0.toString())
            }

        }, session)

    }

    fun answer(target: String) {
        val constraints = MediaConstraints()
        constraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        constraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))

        val peerConnection = getPeerConnection(target)
        peerConnection!!.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                peerConnection.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {
                    }


                    override fun onSetSuccess() {
                        val answer = hashMapOf(
                            "sdp" to desc?.description,
                            "type" to desc?.type
                        )
                        socketRepository.sendMessageToSocket(
                            MessageModel(
                                "create_answer", localUsername, target, answer
                            )
                        )
                    }

                    override fun onCreateFailure(p0: String?) {
                        Log.e("LocalDescriptionFailure", p0.toString())
                    }

                    override fun onSetFailure(p0: String?) {
                        Log.e("LocalDescriptionFailure", p0.toString())
                    }

                }, desc)
            }

            override fun onSetSuccess() {
            }

            override fun onCreateFailure(p0: String?) {
                Log.e("CreateAnswerFailure", "Error ${p0}")
            }

            override fun onSetFailure(p0: String?) {
                Log.e("CreateAnswerFailure", "Error ${p0}")
            }

        }, constraints)
    }

    fun addIceCandidate(p0: IceCandidate?, target: String) {

        getPeerConnection(target)!!.addIceCandidate(p0)
    }

    fun switchCamera() {
        videoCapturer?.switchCamera(null)
    }

    fun toggleAudio(mute: Boolean) {
        localAudioTrack?.setEnabled(mute)
    }

    fun toggleCamera(cameraPause: Boolean) {
        localVideoTrack?.setEnabled(cameraPause)
    }

    fun endCall() {
        peerConnections.forEach {
            it.second.close()
        }
        peerConnections.clear()
    }

    private fun getPeerConnection(username: String): PeerConnection? {
        return peerConnections.find { it.first == username }?.second;
    }

    fun broadCast(audioTracks: ArrayList<AudioTrack>, videoTracks: ArrayList<VideoTrack>) {

        // peerConnections is list of Pair classes
        // adding audio/video tracks of first user in stream to second user
        val peerConnectionWithSecondUser = peerConnections[1].second
        val stream = peerConnectionFactory.createLocalMediaStream("another_thread")
        stream.addTrack(audioTracks[0])
        stream.addTrack(videoTracks[0])

        peerConnectionWithSecondUser.addTrack(stream.audioTracks[0], listOf(stream.id))
        peerConnectionWithSecondUser.addTrack(stream.videoTracks[0], listOf(stream.id))

    }

    fun isConnectionStable(targetUserNameEt: String?): Boolean {
        if (targetUserNameEt == null) {
            peerConnections.forEach {
                val signalingState = it.second
                Log.d("SignalingState", "Signaling state 1 ${signalingState?.signalingState()}")
                Log.d("SignalingState", "Signaling state 1 ${signalingState?.connectionState()}")
                Log.d("SignalingState", "Signaling state 1 ${signalingState?.iceConnectionState()}")
                Log.d("SignalingState", "Signaling state 1 ${signalingState?.iceGatheringState()}")
                Log.d(
                    "SignalingState",
                    "Signaling state 1 ${signalingState?.localDescription?.type}"
                )
                Log.d(
                    "SignalingState",
                    "Signaling state ${signalingState?.remoteDescription?.type}"
                )

            }
            return false
        } else {
            val signalingState = peerConnections.find { it.first == targetUserNameEt }?.second
            Log.d("SignalingState", "Signaling state ${signalingState?.signalingState()}")
            Log.d("SignalingState", "Signaling state ${signalingState?.connectionState()}")
            Log.d("SignalingState", "Signaling state ${signalingState?.iceConnectionState()}")
            Log.d("SignalingState", "Signaling state ${signalingState?.iceGatheringState()}")
            Log.d("SignalingState", "Signaling state ${signalingState?.localDescription?.type}")
            Log.d("SignalingState", "Signaling state ${signalingState?.remoteDescription?.type}")
            return signalingState?.connectionState() == PeerConnection.PeerConnectionState.CONNECTED
        }

    }

    fun printStatus(name: String?) {
        val signalingState = peerConnections.find { it.first == name }?.second
        Log.d("SignalingState", "Signaling state ${signalingState?.signalingState()}")
        Log.d("SignalingState", "Signaling state ${signalingState?.connectionState()}")
        Log.d("SignalingState", "Signaling state ${signalingState?.iceConnectionState()}")
        Log.d("SignalingState", "Signaling state ${signalingState?.iceGatheringState()}")
        Log.d("SignalingState", "Signaling state ${signalingState?.localDescription?.type}")
        Log.d("SignalingState", "Signaling state ${signalingState?.remoteDescription?.type}")
    }


}