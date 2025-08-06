package com.mcandle.bledemo

import android.graphics.PorterDuff
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class BLEDeviceAdapter(
    private val deviceList: MutableList<DeviceModel>,
    private val onDeviceClick: (DeviceModel) -> Unit  // ✅ DeviceModel을 그대로 전달하도록 수정
) : RecyclerView.Adapter<BLEDeviceAdapter.BLEDeviceViewHolder>() {

    class BLEDeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val deviceAddressTextView: TextView = itemView.findViewById(R.id.deviceAddress)
        val deviceRssiTextView: TextView = itemView.findViewById(R.id.deviceRssi)
        val deviceNameTextView: TextView = itemView.findViewById(R.id.deviceName)
        val deviceRssiIcon: ImageView = itemView.findViewById(R.id.deviceRssiIcon)
        val txPowerTextView: TextView = itemView.findViewById(R.id.tvTxPower)
        val bondStateTextView: TextView = itemView.findViewById(R.id.tvBondState)
        val manufacturerDataTextView: TextView = itemView.findViewById(R.id.tvManufacturerData)
        val serviceUuidsTextView: TextView = itemView.findViewById(R.id.tvServiceUUIDs)
        val serviceDataTextView: TextView = itemView.findViewById(R.id.tvServiceData)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BLEDeviceViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_ble_device, parent, false)
        return BLEDeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: BLEDeviceViewHolder, position: Int) {
        val device = deviceList[position]

        holder.deviceAddressTextView.text = device.address
        holder.deviceRssiTextView.text = "${device.rssi} dBm"
        holder.deviceNameTextView.text = device.name

        holder.txPowerTextView.text = "TX : ${device.txPower ?: "N/A"} dBm"
        holder.txPowerTextView.visibility = if (device.txPower != null) View.VISIBLE else View.GONE

        holder.manufacturerDataTextView.text = "Manufacturer Data: ${device.manufacturerData?.let { device.getManufacturerDataHex() } ?: "N/A"}"
        holder.manufacturerDataTextView.visibility = if (device.manufacturerData.isNotEmpty()) View.VISIBLE else View.GONE

        holder.serviceUuidsTextView.text = "UUIDs: ${device.serviceUuids.joinToString()}"
        holder.serviceUuidsTextView.visibility = if (device.serviceUuids.isNotEmpty()) View.VISIBLE else View.GONE

        holder.serviceDataTextView.text = "Service Data: ${device.serviceData?.let { device.getServiceDataHex() } ?: "N/A"}"
        holder.serviceDataTextView.visibility = if (device.serviceData.isNotEmpty()) View.VISIBLE else View.GONE

        val context = holder.itemView.context
        val grayColor = ContextCompat.getColor(context, R.color.gray)
        val defaultColor = ContextCompat.getColor(context, R.color.default_text_color)

        if (device.rssi == -100) {
            holder.deviceRssiTextView.setTextColor(grayColor)
            holder.deviceRssiIcon.setColorFilter(grayColor, PorterDuff.Mode.SRC_IN)
        } else {
            holder.deviceRssiTextView.setTextColor(defaultColor)
            holder.deviceRssiIcon.setColorFilter(defaultColor, PorterDuff.Mode.SRC_IN)
        }

        // 🔹 클릭 이벤트를 MainActivity에서 처리하도록 변경
        holder.itemView.setOnClickListener {
            onDeviceClick(device)
        }
    }

    override fun getItemCount() = deviceList.size
}