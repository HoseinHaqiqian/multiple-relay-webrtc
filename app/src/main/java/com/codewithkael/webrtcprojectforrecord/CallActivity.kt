package com.codewithkael.webrtcprojectforrecord

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.codewithkael.webrtcprojectforrecord.databinding.ActivityCallBinding
import org.webrtc.*

class CallActivity : AppCompatActivity() {

    lateinit var binding: ActivityCallBinding
    private var rtcClient: RTCClient? = null

    var remoteStream: MediaStream? = null
    var relayCount = 0

    companion object {
        val eglContext = EglBase.create()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)
        init()
    }

    fun gotStream(mediaStream: MediaStream) {
        remoteStream = mediaStream
        remoteStream?.videoTracks?.get(0)?.addSink(binding.remoteView)
    }

    private fun init() {
        (binding.remoteView).apply {
            setEnableHardwareScaler(true)
            setMirror(true)
            init(RTCClient.eglContext.eglBaseContext, null)
        }

        (binding.localView).apply {
            setEnableHardwareScaler(true)
            setMirror(true)
            init(RTCClient.eglContext.eglBaseContext, null)
        }

        rtcClient = RTCClient(application)

        binding.callButton.setOnClickListener {
            val stream = rtcClient?.startLocalVideoAndAudio(binding.localView)
            stream?.videoTracks?.get(0)?.addSink(binding.localView)
            rtcClient?.createPairPC(stream!!) { gotStream(it) }
        }

        binding.relayButton.setOnClickListener {
            rtcClient?.createPairPC(remoteStream!!) { gotStream(it) }
            relayCount++
            binding.relayCount.text = "relay count $relayCount"
        }
    }
}