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
    private val onDeviceClick: (DeviceModel) -> Unit,
    private var displayMode: String = "BLE"  // "BLE" 또는 "멤버십"
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_BLE = 1
        private const val VIEW_TYPE_MEMBERSHIP = 2
    }

    // 멤버십 정보 업데이트 함수
    fun updateDisplayMode(mode: String) {
        displayMode = mode
        notifyDataSetChanged()
    }

    // ServiceData에서 멤버십 정보 파싱: "2200님 (1234 5678 5567 6666)"
    // Service Data: "31 32 33 34 35 36 37 38 31 32 33 34 35 36 37 38 31 32 33 34"
    // ASCII 변환: "12345678123456781234"
    // 앞 16자리: 카드번호, 뒤 4자리: 전화번호
    private fun parseServiceDataForMembership(serviceData: String): String {
        if (serviceData.isEmpty()) return "정보 없음"

        // Hex to ASCII 변환: "31 32 33 34..." -> "1234..."
        val hexValues = serviceData.trim().split(Regex("\\s+"))
        val asciiString = try {
            hexValues.filter { it.isNotEmpty() }
                .map { Integer.parseInt(it, 16).toChar() }
                .joinToString("")
        } catch (e: Exception) {
            return "파싱 오류"
        }

        if (asciiString.length < 20) return "데이터 부족"

        // 카드번호: 앞 16자리
        val cardNumber = asciiString.take(16)
            .chunked(4)
            .joinToString(" ")

        // 전화번호: 뒤 4자리
        val phoneNumber = asciiString.takeLast(4)

        return "${phoneNumber}님 ($cardNumber)"
    }

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

    class MembershipViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val membershipInfoTextView: TextView = itemView.findViewById(R.id.tvMembershipInfo)
        val macAddressTextView: TextView = itemView.findViewById(R.id.tvMacAddress)
        val rssiValueTextView: TextView = itemView.findViewById(R.id.tvRssiValue)
        val rssiIcon: ImageView = itemView.findViewById(R.id.ivRssiIcon)
    }

    override fun getItemViewType(position: Int): Int {
        return when (displayMode) {
            "멤버십" -> VIEW_TYPE_MEMBERSHIP
            else -> VIEW_TYPE_BLE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_MEMBERSHIP -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_membership_device, parent, false)
                MembershipViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_ble_device, parent, false)
                BLEDeviceViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val device = deviceList[position]

        when (holder) {
            is BLEDeviceViewHolder -> bindBLEDevice(holder, device)
            is MembershipViewHolder -> bindMembershipDevice(holder, device)
        }

        // 클릭 이벤트는 공통
        holder.itemView.setOnClickListener {
            onDeviceClick(device)
        }
    }

    private fun bindBLEDevice(holder: BLEDeviceViewHolder, device: DeviceModel) {
        holder.deviceAddressTextView.text = device.address
        holder.deviceRssiTextView.text = "${device.rssi} dBm"
        holder.deviceNameTextView.text = device.name

        holder.txPowerTextView.text = "TX : ${device.txPower ?: "N/A"} dBm"
        holder.txPowerTextView.visibility = if (device.txPower != null) View.VISIBLE else View.GONE

        holder.manufacturerDataTextView.text = "Manufacturer Data: ${device.getManufacturerDataHex().ifEmpty { "N/A" }}"
        holder.manufacturerDataTextView.visibility = if (device.manufacturerData.isNotEmpty()) View.VISIBLE else View.GONE

        holder.serviceUuidsTextView.text = "UUID: ${device.serviceUuids.ifEmpty { "N/A" }}"
        holder.serviceUuidsTextView.visibility = if (device.serviceUuids.isNotEmpty()) View.VISIBLE else View.GONE

        holder.serviceDataTextView.text = "Service Data: ${device.getServiceDataHex().ifEmpty { "N/A" }}"
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

        // 연결 상태에 따른 배경색 변경
        updateConnectionBackground(holder.itemView, device)
    }

    /**
     * 연결 상태에 따른 배경색 업데이트
     */
    private fun updateConnectionBackground(itemView: View, device: DeviceModel) {
        val context = itemView.context
        when (device.connectionState) {
            ConnectionState.CONNECTED -> {
                itemView.setBackgroundColor(ContextCompat.getColor(context, android.R.color.holo_green_light))
            }
            ConnectionState.CONNECTING -> {
                itemView.setBackgroundColor(ContextCompat.getColor(context, android.R.color.holo_orange_light))
            }
            ConnectionState.ERROR -> {
                itemView.setBackgroundColor(ContextCompat.getColor(context, android.R.color.holo_red_light))
            }
            else -> {
                itemView.setBackgroundColor(ContextCompat.getColor(context, android.R.color.white))
            }
        }
    }

    private fun bindMembershipDevice(holder: MembershipViewHolder, device: DeviceModel) {
        // 멤버십 정보 파싱 및 표시 (Service Data 사용)
        val membershipInfo = parseServiceDataForMembership(device.serviceData)
        holder.membershipInfoTextView.text = membershipInfo

        // MAC 주소
        holder.macAddressTextView.text = device.address

        // RSSI 정보
        holder.rssiValueTextView.text = "${device.rssi} dBm"

        // RSSI 아이콘 색상
        val context = holder.itemView.context
        val grayColor = ContextCompat.getColor(context, R.color.gray)
        val defaultColor = ContextCompat.getColor(context, R.color.default_text_color)

        if (device.rssi == -100) {
            holder.rssiValueTextView.setTextColor(grayColor)
            holder.rssiIcon.setColorFilter(grayColor, PorterDuff.Mode.SRC_IN)
        } else {
            holder.rssiValueTextView.setTextColor(defaultColor)
            holder.rssiIcon.setColorFilter(defaultColor, PorterDuff.Mode.SRC_IN)
        }

        // 연결 상태에 따른 배경색 변경
        updateConnectionBackground(holder.itemView, device)
    }

    override fun getItemCount() = deviceList.size
}