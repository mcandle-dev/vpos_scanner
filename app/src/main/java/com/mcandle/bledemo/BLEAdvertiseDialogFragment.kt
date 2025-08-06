package com.mcandle.bledemo

import android.app.Dialog
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment

class BLEAdvertiseDialogFragment : DialogFragment() {

    private lateinit var timerText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnConfirm: Button
    private lateinit var btnStop: Button
    private lateinit var btnRestart: Button
    private lateinit var descriptionText: TextView

    private var countDownTimer: CountDownTimer? = null
    private val duration = 10

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_ble_advertise, null)

        timerText = view.findViewById(R.id.tv_timer)
        progressBar = view.findViewById(R.id.progress_timer)
        btnConfirm = view.findViewById(R.id.btn_confirm)
        btnStop = view.findViewById(R.id.btn_stop)
        btnRestart = view.findViewById(R.id.btn_restart)
        descriptionText = view.findViewById(R.id.tv_description)

        progressBar.max = duration
        progressBar.progress = duration

        btnConfirm.setOnClickListener {
            startAdvertising()
        }

        btnStop.setOnClickListener {
            stopAdvertising(manual = true)
        }

        btnRestart.setOnClickListener {
            startAdvertising()
        }

        return AlertDialog.Builder(requireContext())
            .setView(view)
            .setCancelable(false)
            .create()
    }

    public fun startAdvertising() {
        btnConfirm.visibility = View.GONE
        btnStop.visibility = View.VISIBLE
        btnRestart.visibility = View.GONE
        descriptionText.text = "BLE 광고 중입니다. 10초 후 자동 종료됩니다."

        // ★ 여기서 MainActivity의 함수 호출
        (activity as? MainActivity)?.sendAdvertise()

        countDownTimer = object : CountDownTimer((duration * 1000).toLong(), 1000) {
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
        progressBar.progress = 0
        timerText.text = "0초"

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
    }
}
