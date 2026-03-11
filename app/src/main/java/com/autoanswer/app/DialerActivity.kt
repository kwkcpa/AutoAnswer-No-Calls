package com.autoanswer.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.autoanswer.app.databinding.ActivityDialerBinding

class DialerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDialerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDialerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Pre-fill from incoming DIAL intent
        val dialData = intent?.data
        if (dialData != null && dialData.scheme == "tel") {
            binding.editPhoneNumber.setText(dialData.schemeSpecificPart)
        }

        // Dial pad buttons
        val buttons = mapOf(
            binding.btn0 to "0", binding.btn1 to "1", binding.btn2 to "2",
            binding.btn3 to "3", binding.btn4 to "4", binding.btn5 to "5",
            binding.btn6 to "6", binding.btn7 to "7", binding.btn8 to "8",
            binding.btn9 to "9", binding.btnStar to "*", binding.btnHash to "#"
        )

        buttons.forEach { (button, digit) ->
            button.setOnClickListener {
                binding.editPhoneNumber.append(digit)
            }
        }

        binding.btnDelete.setOnClickListener {
            val text = binding.editPhoneNumber.text
            if (text.isNotEmpty()) {
                text.delete(text.length - 1, text.length)
            }
        }

        binding.btnDelete.setOnLongClickListener {
            binding.editPhoneNumber.text.clear()
            true
        }

        binding.btnCall.setOnClickListener {
            val number = binding.editPhoneNumber.text.toString().trim()
            if (number.isEmpty()) {
                Toast.makeText(this, "Enter a number", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            placeCall(number)
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
    }

    private fun placeCall(number: String) {
        try {
            val uri = Uri.fromParts("tel", number, null)
            val telecomManager = getSystemService(TelecomManager::class.java)
            val extras = Bundle()
            telecomManager.placeCall(uri, extras)
        } catch (e: SecurityException) {
            Toast.makeText(this, "Call permission required", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error placing call: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
