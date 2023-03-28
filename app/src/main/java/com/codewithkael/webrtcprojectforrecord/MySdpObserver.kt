package com.codewithkael.webrtcprojectforrecord

import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

abstract class MySdpObserver : SdpObserver {

    override fun onCreateSuccess(sdp: SessionDescription?) {

    }

    override fun onSetSuccess() {
    }

    override fun onCreateFailure(error: String?) {
    }

    override fun onSetFailure(error: String?) {
    }

}