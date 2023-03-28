package com.codewithkael.webrtcprojectforrecord

import android.app.Application
import com.codewithkael.webrtcprojectforrecord.utils.PeerConnectionObserver
import org.webrtc.*
import java.util.UUID

class RTCClient(
    private val application: Application,
) {

    init {
        initPeerConnectionFactory(application)
    }

    companion object {
        val eglContext = EglBase.create()
    }

    val peerConnectionFactory by lazy { createPeerConnectionFactory() }
    private val iceServer = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("turn:77.73.67.64:3478")
            .setUsername("turn-demo-server")
            .setPassword("coiansliocuna89s7ca")
            .createIceServer(),

        )
    private val peerConnections: HashMap<String, PeerConnection> = hashMapOf()

    //    private val peerConnection by lazy { createPeerConnection(observer) }
    private val localVideoSource by lazy { peerConnectionFactory.createVideoSource(false) }
    private val localAudioSource by lazy { peerConnectionFactory.createAudioSource(MediaConstraints()) }
    private var videoCapturer: CameraVideoCapturer? = null
    private var localAudioTrack: AudioTrack? = null
    private var localVideoTrack: VideoTrack? = null
    var localStream: MediaStream? = null

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

    fun startLocalVideoAndAudio(surface: SurfaceViewRenderer): MediaStream {
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
            peerConnectionFactory.createVideoTrack(
                "${UUID.randomUUID()}_video_track",
                localVideoSource
            )
        localVideoTrack?.addSink(surface)
        localAudioTrack =
            peerConnectionFactory.createAudioTrack(
                "${UUID.randomUUID()}_audio_track",
                localAudioSource
            )
        localStream = peerConnectionFactory.createLocalMediaStream(UUID.randomUUID().toString())
        localStream!!.addTrack(localVideoTrack)
        localStream!!.addTrack(localAudioTrack)
        return localStream!!
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

    private fun getPeerConnection(username: String): PeerConnection? {
        return peerConnections[username]
    }

    fun createPairPC(stream: MediaStream, handler: (MediaStream) -> Unit) {
        val peer1Name = "${UUID.randomUUID()}"
        val peer2Name = "${UUID.randomUUID()}"
        val pc1 = createPeerConnection(object : PeerConnectionObserver() {
            override fun onIceCandidate(p0: IceCandidate?) {
                peerConnections[peer2Name]?.addIceCandidate(p0)
            }

        })
        peerConnections[peer1Name] = pc1!!

        val pc2 = createPeerConnection(object : PeerConnectionObserver() {
            override fun onIceCandidate(p0: IceCandidate?) {
                peerConnections[peer1Name]?.addIceCandidate(p0)
            }

            override fun onAddStream(p0: MediaStream?) {
                handler.invoke(p0!!)
            }
        })
        peerConnections[peer2Name] = pc2!!

        pc1.addTrack(stream.audioTracks[0], arrayListOf(stream.id))
        pc1.addTrack(stream.videoTracks[0], arrayListOf(stream.id))


        pc1.createOffer(object : MySdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                pc1.setLocalDescription(object : MySdpObserver() {}, sdp)
                peerConnections[peer2Name]!!.also { pc2 ->
                    pc2.setRemoteDescription(object : MySdpObserver() {}, sdp)
                    pc2.createAnswer(object : MySdpObserver() {
                        override fun onCreateSuccess(sdp: SessionDescription?) {
                            pc2.setLocalDescription(object : MySdpObserver() {}, sdp)
                            pc1.setRemoteDescription(object : MySdpObserver() {}, sdp)
                        }
                    }, MediaConstraints())
                }


            }

        }, MediaConstraints())


    }

}