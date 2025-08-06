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
import org.json.JSONArray
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

        // 🔹 Config 버튼 연결
        btnAdvertise = findViewById(R.id.btn_advertise)
        btnAdvertise.setOnClickListener {
            BLEAdvertiseDialogFragment().show(supportFragmentManager, "BLE_ADVERTISE")
            sendAdvertise()
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

    private fun Step3() {
        // 실제 BLE 스캔은 주석 처리
        /*
        val sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE)
        val scanResultListener = object : BleScan.ScanResultListener {
            override fun onScanResult(scanData: JSONArray) {
                runOnUiThread {
                    Log.d("BLE_SCAN", "Received Scan Data: $scanData")
                    // UI 업데이트 코드 추가 가능
                }
            }
        }
        lifecycleScope.launch(Dispatchers.IO) {
            bleScan.startScanAsync(sharedPreferences, scanResultListener)
        }
        */

        // ★★★ 임의의 테스트 DeviceModel 데이터 5개로 UI 테스트 ★★★
        val dummyDevices = listOf(
            DeviceModel(
                name = "Test Beacon 1",
                address = "11:22:33:44:55:61",
                rssi = -53,
                txPower = -8,
                serviceUuids = emptyList(),
                serviceData = emptyMap(),
                manufacturerData = emptyMap()
            ),
            DeviceModel(
                name = "Test Beacon 2",
                address = "11:22:33:44:55:62",
                rssi = -70,
                txPower = null,
                serviceUuids = emptyList(),
                serviceData = emptyMap(),
                manufacturerData = emptyMap()
            ),
            DeviceModel(
                name = "Test Beacon 3",
                address = "11:22:33:44:55:63",
                rssi = -65,
                txPower = -4,
                serviceUuids = emptyList(),
                serviceData = emptyMap(),
                manufacturerData = emptyMap()
            ),
            DeviceModel(
                name = "Test Beacon 4",
                address = "11:22:33:44:55:64",
                rssi = -80,
                txPower = -7,
                serviceUuids = emptyList(),
                serviceData = emptyMap(),
                manufacturerData = emptyMap()
            ),
            DeviceModel(
                name = "Test Beacon 5",
                address = "11:22:33:44:55:65",
                rssi = -90,
                txPower = null,
                serviceUuids = emptyList(),
                serviceData = emptyMap(),
                manufacturerData = emptyMap()
            )
        )

        // ★ UI 업데이트 함수 호출!
        updateDeviceList(dummyDevices)
    }

    private fun onDeviceSelected(device: DeviceModel) {
        // 예: 다이얼로그 띄우기, 로그 찍기, 상세 페이지 이동 등
        showPaymentDialog(device)
    }


    private fun toggleScan() {
        if (isScanning) {
            // Stop Scan
            isScanning = false
            btn3.text = "ComRev"
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

            // 코루틴 실행 (백그라운드에서 실행)
            scanJob = CoroutineScope(Dispatchers.IO).launch {
                Step3()
            }
            Log.d("MainActivity", "Scanning started")
        }
    }

    private fun startScan() {
        // 스캔 리스너 설정
        val scanListener = object : BleScan.ScanResultListener {
            override fun onScanResult(scanData: JSONArray) {
                // 백그라운드 스레드에서 메인 스레드로 전환
                Handler(Looper.getMainLooper()).post {
                    Log.e("BLE_SCAN", "onScanResult: " + scanData)
                    //               updateDeviceList(scanData)
                }
            }
        }
    }

    private fun showPaymentDialog(device: DeviceModel) {
        // 예시: serviceUuids에서 값 추출 (구체적 데이터 구조에 따라 맞게 수정)
        val serviceUuidList = device.serviceUuids.map { it.toString() }
        val phoneNumber = serviceUuidList.getOrNull(0) ?: "****-2200"
        val orderNumber = serviceUuidList.getOrNull(1) ?: ""

        val dialogView = layoutInflater.inflate(R.layout.dialog_payment_info, null)
        val tvCardNumber = dialogView.findViewById<TextView>(R.id.tvCardNumber)
        val tvPhone = dialogView.findViewById<TextView>(R.id.tvPhone)
        val etOrderNumber = dialogView.findViewById<EditText>(R.id.etOrderNumber)

        // 카드번호와 전화번호 UI 표시
        tvCardNumber.text = device.address  // 예시로 MAC 주소 표시
        tvPhone.text = phoneNumber
        etOrderNumber.setText(orderNumber)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialogView.findViewById<Button>(R.id.btnMobilePayment).setOnClickListener {
            // TODO: 모바일 결제 처리
            dialog.dismiss()
            BLEAdvertiseDialogFragment().show(supportFragmentManager, "BLE_ADVERTISE")
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

        etMajorUuid.setText(sp.getString("majorUuid", "0708"))
        etMinorUuid.setText(sp.getString("minorUuid", "0506"))
        etCustomUuid.setText(sp.getString("customUuid", "1234567890,ABCDE"))

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

                Log.d("ATCommand", "AT+BEACON=${companyId},${majorUuidStr},${minorUuidStr},${customUuid},0")

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
        var ret = 0

        ret = if (mStartFlag) {
            At.Lib_EnableMaster(true)
        } else {
            At.Lib_EnableBeacon(true)
        }
        Log.d("MainActivity", "mStartFlag: " + mStartFlag)

        if (ret == 0) {
            mEnableFlag = true
            if (mStartFlag) {
                SendPromptMsg("Start master succeeded!\n")
            } else {
                SendPromptMsg("Start beacon succeeded!\n")
            }
            SendPromptMsg("Note: Effective immediately; Power-off preservation.\n")
        } else {
            SendPromptMsg("Start beacon failed, return: $ret\n")
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

            for (device in deviceList) {
                if (newDevices.none { it.address == device.address }) {
                    device.rssi = -100
                }
            }

            for (newDevice in newDevices) {
                val existingDevice = deviceList.find { it.address == newDevice.address }
                if (existingDevice != null) {
                    existingDevice.rssi = newDevice.rssi
                    existingDevice.manufacturerData = newDevice.manufacturerData
                    existingDevice.serviceData = newDevice.serviceData
                    updatedDeviceCount++
                } else {
                    deviceList.add(newDevice)
                    newDeviceCount++
                }
            }

            adapter.notifyDataSetChanged()
            Log.d("BLE_SCAN", "Updated Device List: New = $newDeviceCount, Updated = $updatedDeviceCount")
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