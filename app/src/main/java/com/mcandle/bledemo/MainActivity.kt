package com.mcandle.bledemo

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mcandle.bledemo.utils.BLEUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import vpos.apipackage.At
import vpos.apipackage.Beacon




class MainActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var btn3: Button
    private lateinit var btnAdvertise: Button
    private lateinit var switchBeaconMaster: SwitchCompat

    private var bleScan = BleScan() // BleScan 인스턴스 생성can()
    private var isScanning = false // 상태 변수 추가
    private var scanJob: Job? = null // 코루틴 Job 저장 변수
    private var mStartFlag = false
    private var isBeaconActive = false // Beacon 활성화 상태 (true: 활성화, false: 비활성화)
    private var mMasterFlag = false // BEACON/MASTER 모드 플래그


    private val deviceList = mutableListOf<DeviceModel>()
    private lateinit var adapter: BLEDeviceAdapter

    private var lastUiPost = 0L
    private val UI_POST_INTERVAL = 300L // ms, 필요하면 200~500 사이로 조절

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        
        // Toolbar 설정
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        recyclerView = findViewById(R.id.recyclerView)
        
        // SharedPreferences에서 현재 Display Title 모드 가져오기
        val sp = getSharedPreferences("beaconInfo", MODE_PRIVATE)
        val currentDisplayMode = sp.getString("displayTitle", "멤버십") ?: "멤버십"
        
        adapter = BLEDeviceAdapter(deviceList, { device -> onDeviceSelected(device) }, currentDisplayMode)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        // BEACON/MASTER 스위치 설정
        switchBeaconMaster = findViewById(R.id.BeaconMaster)
        switchBeaconMaster.setOnCheckedChangeListener { _, isChecked ->
            mMasterFlag = isChecked
            Log.d("MainActivity", "Mode switched to: ${if (isChecked) "MASTER" else "BEACON"}")
        }

        btn3 = findViewById(R.id.btn3)
        btn3.setOnClickListener {
            toggleScan()
            Log.d("MainActivity", "Button 3 clicked")
        }

        // 🔹 Advertise 버튼 연결
        btnAdvertise = findViewById(R.id.btn_advertise)
        btnAdvertise.setOnClickListener {
            BLEAdvertiseDialogFragment().show(supportFragmentManager, "BLE_ADVERTISE")
        }

        // 🔹 BleScan에서 데이터를 받을 콜백 설정
        bleScan.setDataReceiveListener { buff ->
            runOnUiThread {
                Log.e("TAG", "Lib_ComRecvAT buff: $buff") // 🔥 MainActivity에서 buff 로그 출력
                // 나중에 UI 업데이트 로직 추가 가능 (ex: TextView)
            }
        }

    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                showScanFilterConfigDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }


    // 안전한 복사본 만들기 (예외 없이)
    private fun copyJsonArraySafe(source: JSONArray): JSONArray {
        val newArray = JSONArray()
        for (i in 0 until source.length()) {
            try {
                val obj = source.getJSONObject(i)
                // JSONObject 자체도 새로 생성하여 값만 복사
                val newObj = JSONObject(obj.toString())
                newArray.put(newObj)
            } catch (e: Exception) {
                // 예외 무시 (잘못된 인덱스/데이터)
            }
        }
        return newArray
    }

    // 1) 스캔 시작 (IO)
    private fun startBleScan() {
        val sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE)
        val scanResultListener = object : BleScan.ScanResultListener {
            override fun onScanResult(scanData: JSONArray) {
                // 결과 들어올 때마다 백그라운드 파싱으로 위임
                onBleScanResult(scanData)
            }
        }
        lifecycleScope.launch(Dispatchers.IO) {
            bleScan.startScanAsync(sharedPreferences, scanResultListener)
        }
    }

    // 2) 결과 처리: 백그라운드에서 파싱 + 디바운스, UI에는 updateDeviceList만 올리기
    private fun onBleScanResult(scanData: JSONArray) {
        lifecycleScope.launch(Dispatchers.Default) {
            // 안전 복제 (원본 변경 대비)
            val snapshot = copyJsonArraySafe(scanData)

            val newDevices = mutableListOf<DeviceModel>()
            for (i in 0 until snapshot.length()) {
                val jsonObj = snapshot.getJSONObject(i)

                val address = jsonObj.optString("MAC") ?: "Unknown"
                val rssi = jsonObj.optInt("RSSI", -100)

                var name = "Unknown"
                // RSP 패킷에서 Device Name 확인
                if (jsonObj.has("RSP")) {
                    val rspObj = jsonObj.getJSONObject("RSP")
                    name = rspObj.optString("Device Name", "Unknown")
                }
                // ADV 패킷에서도 Device Name 확인 (RSP에서 못 찾았을 때)
                if (name == "Unknown" && jsonObj.has("ADV")) {
                    val advObj = jsonObj.getJSONObject("ADV")
                    name = advObj.optString("Device Name", "Unknown")
                }

                // Service UUIDs 키 변형 대응: "Service UUIDs" / "ServiceUUIDs"
                val serviceUuid: String = if (jsonObj.has("ADV")) {
                    val advObj = jsonObj.getJSONObject("ADV")
                    val raw = advObj.optString(
                        "Service UUIDs",
                        advObj.optString("ServiceUUIDs", "")
                    )
                    raw.trim().split(Regex("\\s+")).firstOrNull() ?: ""
                } else ""

                val serviceData: String = if (jsonObj.has("ADV")) {
                    jsonObj.getJSONObject("ADV").optString("Service Data", "")
                } else ""

                val manufacturerData: String = if (jsonObj.has("ADV")) {
                    jsonObj.getJSONObject("ADV").optString("Manufacturer Data", "")
                } else ""

                newDevices.add(
                    DeviceModel(
                        name = name,
                        address = address,
                        rssi = rssi,
                        serviceUuids = serviceUuid,
                        serviceData = serviceData,
                        manufacturerData = manufacturerData
                    )
                )
            }

            // 디바운스: 최근 UI 반영 후 300ms 이내면 스킵
            val now = System.currentTimeMillis()
            if (now - lastUiPost < UI_POST_INTERVAL) return@launch
            lastUiPost = now

            withContext(Dispatchers.Main) {
                updateDeviceList(newDevices) // ✅ 메인에선 이것만!
            }
        }
    }


    private fun onDeviceSelected(device: DeviceModel) {
        // 예: 다이얼로그 띄우기, 로그 찍기, 상세 페이지 이동 등
        showPaymentDialog(device)
    }


    /**
     * 스캔 중지 (public - BLEConnectDialogFragment에서 호출)
     */
    fun stopScan() {
        if (isScanning) {
            isScanning = false
            btn3.text = "Start"
            scanJob?.cancel()
            bleScan.stopScan()
            Log.d("MainActivity", "Scanning stopped via stopScan()")
        }
    }

    private fun toggleScan() {
        if (isScanning) {
            // Stop Scan
            stopScan()
        } else {
            // Start Scan - Beacon 모드이면 자동으로 Master 모드로 변경
            if (!mMasterFlag) {
                mMasterFlag = true
                switchBeaconMaster.isChecked = true // UI 업데이트
                SendPromptMsg("Switched to MASTER mode for scanning")
                Log.d("MainActivity", "Automatically switched to MASTER mode")
            }
            
            isScanning = true
            btn3.text = "Stop"

            // ✅ 스캔 시작 전에 기존 리스트 초기화
            deviceList.clear()
            adapter.notifyDataSetChanged()

            // 코루틴 실행 (백그라운드에서 실행)
            scanJob = CoroutineScope(Dispatchers.IO).launch {
                // Master 모드 확인 및 설정
                At.Lib_EnableMaster(true)
                startBleScan()
            }
            Log.d("MainActivity", "Scanning started")
        }
    }

    private fun showPaymentDialog(device: DeviceModel) {
        // 예시: serviceUuids에서 값 추출 (구체적 데이터 구조에 따라 맞게 수정)

        val phoneNumber = { addr: String? ->
            addr?.filter(Char::isDigit)  // 숫자만 남기기
                ?.drop(16)               // 앞 16자리 버리기
                ?.take(4)                // 다음 4자리만
                ?: ""
        }(device.serviceUuids)

        val orderNumber = { addr: String? ->
            addr?.filter(Char::isDigit)  // 숫자만 남기기
                ?.take(8)                // 다음 4자리만
                ?: "12345678"
        }(device.serviceUuids)

        val dialogView = layoutInflater.inflate(R.layout.dialog_payment_info, null)
        val tvCardNumber = dialogView.findViewById<TextView>(R.id.tvCardNumber)
        val tvPhone = dialogView.findViewById<TextView>(R.id.tvPhone)
        val etOrderNumber = dialogView.findViewById<EditText>(R.id.etOrderNumber)

        tvPhone.text =  "$phoneNumber 님"
        // 카드번호와 전화번호 UI 표시
        tvCardNumber.text = { addr: String? ->
            addr
                ?.filter(Char::isDigit)          // 1. 숫자만 남김 (하이픈 제거)
                ?.take(16)                        // 2. 앞 16자리만
                ?.chunked(4)                      // 3. 4자리씩 나눔
                ?.joinToString("-")               // 4. 하이픈으로 연결
                ?: ""
        }(device.serviceUuids)
        etOrderNumber.setText(orderNumber)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialogView.findViewById<Button>(R.id.btnMobilePayment).setOnClickListener {
            dialog.dismiss()
            // Connect Dialog 표시
            BLEConnectDialogFragment.newInstance(device).show(supportFragmentManager, "BLE_CONNECT")
        }
        dialogView.findViewById<Button>(R.id.btnOfflinePayment).setOnClickListener {
            // TODO: 오프라인 결제 처리
            dialog.dismiss()
            // 실제 카드번호 변수(cardNo)로 전달
            val memberCardNo = "1234-5678-9012-3456" // ← 추후 실 데이터로 변경
            showMemberInfoDialog(memberCardNo)
        }

        dialog.show()
    }



    private fun showScanFilterConfigDialog() {
        if (mStartFlag) {
            Log.i("unique Start", "start---------->flag=$mStartFlag")
            return
        }
        mStartFlag = true
        SendPromptMsg("")

        val inputLayout = layoutInflater.inflate(R.layout.item_beacon_info, null)
        val etBroadcastName = inputLayout.findViewById<EditText>(R.id.etBroadcastName)
        val rgDisplayTitle = inputLayout.findViewById<RadioGroup>(R.id.rgDisplayTitle)
        val rbMembership = inputLayout.findViewById<RadioButton>(R.id.rbMembership)
        val rbBLE = inputLayout.findViewById<RadioButton>(R.id.rbBLE)

        val scanSp = getSharedPreferences("scanInfo", MODE_PRIVATE)
        etBroadcastName.setText(scanSp.getString("broadcastName", "MCan"))

        val sp = getSharedPreferences("beaconInfo", MODE_PRIVATE)

        // Display Title 라디오 버튼 초기값 설정
        val savedDisplayTitle = sp.getString("displayTitle", "멤버십")
        when (savedDisplayTitle) {
            "멤버십" -> rbMembership.isChecked = true
            "BLE" -> rbBLE.isChecked = true
            else -> rbMembership.isChecked = true // 기본값: 멤버십
        }
        AlertDialog.Builder(this)
            .setTitle("Setting")
            .setView(inputLayout)
            .setCancelable(false)
            .setPositiveButton("OK") { dialog, _ ->
                val broadcastName = etBroadcastName.text.toString().trim()

                // Display Title 선택값 가져오기
                val selectedDisplayTitle = when (rgDisplayTitle.checkedRadioButtonId) {
                    R.id.rbMembership -> "멤버십"
                    R.id.rbBLE -> "BLE"
                    else -> "멤버십" // 기본값
                }

                // 설정 저장
                val scanEditor = scanSp.edit()
                scanEditor.putString("broadcastName", broadcastName)
                scanEditor.apply()

                val editor = sp.edit()
                editor.putString("displayTitle", selectedDisplayTitle)
                editor.apply()

                // UI 업데이트
                adapter.updateDisplayMode(selectedDisplayTitle)
                Log.d("MainActivity", "Settings saved: broadcastName=$broadcastName, displayTitle=$selectedDisplayTitle")
                SendPromptMsg("설정이 저장되었습니다.")
                mStartFlag = false
            }
            .setNegativeButton("Cancel") { _, _ ->
                SendPromptMsg("Cancel Config Beacon.\n")
                mStartFlag = false
            }
            .show()
    }

    public fun sendAdvertise() {
        // -2500 타임아웃 에러 방지를 위해 스캔 상태 확인 후 일시 중지
        val wasScanningBeforeAdvertise = isScanning
        if (isScanning) {
            isScanning = false
            scanJob?.cancel()
            bleScan.stopScan()
            btn3.text = "Start"
            Log.d("MainActivity", "Scan paused for advertising")
        }

        Thread {
            // BEACON 모드로 변경 (Master 모드에서 Beacon 모드로 전환)
            if (mMasterFlag) {
                At.Lib_EnableMaster(false) // Master 모드 해제
                mMasterFlag = false
                runOnUiThread {
                    switchBeaconMaster.isChecked = false // UI 업데이트
                    SendPromptMsg("Switched to BEACON mode for advertising")
                }
                Log.d("MainActivity", "Automatically switched from MASTER to BEACON mode")
            }
            
            val ret = At.Lib_EnableBeacon(true)
            Log.d("MainActivity", "sendAdvertise-ret: " + ret)
            
            runOnUiThread {
                when (ret) {
                    0 -> {
                        SendPromptMsg("Start beacon succeeded!\n")
                        SendPromptMsg("Note: Effective immediately; Power-off preservation.\n")
                        isBeaconActive = true // Beacon이 활성화된 상태
                    }
                    -2500 -> SendPromptMsg("Beacon start failed: Communication timeout\n")
                    -2501 -> SendPromptMsg("Beacon start failed: Wrong length\n")
                    -2502 -> SendPromptMsg("Beacon start failed: Communication error\n")
                    -2503 -> SendPromptMsg("Beacon start failed: Wrong data\n")
                    -2504 -> SendPromptMsg("Beacon start failed: Wrong command\n")
                    -2505 -> SendPromptMsg("Beacon start failed: EDC error\n")
                    -2506 -> SendPromptMsg("Beacon start failed: Other error\n")
                    -2507 -> SendPromptMsg("Beacon start failed: CRC16 error\n")
                    -2508 -> SendPromptMsg("Beacon start failed: Open failed\n")
                    -2509 -> SendPromptMsg("Beacon start failed: Send error\n")
                    -2510 -> SendPromptMsg("Beacon start failed: Receive error\n")
                    else -> SendPromptMsg("Beacon start failed, return: ${ret}\n")
                }
                
                // 이전에 스캔 중이었다면 자동으로 다시 시작 (Master 모드로 전환 후)
                if (wasScanningBeforeAdvertise && ret == 0) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (!isScanning) {
                            // Master 모드로 다시 전환하고 스캔 재개
                            mMasterFlag = true
                            switchBeaconMaster.isChecked = true
                            toggleScan()
                            Log.d("MainActivity", "Scan resumed after advertising")
                        }
                    }, 1000) // 1초 후 스캔 재개
                }
            }
        }.start()
    }

    public fun stopAdvertise() {
        Thread {
            val ret = At.Lib_EnableBeacon(false)
            
            runOnUiThread {
                if (ret == 0) {
                    SendPromptMsg("Stop beacon succeeded!\n")
                    SendPromptMsg("Note: Effective immediately; Power-off preservation.\n")
                    isBeaconActive = false // Beacon이 비활성화된 상태
                } else {
                    SendPromptMsg("Stop beacon failed, return: $ret\n")
                }
            }
        }.start()
    }

    fun SendPromptMsg(strInfo: String?) {
        strInfo?.let {
            runOnUiThread {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun updateDeviceList(newDevices: List<DeviceModel>) {
        runOnUiThread {
            var newDeviceCount = 0
            var updatedDeviceCount = 0
            val positionsToUpdate = mutableListOf<Int>()

            // 이번 배치에 안 들어온 기기는 지우지 말고 약화 표시
            for ((index, device) in deviceList.withIndex()) {
                if (newDevices.none { it.address == device.address }) {
                    if (device.rssi != -100) {
                        device.rssi = -100
                        positionsToUpdate.add(index)
                    }
                }
            }

            for (newDevice in newDevices) {
                val existingIndex = deviceList.indexOfFirst { it.address == newDevice.address }
                if (existingIndex != -1) {
                    val existingDevice = deviceList[existingIndex]
                    var isChanged = false

                    // RSSI는 항상 최신
                    if (existingDevice.rssi != newDevice.rssi) {
                        existingDevice.rssi = newDevice.rssi
                        isChanged = true
                    }

                    // ✅ 새 값이 있을 때만 덮어쓰기 (빈 문자열이면 이전 값 유지)
                    if (newDevice.manufacturerData.isNotEmpty() && existingDevice.manufacturerData != newDevice.manufacturerData) {
                        existingDevice.manufacturerData = newDevice.manufacturerData
                        isChanged = true
                    }
                    if (newDevice.serviceUuids.isNotEmpty() && existingDevice.serviceUuids != newDevice.serviceUuids) {
                        existingDevice.serviceUuids = newDevice.serviceUuids
                        isChanged = true
                    }
                    if (newDevice.serviceData.isNotEmpty() && existingDevice.serviceData != newDevice.serviceData) {
                        existingDevice.serviceData = newDevice.serviceData
                        isChanged = true
                    }

                    // 이름은 Unknown일 때만 새 값으로
                    if (existingDevice.name == "Unknown" && newDevice.name.isNotBlank()) {
                        existingDevice.name = newDevice.name
                        isChanged = true
                    }

                    // TxPower는 값이 있을 때만 갱신
                    if (newDevice.txPower != null && existingDevice.txPower != newDevice.txPower) {
                        existingDevice.txPower = newDevice.txPower
                        isChanged = true
                    }

                    if (isChanged) {
                        positionsToUpdate.add(existingIndex)
                    }
                    updatedDeviceCount++
                } else {
                    deviceList.add(newDevice)
                    adapter.notifyItemInserted(deviceList.size - 1)
                    newDeviceCount++
                }
            }

            // 변경된 항목만 개별적으로 업데이트
            for (position in positionsToUpdate) {
                adapter.notifyItemChanged(position)
            }

            // Log.d("BLE_SCAN", "Updated Device List: New = $newDeviceCount, Updated = $updatedDeviceCount, Changed positions = ${positionsToUpdate.size}")
        }
    }


    private fun showMemberInfoDialog(cardNo: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_member_info, null)

        // 하드코딩 예시 데이터(카드번호만 인자로 받음)
        val name = "홍길동"
        val grade = "VVIP"
        val phone = "010-1234-5678"
        val kakao = "Y"
        // val cardNo = "1234-5678-9012-3456"  // <= 제거
        val creditCard = "우리카드(1234-56), 신한카드(9876-54)"
        val promotion = "여름 정기 세일 혜택 제공!"

        dialogView.findViewById<TextView>(R.id.tvName).text = "이름: $name"
        dialogView.findViewById<TextView>(R.id.tvGrade).text = "등급: $grade"
        dialogView.findViewById<TextView>(R.id.tvPhone).text = "전화번호: $phone"
        dialogView.findViewById<TextView>(R.id.tvKakao).text = "카카오 사용: $kakao"
        dialogView.findViewById<TextView>(R.id.tvCardNo).text = "멤버십 카드번호: $cardNo"
        dialogView.findViewById<TextView>(R.id.tvCreditCard).text = "신용카드 정보: $creditCard"
        dialogView.findViewById<TextView>(R.id.tvPromotion).text = "프로모션: $promotion"

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialogView.findViewById<Button>(R.id.btnClose).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun finish() {
        Log.d("MainActivity", "=== finish() called ===")
        Log.d("MainActivity", "isBeaconActive: $isBeaconActive")
        Log.d("MainActivity", "mMasterFlag: $mMasterFlag")
        Log.d("MainActivity", "isScanning: $isScanning")
        
        if (isBeaconActive || isScanning) {
            Log.d("MainActivity", "Beacon active or scanning - showing dialog")
            val message = when {
                isBeaconActive && isScanning -> "Beacon is active and scanning is running. Do you want to stop both before closing?"
                isBeaconActive -> "Beacon is currently active. Do you want to disable it before closing?"
                isScanning -> "Scanning is currently running. Do you want to stop it before closing?"
                else -> "Do you want to exit?"
            }
            
            AlertDialog.Builder(this)
                .setTitle("Exit Application")
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("Yes") { _, _ ->
                    Log.d("MainActivity", "User chose YES - stopping services")
                    Thread {
                        if (isBeaconActive) {
                            At.Lib_EnableBeacon(false)
                            Log.d("MainActivity", "Beacon disabled")
                        }
                        if (isScanning) {
                            isScanning = false
                            scanJob?.cancel()
                            bleScan.stopScan()
                            Log.d("MainActivity", "Scanning stopped")
                        }
                        runOnUiThread {
                            super.finish()
                        }
                    }.start()
                }
                .setNegativeButton("No") { _, _ ->
                    Log.d("MainActivity", "User chose NO - finishing without stopping services")
                    super.finish()
                }
                .show()
            return
        }
        
        // 스캔 중인 경우 중지
        if (isScanning) {
            Log.d("MainActivity", "Stopping scan before finish")
            isScanning = false
            scanJob?.cancel()
            bleScan.stopScan()
        }
        
        Log.d("MainActivity", "Calling super.finish()")
        super.finish()
    }

    override fun onBackPressed() {
        Log.d("MainActivity", "=== onBackPressed() called ===")
        Log.d("MainActivity", "isBeaconActive: $isBeaconActive")
        Log.d("MainActivity", "mMasterFlag: $mMasterFlag") 
        Log.d("MainActivity", "isScanning: $isScanning")
        Log.d("MainActivity", "About to call finish()")
        finish()
    }

}