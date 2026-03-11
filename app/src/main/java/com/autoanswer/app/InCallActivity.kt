package com.autoanswer.app

import android.os.Bundle
import android.telecom.Call
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.autoanswer.app.databinding.ActivityIncallBinding

class InCallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIncallBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show on lock screen
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        binding = ActivityIncallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnAnswer.setOnClickListener {
            CallManager.answer()
        }

        binding.btnHangup.setOnClickListener {
            CallManager.hangup()
        }

        binding.btnReject.setOnClickListener {
            CallManager.reject()
        }

        // Listen for call state changes
        CallManager.onCallChanged = { call ->
            runOnUiThread { updateUI(call) }
        }

        // Update with current state
        updateUI(CallManager.currentCall)
    }

    private fun updateUI(call: Call?) {
        if (call == null) {
            finish()
            return
        }

        // Show caller info
        val handle = call.details?.handle
        val number = handle?.schemeSpecificPart ?: "Unknown"
        binding.textCallerNumber.text = number

        when (call.state) {
            Call.STATE_RINGING -> {
                binding.textCallState.text = "Incoming Call"
                binding.btnAnswer.visibility = View.VISIBLE
                binding.btnReject.visibility = View.VISIBLE
                binding.btnHangup.visibility = View.GONE
            }
            Call.STATE_DIALING, Call.STATE_CONNECTING -> {
                binding.textCallState.text = "Dialing..."
                binding.btnAnswer.visibility = View.GONE
                binding.btnReject.visibility = View.GONE
                binding.btnHangup.visibility = View.VISIBLE
            }
            Call.STATE_ACTIVE -> {
                binding.textCallState.text = "Connected"
                binding.btnAnswer.visibility = View.GONE
                binding.btnReject.visibility = View.GONE
                binding.btnHangup.visibility = View.VISIBLE
            }
            Call.STATE_DISCONNECTED -> {
                binding.textCallState.text = "Call Ended"
                binding.btnAnswer.visibility = View.GONE
                binding.btnReject.visibility = View.GONE
                binding.btnHangup.visibility = View.GONE
                binding.textCallerNumber.postDelayed({ finish() }, 1500)
            }
            else -> {
                binding.textCallState.text = "Call State: ${call.state}"
            }
        }
    }

    override fun onDestroy() {
        CallManager.onCallChanged = null
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Don't allow back during a call
        if (CallManager.currentCall?.state == Call.STATE_ACTIVE ||
            CallManager.currentCall?.state == Call.STATE_RINGING) {
            return
        }
        super.onBackPressed()
    }
}
