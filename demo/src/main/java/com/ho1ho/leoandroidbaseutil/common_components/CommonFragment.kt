package com.ho1ho.leoandroidbaseutil.common_components

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import com.ho1ho.androidbase.exts.ITAG
import com.ho1ho.androidbase.utils.LLog
import com.ho1ho.leoandroidbaseutil.R
import com.ho1ho.leoandroidbaseutil.ui.*
import com.ho1ho.leoandroidbaseutil.ui.camera2.Camera2LiveActivity
import com.ho1ho.leoandroidbaseutil.ui.media_player.PlayRawH265ByMediaCodecActivity
import com.ho1ho.leoandroidbaseutil.ui.media_player.PlayVideoByMediaCodecActivity
import com.ho1ho.leoandroidbaseutil.ui.sharescreen.client.ScreenShareClientActivity
import com.ho1ho.leoandroidbaseutil.ui.sharescreen.master.ScreenShareMasterActivity
import com.ho1ho.leoandroidbaseutil.ui.socket.SocketActivity
import com.ho1ho.leoandroidbaseutil.ui.socket.WebSocketClientActivity
import com.ho1ho.leoandroidbaseutil.ui.socket.WebSocketServerActivity

class CommonFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_common, container, false)
        root.findViewById<GridView>(R.id.gridView).adapter = ColorBaseAdapter(this)
        return root
    }

    override fun onDestroy() {
//        CustomApplication.instance.closeDebugOutputFile()
        LLog.i(ITAG, "onDestroy()")
        super.onDestroy()
        // In some cases, if you use saved some parameters in Application, when app exits,
        // the parameters may not be released. So we need to call AppUtil.exitApp(ctx)
//        AppUtil.exitApp(this)
    }

    class ColorBaseAdapter(private val ctx: Fragment) : BaseAdapter() {
        internal class ViewHolder {
            lateinit var textView: TextView
            lateinit var cardView: CardView
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val viewHolder: ViewHolder
            val noneConvertView: View

            if (convertView == null) {
                noneConvertView = LayoutInflater.from(parent?.context).inflate(R.layout.grid_item, parent, false)
                viewHolder = ViewHolder()
                viewHolder.cardView = noneConvertView.findViewById(R.id.cardView)
                viewHolder.textView = noneConvertView.findViewById(R.id.name)
                noneConvertView.tag = viewHolder
            } else {
                noneConvertView = convertView
                viewHolder = noneConvertView.tag as ViewHolder
            }
            viewHolder.textView.text = featureList[position].first
            viewHolder.cardView.setCardBackgroundColor(color[color.indices.random()])
            viewHolder.cardView.setOnClickListener {
                val intent = Intent(ctx.requireActivity(), featureList[position].second)
                intent.putExtra("title", featureList[position].first)
                ctx.startActivity(intent)
            }
            return noneConvertView
        }

        override fun getItem(position: Int): Any {
            return featureList[position]
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getCount(): Int {
            return featureList.size
        }
    }

    companion object {
        private val featureList = arrayOf(
            Pair("ScreenShare\nMaster side", ScreenShareMasterActivity::class.java),
            Pair("ScreenShare\nClient side", ScreenShareClientActivity::class.java),
            Pair("WebSocket Server", WebSocketServerActivity::class.java),
            Pair("WebSocket Client", WebSocketClientActivity::class.java),
            Pair("Socket Client", SocketActivity::class.java),
            Pair("Device Info", DeviceInfoActivity::class.java),
            Pair("Play Video File by MediaCodec", PlayVideoByMediaCodecActivity::class.java),
            Pair("Play Raw H265 by MediaCodec", PlayRawH265ByMediaCodecActivity::class.java),
            Pair("TakeScreenshot", TakeScreenshotActivity::class.java),
            Pair("Record Single App Screen", RecordSingleAppScreenActivity::class.java),
            Pair("Network Monitor", NetworkMonitorActivity::class.java),
            Pair("Camera2Live", Camera2LiveActivity::class.java),
            Pair("Audio", AudioActivity::class.java),
            Pair("Coroutine", CoroutineActivity::class.java),
            Pair("HTTP Related", HttpActivity::class.java),
            Pair("Log", LogActivity::class.java),
            Pair("Clipboard", ClipboardActivity::class.java),
            Pair("SaveInstanceState", SaveInstanceStateActivity::class.java),
            Pair("KeepAlive", KeepAliveActivity::class.java)
        )

        private val color = arrayOf(
            Color.parseColor("#80CBC4"),
            Color.parseColor("#80DEEA"),
            Color.parseColor("#81D4FA"),
            Color.parseColor("#90CAF9"),
            Color.parseColor("#9FA8DA"),
            Color.parseColor("#A5D6A7"),
            Color.parseColor("#B0BEC5"),
            Color.parseColor("#B39DDB"),
            Color.parseColor("#BCAAA4"),
            Color.parseColor("#C5E1A5"),
            Color.parseColor("#CE93D8"),
            Color.parseColor("#E6EE9C"),
            Color.parseColor("#EF9A9A"),
            Color.parseColor("#F48FB1"),
            Color.parseColor("#FFAB91"),
            Color.parseColor("#FFCC80"),
            Color.parseColor("#FFE082"),
            Color.parseColor("#FFF59D")
        )
    }
}