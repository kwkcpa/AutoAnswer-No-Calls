package com.autoanswer.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.autoanswer.app.databinding.ActivityMainBinding
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences

    companion object {
        const val PREFS_NAME = "auto_answer_prefs"
        const val KEY_ENABLED = "auto_answer_enabled"
        const val KEY_MESSAGE = "announcement_message"
        const val KEY_DELAY_SECONDS = "answer_delay_seconds"
        const val DEFAULT_MESSAGE = "This is an unmonitored line used for outgoing messages only. Please do not leave a message. To reach us, please call the main number listed on your original correspondence from our entity. Thank you."
        const val CHANNEL_ID = "auto_answer_channel"
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            requestDialerRole()
        } else {
            Toast.makeText(this, "All permissions are required", Toast.LENGTH_LONG).show()
            binding.switchEnabled.isChecked = false
        }
    }

    private val dialerRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            prefs.edit().putBoolean(KEY_ENABLED, true).apply()
            updateStatusIndicator(true)
            Toast.makeText(this, "Auto-answer is now active!", Toast.LENGTH_SHORT).show()
            checkBatteryOptimization()
        } else {
            Toast.makeText(this, "Default dialer role is required for auto-answer", Toast.LENGTH_LONG).show()
            binding.switchEnabled.isChecked = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        createNotificationChannel()
        setupUI()
    }

    override fun onResume() {
        super.onResume()
        updateDialerStatus()
        updateBatteryOptStatus()
    }

    private fun setupUI() {
        val isEnabled = prefs.getBoolean(KEY_ENABLED, false)
        val savedMessage = prefs.getString(KEY_MESSAGE, DEFAULT_MESSAGE) ?: DEFAULT_MESSAGE
        val savedDelay = prefs.getInt(KEY_DELAY_SECONDS, 0)

        binding.switchEnabled.isChecked = isEnabled
        binding.editMessage.setText(savedMessage)
        binding.sliderDelay.value = savedDelay.toFloat()
        binding.textDelayValue.text = if (savedDelay == 0) "Instant" else "${savedDelay}s"

        updateStatusIndicator(isEnabled)

        binding.switchEnabled.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                checkPermissionsAndEnable()
            } else {
                prefs.edit().putBoolean(KEY_ENABLED, false).apply()
                updateStatusIndicator(false)
            }
        }

        binding.btnSaveMessage.setOnClickListener {
            val message = binding.editMessage.text.toString().trim()
            if (message.isEmpty()) {
                binding.editMessage.error = "Message cannot be empty"
                return@setOnClickListener
            }
            prefs.edit().putString(KEY_MESSAGE, message).apply()
            Toast.makeText(this, "Announcement message saved!", Toast.LENGTH_SHORT).show()
        }

        binding.sliderDelay.addOnChangeListener { _, value, _ ->
            val seconds = value.toInt()
            binding.textDelayValue.text = if (seconds == 0) "Instant" else "${seconds}s"
            prefs.edit().putInt(KEY_DELAY_SECONDS, seconds).apply()
        }

        binding.btnTestTts.setOnClickListener {
            val message = binding.editMessage.text.toString().trim()
            if (message.isEmpty()) {
                Toast.makeText(this, "Enter a message first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startService(Intent(this, TtsTestService::class.java).apply {
                putExtra("message", message)
            })
        }

        binding.btnBatteryOpt.setOnClickListener {
            requestBatteryOptimizationExemption()
        }
    }

    private fun updateStatusIndicator(enabled: Boolean) {
        if (enabled) {
            binding.textStatus.text = "ACTIVE"
            binding.textStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            binding.textStatusDesc.text = "All incoming calls will be auto-answered with your announcement"
        } else {
            binding.textStatus.text = "INACTIVE"
            binding.textStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            binding.textStatusDesc.text = "Calls will ring normally"
        }
    }

    private fun updateDialerStatus() {
        val roleManager = getSystemService(RoleManager::class.java)
        val isDefaultDialer = roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
        if (isDefaultDialer) {
            binding.textDialerStatus.text = "Default dialer: This app"
            binding.textDialerStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
        } else {
            binding.textDialerStatus.text = "Default dialer: Other app (tap toggle to set)"
            binding.textDialerStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
        }
    }

    private fun updateBatteryOptStatus() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val isIgnoring = pm.isIgnoringBatteryOptimizations(packageName)
        if (isIgnoring) {
            binding.textBatteryStatus.text = "Battery optimization: Unrestricted"
            binding.textBatteryStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
        } else {
            binding.textBatteryStatus.text = "Battery optimization: Restricted (tap to fix)"
            binding.textBatteryStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
        }
    }

    private fun checkPermissionsAndEnable() {
        val requiredPermissions = arrayOf(
            Manifest.permission.ANSWER_PHONE_CALLS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.POST_NOTIFICATIONS
        )

        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            requestDialerRole()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun requestDialerRole() {
        val roleManager = getSystemService(RoleManager::class.java)
        if (roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
            // Already the default dialer
            prefs.edit().putBoolean(KEY_ENABLED, true).apply()
            updateStatusIndicator(true)
            Toast.makeText(this, "Auto-answer is now active!", Toast.LENGTH_SHORT).show()
            return
        }

        if (roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
            dialerRoleLauncher.launch(intent)
        } else {
            Toast.makeText(this, "Dialer role not available on this device", Toast.LENGTH_LONG).show()
            binding.switchEnabled.isChecked = false
        }
    }

    private fun checkBatteryOptimization() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            AlertDialog.Builder(this)
                .setTitle("Battery Optimization")
                .setMessage("For reliable operation, disable battery optimization for this app.")
                .setPositiveButton("Open Settings") { _, _ -> requestBatteryOptimizationExemption() }
                .setNegativeButton("Later", null)
                .show()
        }
    }

    private fun requestBatteryOptimizationExemption() {
        try {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            Toast.makeText(this, "Find 'Auto Answer' and set to Unrestricted", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Please manually disable battery optimization in Settings", Toast.LENGTH_LONG).show()
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Auto Answer", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Auto answer notifications" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
