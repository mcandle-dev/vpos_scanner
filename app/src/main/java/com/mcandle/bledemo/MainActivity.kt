package com.mcandle.bledemo

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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
    private lateinit var btn1: Button
    private lateinit var btn2: Button
    private lateinit var btn3: Button
    private lateinit var btnConfig: Button  // 추가
    private lateinit var btnAdvertise: Button

    private var bleScan = BleScan() // BleScan 인스턴스 생성can()
    private var isScanning = false // 상태 변수 추가
    private var scanJob: Job? = null // 코루틴 Job 저장 변수
    private var mStartFlag = false
    private var mEnableFlag = true


    private val deviceList = mutableListOf<DeviceModel>()
    private lateinit var adapter: BLEDeviceAdapter

    private var lastUiPost = 0L
    private val UI_POST_INTERVAL = 300L // ms, 필요하면 200~500 사이로 조절

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        recyclerView = findViewById(R.id.recyclerView)
        adapter = BLEDeviceAdapter(deviceList) { device -> onDeviceSelected(device) }
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        btn1 = findViewById(R.id.btn1)
        btn1.setOnClickListener {
            Step1()
            Log.d("MainActivity", "Button 1 clicked")
        }

        btn2 = findViewById(R.id.btn2)
        btn2.setOnClickListener {
            Step2()
            Log.d("MainActivity", "Button 2 clicked")
        }

        btn3 = findViewById(R.id.btn3)
        btn3.setOnClickListener {
            toggleScan()
            Log.d("MainActivity", "Button 3 clicked")
        }

        // 🔹 Config 버튼 연결
        btnConfig = findViewById(R.id.btn_config)
        btnConfig.setOnClickListener {
            showScanFilterConfigDialog()
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

    private fun Step1() {
        val ret = bleScan.enableMasterMode(true)
        Log.d("MainActivity", "Step1: " + ret)
        val mac = bleScan.getDeviceMacAddress()
        Log.d("MainActivity", "mac: " + mac)

    }

    private fun Step2() {
        val ret = bleScan.startNewScan("", "", 0, "", "")
        Log.d("MainActivity", "Step2: " + ret)
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
                if (jsonObj.has("RSP")) {
                    val rspObj = jsonObj.getJSONObject("RSP")
                    name = rspObj.optString("Device Name", "Unknown")
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


    private fun toggleScan() {
        if (isScanning) {
            // Stop Scan
            isScanning = false
            btn3.text = "Start"
            btn1.isEnabled = true
            btn2.isEnabled = true
            scanJob?.cancel() // 코루틴 중지
            bleScan.stopScan() // BLE 스캔 중지
            Log.d("MainActivity", "Scanning stopped")
        } else {
            // Start Scan
            isScanning = true
            btn3.text = "Stop"
            btn1.isEnabled = false
            btn2.isEnabled = false

            // ✅ 스캔 시작 전에 기존 리스트 초기화
            deviceList.clear()
            adapter.notifyDataSetChanged()

            // 코루틴 실행 (백그라운드에서 실행)
            scanJob = CoroutineScope(Dispatchers.IO).launch {
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
            // (a) SharedPreferences에서 companyId/minorUuid 불러오기 (기본값 포함)
            val sp = getSharedPreferences("beaconInfo", MODE_PRIVATE)
            val companyId = sp.getString("companyId", "4C00") ?: "4C00"   // 기본 0x4C00
            val minorUuid = sp.getString("minorUuid", "0506") ?: "0506"   // 저장된 HEX(4자리) 가정
            // (b) majorUuid = phoneNumber(전화번호 4자리) → HEX 4자리
            val majorInt = phoneNumber.toIntOrNull() ?: 0
            val majorUuid = String.format("%04X", majorInt)

            // (c) customUuid = 주문번호(<=8) + 공백패딩(총 12자) + 전화4 → ASCII→HEX
            val orderRaw = etOrderNumber.text.toString().trim()
            val order8 = orderRaw.take(8)
            val customAscii16 = order8 + " ".repeat(12 - order8.length) + phoneNumber
            val customUuid = BLEUtils.asciiToHex(customAscii16).replace(" ", "")

            // (d) 장치에 비콘 파라미터 설정 (백그라운드)
            Thread {
                val beacon = Beacon(companyId, majorUuid, minorUuid, customUuid)
                val ret = At.Lib_SetBeaconParams(beacon)
                runOnUiThread {
                    if (ret == 0) {
                        dialog.dismiss()
                        // ✔ UI에서 안전하게 다이얼로그 띄우기
                        BLEAdvertiseDialogFragment().show(supportFragmentManager, "BLE_ADVERTISE")
                    } else {
                        SendPromptMsg(
                            "Config beacon failed, return: $ret\n" +
                                    "Company ID: 0x${beacon.companyId}\n" +
                                    "Major: 0x${beacon.major}\n" +
                                    "Minor: 0x${beacon.minor}\n" +
                                    "Custom UUID: 0x${beacon.customUuid}\n"
                        )
                    }
                }
            }.start()
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
        val etCompanyId = inputLayout.findViewById<EditText>(R.id.etCompanyId)
        val etMajorUuid = inputLayout.findViewById<EditText>(R.id.etMajorUuid)
        val etMinorUuid = inputLayout.findViewById<EditText>(R.id.etMinorUuid)
        val etCustomUuid = inputLayout.findViewById<EditText>(R.id.etCustomUuid)

        val sp = getSharedPreferences("beaconInfo", MODE_PRIVATE)
        etCompanyId.setText(sp.getString("companyId", "4C00"))

//        etMajorUuid.setText(sp.getString("majorUuid", "2200"))
//        etMinorUuid.setText(sp.getString("minorUuid", "0506"))
        //etCustomUuid.setText(sp.getString("customUuid", "1234567890123456"))
        // 나중 원복
        etMajorUuid.setText("2200")
        etMinorUuid.setText("0506")
        etCustomUuid.setText("1234567890123456")
        AlertDialog.Builder(this)
            .setTitle("Config Beacon")
            .setView(inputLayout)
            .setCancelable(false)
            .setPositiveButton("OK") { dialog, _ ->
                val companyId = etCompanyId.text.toString().trim()
                val majorUuidStr = etMajorUuid.text.toString().trim()
                val minorUuidStr = etMinorUuid.text.toString().trim()
                // 1. 입력값 추출 및 16자리 padding
                val customUuidInput = etCustomUuid.text.toString().trim()
                val paddedCustomUuid = customUuidInput.padEnd(16, ' ')

                // 2. 기존 BLEUtils.asciiToHex() 사용 (공백 제거 포함)
                val customUuid = BLEUtils.asciiToHex(paddedCustomUuid).replace(" ", "")


                // 1. majorUuid: 10진수 → 16진수 4자리 문자열
                val majorInt = majorUuidStr.toIntOrNull()
                val majorUuid = if (majorInt != null && majorInt in 0..9999) {
                    String.format("%04X", majorInt)  // 4자리 16진수(대문자)
                } else {
                    ""
                }

                // 2. minorUuid: 10진수 → 16진수 문자열 (4자리로 맞추려면 "%04X" 사용)
                val minorInt = minorUuidStr.toIntOrNull()
                val minorUuid = if (minorInt != null) {
                    minorInt.toString(16).uppercase()   // 자리수 제한 없이 16진수 문자열 (대문자)
                    String.format("%04X", minorInt)  // 4자리로 맞추려면 이 라인 사용
                } else {
                    ""
                }

                Log.d("ATCommand", "AT+BEACON=${companyId},${majorUuid},${minorUuid},${customUuid},0")

                if (companyId.isEmpty() || majorUuid.isEmpty() || minorUuid.isEmpty() || customUuid.isEmpty()) {
                    SendPromptMsg("Empty Field!\n")
                    mStartFlag = false
                    return@setPositiveButton
                } else {
                    Log.d(
                        "ATCommand",
                        "AT+BEACON=${companyId},${majorUuid},${minorUuid},${customUuid},0"
                    )
                }

                Thread {
                    val beacon = Beacon(companyId, majorUuid, minorUuid, customUuid)
                    val ret = At.Lib_SetBeaconParams(beacon)
                    if (ret == 0) {
                        SendPromptMsg(
                            "Config beacon succeeded!\n" +
                                    "Company ID: 0x${beacon.companyId}\n" +
                                    "Major: 0x${beacon.major}\n" +
                                    "Minor: 0x${beacon.minor}\n" +
                                    "Custom UUID: 0x${beacon.customUuid}\n"
                        )
                        val editor = sp.edit()
                        editor.putString("companyId", companyId)
                        editor.putString("majorUuid", majorUuid)
                        editor.putString("minorUuid", minorUuid)
                        editor.putString("customUuid", customUuid)
                        editor.apply()
                    } else {
                        SendPromptMsg("Config beacon failed, return: $ret\n")
                    }
                    mStartFlag = false
                }.start()
            }
            .setNegativeButton("Cancel") { _, _ ->
                SendPromptMsg("Cancel Config Beacon.\n")
                mStartFlag = false
            }
            .show()
    }

    public fun sendAdvertise() {

        // 확인사항 -2500 오류 방지를 위해서 advertise 전에 scan을 멈추고 시작
        toggleScan()

        var ret = 0
        ret = At.Lib_EnableBeacon(true)
        Log.d("MainActivity", "sendAdvertise-ret: " + ret)

        if (ret == 0) {
            SendPromptMsg("Start beacon succeeded!\n")
            SendPromptMsg("Note: Effective immediately; Power-off preservation.\n")
        }
        else {
            SendPromptMsg("Start beacon failed, return: ${ret}\n")
        }
    }

    public fun stopAdvertise() {
        var ret = 0
        ret = At.Lib_EnableBeacon(false)

        if (ret == 0) {
            SendPromptMsg("Stop beacon succeeded!\n")
            SendPromptMsg("Note: Effective immediately; Power-off preservation.\n")
        } else {
            SendPromptMsg("Stop beacon failed, return: $ret\n")
        }

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

            // 이번 배치에 안 들어온 기기는 지우지 말고 약화 표시
            for (device in deviceList) {
                if (newDevices.none { it.address == device.address }) {
                    device.rssi = -100
                }
            }

            for (newDevice in newDevices) {
                val existingDevice = deviceList.find { it.address == newDevice.address }
                if (existingDevice != null) {
                    // RSSI는 항상 최신
                    existingDevice.rssi = newDevice.rssi

                    // ✅ 새 값이 있을 때만 덮어쓰기 (빈 문자열이면 이전 값 유지)
                    if (newDevice.manufacturerData.isNotEmpty()) {
                        existingDevice.manufacturerData = newDevice.manufacturerData
                    }
                    if (newDevice.serviceUuids.isNotEmpty()) {
                        existingDevice.serviceUuids = newDevice.serviceUuids
                    }
                    if (newDevice.serviceData.isNotEmpty()) {
                        existingDevice.serviceData = newDevice.serviceData
                    }

                    // 이름은 Unknown일 때만 새 값으로
                    if (existingDevice.name == "Unknown" && newDevice.name.isNotBlank()) {
                        existingDevice.name = newDevice.name
                    }

                    // TxPower는 값이 있을 때만 갱신
                    if (newDevice.txPower != null) {
                        existingDevice.txPower = newDevice.txPower
                    }

                    updatedDeviceCount++
                } else {
                    deviceList.add(newDevice)
                    newDeviceCount++
                }
            }

            adapter.notifyDataSetChanged()
            // Log.d("BLE_SCAN", "Updated Device List: New = $newDeviceCount, Updated = $updatedDeviceCount")
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




}