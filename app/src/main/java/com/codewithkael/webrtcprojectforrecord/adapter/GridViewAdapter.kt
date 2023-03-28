package com.codewithkael.webrtcprojectforrecord.adapter

import android.content.Context
import android.os.Build
import android.provider.MediaStore.Audio.Media
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.RecyclerView
import com.codewithkael.webrtcprojectforrecord.R
import com.codewithkael.webrtcprojectforrecord.RTCClient
import org.webrtc.MediaStream
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack


class RecyclerViewAdapter(
    private var context: Context,
    var remoteStreams: ArrayList<Pair<String, MediaStream>>
) :
    RecyclerView.Adapter<RecyclerViewAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val surfaceView = view.findViewById<SurfaceViewRenderer>(R.id.remote_view)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.grid_remote_view, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        (holder.surfaceView).run {
            setEnableHardwareScaler(true)
            setMirror(true)
            init(RTCClient.eglContext.eglBaseContext, null)
            remoteStreams[position].second?.videoTracks?.get(0)?.addSink(this)
        }
    }


    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getItemCount(): Int {
        return remoteStreams.size
    }

    fun addItem(target: String, stream: MediaStream) {
        val hasId = remoteStreams.find { it.second.id == stream.id } != null

        Log.d("AddStreamLog", "StreamId was ${stream.id} -- Decision $hasId")
        if (!hasId) {
            remoteStreams.add(Pair(target, stream))
            notifyItemInserted(remoteStreams.size)
        } else {
            val index = remoteStreams.indexOfFirst { it.second.id == stream.id }
            remoteStreams[index].second?.dispose()
            remoteStreams[index] = Pair(target, stream)
            notifyItemChanged(index)
        }
    }

    fun updateView(p0: MediaStream) {

    }

}