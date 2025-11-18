package com.mcandle.bledemo

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * BLE 연결 및 데이터 송수신 Dialog
 */
class BLEConnectDialogFragment : DialogFragment() {

    companion object {
        private const val ARG_DEVICE_NAME = "device_name"
        private const val ARG_DEVICE_MAC = "device_mac"
        private const val ARG_ORDER_NUMBER = "order_number"

        fun newInstance(device: DeviceModel, orderNumber: String = ""): BLEConnectDialogFragment {
            return BLEConnectDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_DEVICE_NAME, device.name)
                    putString(ARG_DEVICE_MAC, device.address)
                    putString(ARG_ORDER_NUMBER, orderNumber)
                }
            }
        }
    }

    // UI 요소
    private lateinit var tvTitle: TextView
    private lateinit var tvDeviceName: TextView
    private lateinit var tvDeviceMac: TextView
    private lateinit var tvConnectionStatus: TextView
    private lateinit var progressConnection: ProgressBar
    private lateinit var etSendData: EditText
    private lateinit var btnConnect: Button
    private lateinit var btnSend: Button
    private lateinit var btnDisconnect: Button
    private lateinit var btnClose: Button
    private lateinit var tvReceivedLog: TextView
    private lateinit var scrollView: ScrollView

    // 연결 관리자
    private lateinit var bleConnection: BleConnection

    // 디바이스 정보
    private var deviceName: String = ""
    private var deviceMac: String = ""
    private var orderNumber: String = ""

    // 연결 상태
    private var isConnected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, android.R.style.Theme_Material_Light_Dialog_MinWidth)

        arguments?.let {
            deviceName = it.getString(ARG_DEVICE_NAME, "Unknown")
            deviceMac = it.getString(ARG_DEVICE_MAC, "")
            orderNumber = it.getString(ARG_ORDER_NUMBER, "")
        }

        bleConnection = BleConnection(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_ble_connect, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // UI 요소 바인딩
        tvTitle = view.findViewById(R.id.tvTitle)
        tvDeviceName = view.findViewById(R.id.tvDeviceName)
        tvDeviceMac = view.findViewById(R.id.tvDeviceMac)
        tvConnectionStatus = view.findViewById(R.id.tvConnectionStatus)
        progressConnection = view.findViewById(R.id.progressConnection)
        etSendData = view.findViewById(R.id.etSendData)
        btnConnect = view.findViewById(R.id.btnConnect)
        btnSend = view.findViewById(R.id.btnSend)
        btnDisconnect = view.findViewById(R.id.btnDisconnect)
        btnClose = view.findViewById(R.id.btnClose)
        tvReceivedLog = view.findViewById(R.id.tvReceivedLog)
        scrollView = tvReceivedLog.parent as ScrollView

        // 디바이스 정보 표시
        tvDeviceName.text = deviceName
        tvDeviceMac.text = deviceMac

        // 송신 데이터 기본값 설정 (주문번호)
        if (orderNumber.isNotEmpty()) {
            etSendData.setText(orderNumber)
        }

        // 버튼 리스너 설정
        setupButtonListeners()

        // 초기 상태 설정
        updateUIState()
    }

    override fun onStart() {
        super.onStart()
        // Dialog 크기 설정
        dialog?.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
    }

    private fun setupButtonListeners() {
        // Connect 버튼
        btnConnect.setOnClickListener {
            if (deviceMac.isNotEmpty()) {
                connect()
            } else {
                Toast.makeText(context, "MAC 주소가 없습니다", Toast.LENGTH_SHORT).show()
            }
        }

        // Send 버튼
        btnSend.setOnClickListener {
            val data = etSendData.text.toString()
            if (data.isNotEmpty()) {
                sendData(data)
            } else {
                Toast.makeText(context, "송신 데이터를 입력하세요", Toast.LENGTH_SHORT).show()
            }
        }

        // Disconnect 버튼
        btnDisconnect.setOnClickListener {
            disconnect()
        }

        // Close 버튼
        btnClose.setOnClickListener {
            if (isConnected) {
                disconnect()
            }
            dismiss()
        }
    }

    /**
     * 디바이스에 연결
     */
    private fun connect() {
        appendLog("연결 시도: $deviceMac")
        updateStatus("연결 중...", "#FF9800", true)
        setButtonsEnabled(connectEnabled = false, sendEnabled = false, disconnectEnabled = false)

        lifecycleScope.launch(Dispatchers.IO) {
            // 스캔 중지 (MainActivity에서 처리하도록 콜백)
            (activity as? MainActivity)?.stopScan()

            // 연결 시도
            val result = bleConnection.connectToDevice(deviceMac)

            withContext(Dispatchers.Main) {
                when (result) {
                    is ConnectionResult.Success -> {
                        isConnected = true
                        appendLog("연결 성공! Handle: ${result.handle}")
                        updateStatus("연결됨 (Handle: ${result.handle})", "#4CAF50", false)
                        updateUIState()

                        // 수신 대기 시작
                        startReceiving()
                    }
                    is ConnectionResult.Error -> {
                        isConnected = false
                        appendLog("연결 실패: ${result.message}")
                        updateStatus("연결 실패", "#F44336", false)
                        updateUIState()
                    }
                }
            }
        }
    }

    /**
     * 데이터 송신
     */
    private fun sendData(data: String) {
        appendLog("송신: $data")
        updateStatus("송신 중...", "#FF9800", true)
        setButtonsEnabled(connectEnabled = false, sendEnabled = false, disconnectEnabled = false)

        lifecycleScope.launch(Dispatchers.IO) {
            val dataBytes = data.toByteArray()
            val result = bleConnection.sendData(dataBytes)

            withContext(Dispatchers.Main) {
                when (result) {
                    is SendResult.Success -> {
                        appendLog("송신 완료")
                        updateStatus("연결됨", "#4CAF50", false)
                        etSendData.text.clear()
                    }
                    is SendResult.Error -> {
                        appendLog("송신 실패: ${result.message}")
                        updateStatus("송신 실패", "#F44336", false)
                    }
                }
                updateUIState()
            }
        }
    }

    /**
     * 수신 대기 시작
     */
    private fun startReceiving() {
        lifecycleScope.launch(Dispatchers.IO) {
            while (isConnected && bleConnection.isConnected()) {
                val result = bleConnection.receiveData(3000)

                withContext(Dispatchers.Main) {
                    when (result) {
                        is ReceiveResult.Success -> {
                            val receivedStr = String(result.data)
                            appendLog("수신: $receivedStr")
                        }
                        is ReceiveResult.Timeout -> {
                            // 타임아웃은 정상, 계속 대기
                        }
                        is ReceiveResult.Error -> {
                            appendLog("수신 오류: ${result.message}")
                        }
                    }
                }
            }
        }
    }

    /**
     * 연결 해제
     */
    private fun disconnect() {
        appendLog("연결 해제 중...")
        updateStatus("연결 해제 중...", "#FF9800", true)

        lifecycleScope.launch(Dispatchers.IO) {
            isConnected = false
            val success = bleConnection.disconnect()

            withContext(Dispatchers.Main) {
                if (success) {
                    appendLog("연결 해제 완료")
                } else {
                    appendLog("연결 해제 실패")
                }
                updateStatus("연결 안됨", "#F44336", false)
                updateUIState()
            }
        }
    }

    /**
     * 상태 텍스트 업데이트
     */
    private fun updateStatus(status: String, color: String, showProgress: Boolean) {
        tvConnectionStatus.text = status
        tvConnectionStatus.setTextColor(android.graphics.Color.parseColor(color))
        progressConnection.visibility = if (showProgress) View.VISIBLE else View.GONE
    }

    /**
     * UI 상태 업데이트 (버튼 활성화 등)
     */
    private fun updateUIState() {
        setButtonsEnabled(
            connectEnabled = !isConnected,
            sendEnabled = isConnected,
            disconnectEnabled = isConnected
        )
    }

    /**
     * 버튼 활성화 상태 설정
     */
    private fun setButtonsEnabled(
        connectEnabled: Boolean,
        sendEnabled: Boolean,
        disconnectEnabled: Boolean
    ) {
        btnConnect.isEnabled = connectEnabled
        btnSend.isEnabled = sendEnabled
        btnDisconnect.isEnabled = disconnectEnabled
    }

    /**
     * 로그 추가
     */
    private fun appendLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val logEntry = "[$timestamp] $message\n"

        tvReceivedLog.append(logEntry)

        // 스크롤 맨 아래로
        scrollView.post {
            scrollView.fullScroll(View.FOCUS_DOWN)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 연결 해제
        if (bleConnection.isConnected()) {
            lifecycleScope.launch(Dispatchers.IO) {
                bleConnection.disconnect()
            }
        }
    }
}
