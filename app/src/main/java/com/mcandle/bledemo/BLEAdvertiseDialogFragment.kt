package com.mcandle.bledemo

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import vpos.apipackage.Beacon

class BLEAdvertiseDialogFragment : DialogFragment() {

    private lateinit var timerText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnStop: Button
    private lateinit var btnRestart: Button
    private lateinit var descriptionText: TextView

    private var countDownTimer: CountDownTimer? = null
    private val durationSec = 10
    private var hasStarted = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_ble_advertise, null)

        timerText = view.findViewById(R.id.tv_timer)
        progressBar = view.findViewById(R.id.progress_timer)
        btnStop = view.findViewById(R.id.btn_stop)
        btnRestart = view.findViewById(R.id.btn_restart)
        descriptionText = view.findViewById(R.id.tv_description)

        progressBar.max = durationSec
        progressBar.progress = durationSec

        // 확인 버튼 없이: 중지/재시작만 사용
        btnStop.setOnClickListener { stopAdvertising(manual = true) }
        btnRestart.setOnClickListener { startAdvertising() }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(view)
            .setCancelable(false)
            .create()

        // 다이얼로그가 뜨면 자동 시작 (중복 방지)
        if (!hasStarted) {
            hasStarted = true
            startAdvertising()
        }

        return dialog
    }

    fun startAdvertising() {
        // 버튼 상태
        btnStop.visibility = View.VISIBLE
        btnRestart.visibility = View.GONE
        descriptionText.text = "BLE 광고 중입니다. 10초 후 자동 종료됩니다."

        // 진행 표시 초기화
        progressBar.max = durationSec
        progressBar.progress = durationSec
        timerText.text = "${durationSec}초"

        // MainActivity 통해 광고 시작
        (activity as? MainActivity)?.sendAdvertise()

        // 타이머 시작
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(durationSec * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = (millisUntilFinished / 1000).toInt()
                timerText.text = "${seconds}초"
                progressBar.progress = seconds
            }
            override fun onFinish() {
                stopAdvertising(manual = false)
            }
        }.start()
    }

    private fun stopAdvertising(manual: Boolean) {
        countDownTimer?.cancel()
        countDownTimer = null
        progressBar.progress = 0
        timerText.text = "0초"

        // MainActivity 통해 광고 중지
        (activity as? MainActivity)?.stopAdvertise()

        // 버튼 상태
        btnStop.visibility = View.GONE
        btnRestart.visibility = View.VISIBLE
        descriptionText.text = if (manual) {
            "광고가 중단되었습니다."
        } else {
            "광고가 완료되었습니다. 다시 시작할 수 있습니다."
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        countDownTimer?.cancel()
        countDownTimer = null
    }
}
