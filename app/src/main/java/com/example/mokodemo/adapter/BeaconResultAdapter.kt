package com.example.mokodemo.adapter

import android.app.Activity
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.mokodemo.R
import com.example.mokodemo.entity.BeaconXDevice
import com.example.mokodemo.entity.BeaconXInfo
import com.example.mokodemo.utils.BeaconXParser
import com.moko.support.log.LogModule
import kotlinx.android.synthetic.main.list_item_device.view.*
import java.util.*

/**
 * Beacon Sub list adapter : Show search history list.
 */
class BeaconResultAdapter(
    private val mContext: Context, var mList: ArrayList<BeaconXInfo>,
    private val viewHolderClicks: ViewHolderClicks,
    private val showDistance: Boolean = true,
) : RecyclerView.Adapter<BeaconResultAdapter.ViewHolder>() {
    var mInflater: LayoutInflater = mContext.getSystemService(Activity.LAYOUT_INFLATER_SERVICE) as LayoutInflater

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val v = mInflater.inflate(R.layout.list_item_device, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {
        val model = mList[holder.adapterPosition]
        try {
            holder.itemView.tv_name.text = model.name
            holder.itemView.tv_rssi.text = "${model.rssi}"
            holder.itemView.tv_conn_state.text = ""
            holder.itemView.tv_mac.text = "MAC: ${model.mac}"

            val validDatas: ArrayList<BeaconXInfo.ValidData> = ArrayList(model.validDataHashMap.values)
            Collections.sort(validDatas, Comparator<BeaconXInfo.ValidData> { lhs, rhs ->
                if (lhs.type > rhs.type) {
                    return@Comparator 1
                } else if (lhs.type < rhs.type) {
                    return@Comparator -1
                }
                0
            })

            validDatas.forEach {
                val beaconXDevice: BeaconXDevice = BeaconXParser.getDevice(it.data)
                if (beaconXDevice.isConnected.toInt() == 0) {
                    holder.itemView.tv_conn_state.setText("UNCON")
                } else {
                    holder.itemView.tv_conn_state.setText("CON")
                }
                LogModule.i(beaconXDevice.toString())
            }

            holder.itemView.setOnClickListener {
                viewHolderClicks.onClickItem(holder.adapterPosition)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun getItemCount(): Int {
        return mList.size
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    interface ViewHolderClicks {
        fun onClickItem(position: Int)
    }
}