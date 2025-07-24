package ru.okc.app

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.SubscriptionManager
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

class DeviceConfigActivity : AppCompatActivity() {
    private lateinit var nameEditText: EditText
    private lateinit var phoneEditText: EditText
    private lateinit var simRadioGroup: RadioGroup
    private lateinit var subscriptionManager: SubscriptionManager
    private val gson = Gson()
    private var deviceId: String? = null
    private var isFormatting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_config)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = if (intent.getStringExtra("deviceId") != null)
                "Редактировать устройство"
            else
                "Новое устройство"
        }

        nameEditText = findViewById(R.id.deviceNameEditText)
        phoneEditText = findViewById(R.id.phoneEditText)
        simRadioGroup = findViewById(R.id.simRadioGroup)
        subscriptionManager = getSystemService(SubscriptionManager::class.java)

        deviceId = intent.getStringExtra("deviceId")
        deviceId?.let { loadDevice() }

        checkPermissions()

        findViewById<Button>(R.id.saveButton).setOnClickListener {
            if (validateInput()) {
                saveDevice()
            }
        }

        // Добавляем обработчик вставки из буфера обмена
        phoneEditText.setOnLongClickListener {
            pasteFormattedPhoneNumber()
            true
        }

        // Добавляем форматирование номера телефона
        phoneEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (isFormatting) return

                isFormatting = true
                formatPhoneNumber(s)
                isFormatting = false
            }
        })
    }

    private fun pasteFormattedPhoneNumber() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (clipboard.hasPrimaryClip()) {
            val clipData = clipboard.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val text = clipData.getItemAt(0).text.toString()
                val digits = text.replace(Regex("[^0-9]"), "")

                if (digits.length >= 11) {
                    val formatted = formatRawPhoneNumber(digits)
                    phoneEditText.setText(formatted)
                    phoneEditText.setSelection(formatted.length)
                } else {
                    phoneEditText.setText(digits)
                }
            }
        }
    }

    private fun formatPhoneNumber(s: Editable?) {
        s?.let {
            val digits = it.toString().replace(Regex("[^0-9]"), "")
            if (digits.length > 1) {
                val formatted = formatRawPhoneNumber(digits)
                if (it.toString() != formatted) {
                    it.replace(0, it.length, formatted)
                }
            }
        }
    }

    private fun formatRawPhoneNumber(digits: String): String {
        // Оставляем только цифры
        val cleanDigits = digits.replace(Regex("[^0-9]"), "")

        // Обрабатываем начало номера
        val processedDigits = when {
            cleanDigits.startsWith("8") && cleanDigits.length > 1 -> "7" + cleanDigits.substring(1)
            cleanDigits.startsWith("7") && cleanDigits.length > 1 -> cleanDigits
            else -> "7" + cleanDigits
        }

        // Берем только первые 11 цифр (7 + 10 цифр номера)
        val limitedDigits = if (processedDigits.length > 11) {
            processedDigits.substring(0, 11)
        } else {
            processedDigits
        }

        // Разбиваем на части для форматирования
        val countryCode = "7"
        val mainDigits = if (limitedDigits.startsWith("7")) {
            limitedDigits.substring(1)
        } else {
            limitedDigits
        }.take(10) // Берем только 10 цифр номера

        return when {
            mainDigits.isEmpty() -> "+7"
            mainDigits.length <= 3 -> "+$countryCode($mainDigits"
            mainDigits.length <= 6 -> "+$countryCode(${mainDigits.take(3)})-${mainDigits.substring(3)}"
            mainDigits.length <= 8 -> "+$countryCode(${mainDigits.take(3)})-${mainDigits.substring(3, 6)}-${mainDigits.substring(6)}"
            else -> "+$countryCode(${mainDigits.take(3)})-${mainDigits.substring(3, 6)}-${mainDigits.substring(6, 8)}-${mainDigits.substring(8)}"
        }
    }

    private fun validateInput(): Boolean {
        return when {
            nameEditText.text.toString().trim().isEmpty() -> {
                showError("Введите название устройства")
                false
            }
            phoneEditText.text.toString().trim().isEmpty() -> {
                showError("Введите номер телефона")
                false
            }
            phoneEditText.text.toString().replace(Regex("[^0-9]"), "").length < 11 -> {
                showError("Номер должен содержать 10 цифр после +7")
                false
            }
            else -> true
        }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_PHONE_STATE),
                Constants.PHONE_STATE_PERMISSION_CODE
            )
        } else {
            setupSimOptions()
        }
    }

    private fun loadDevice() {
        val sharedPref = getSharedPreferences("SMSAppPrefs", MODE_PRIVATE)
        val json = sharedPref.getString("devices", "[]") ?: "[]"
        val type = object : TypeToken<List<DeviceConfig>>() {}.type
        val devices = gson.fromJson<List<DeviceConfig>>(json, type) ?: listOf()

        devices.find { it.id == deviceId }?.let { device ->
            nameEditText.setText(device.name)
            phoneEditText.setText(device.phoneNumber)
            when (device.simSlot) {
                0 -> simRadioGroup.check(R.id.sim1Radio)
                1 -> simRadioGroup.check(R.id.sim2Radio)
                else -> simRadioGroup.check(R.id.sim1Radio)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == Constants.PHONE_STATE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupSimOptions()
            } else {
                Toast.makeText(
                    this,
                    "Для работы с SIM-картами нужно разрешение",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun setupSimOptions() {
        try {
            val subscriptions = subscriptionManager.activeSubscriptionInfoList
            findViewById<RadioButton>(R.id.sim1Radio).apply {
                setOnClickListener { vibrate() }
                isEnabled = subscriptions.isNotEmpty()
                text = if (subscriptions.isNotEmpty())
                    "SIM1: ${subscriptions[0].displayName}"
                else
                    "SIM1: Недоступно"
            }
            findViewById<RadioButton>(R.id.sim2Radio).apply {
                setOnClickListener { vibrate() }
                isEnabled = subscriptions.size > 1
                text = if (subscriptions.size > 1)
                    "SIM2: ${subscriptions[1].displayName}"
                else
                    "SIM2: Недоступно"
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка доступа к SIM-картам", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveDevice() {
        // Получаем отформатированный номер и удаляем все, кроме цифр и знака +
        val phoneNumber = phoneEditText.text.toString()
            .replace(Regex("[^0-9+]"), "") // Удаляем все, кроме цифр и +
            .replace("+", "") // Временно удаляем +
            .let { digits ->
                // Добавляем +7 если номер начинается с 7 или 8
                when {
                    digits.startsWith("7") -> "+$digits"
                    digits.startsWith("8") -> "+7${digits.substring(1)}"
                    else -> "+7$digits"
                }
            }
            .take(12) // Берем только +7 и 10 цифр номера (+7XXXXXXXXXX)

        val newDevice = DeviceConfig(
            id = deviceId ?: UUID.randomUUID().toString(),
            name = nameEditText.text.toString().trim(),
            phoneNumber = phoneNumber,
            simSlot = when (simRadioGroup.checkedRadioButtonId) {
                R.id.sim1Radio -> 0
                R.id.sim2Radio -> 1
                else -> 0
            }
        )

        saveToSharedPrefs(newDevice)
        setResult(RESULT_OK)
        finish()
    }

    private fun saveToSharedPrefs(newDevice: DeviceConfig) {
        val sharedPref = getSharedPreferences("SMSAppPrefs", MODE_PRIVATE)
        val json = sharedPref.getString("devices", "[]") ?: "[]"
        val type = object : TypeToken<List<DeviceConfig>>() {}.type
        val devices = gson.fromJson<List<DeviceConfig>>(json, type) ?: mutableListOf()

        val updatedDevices = devices.toMutableList().apply {
            deviceId?.let { removeAll { it.id == deviceId } }
            add(newDevice)
        }

        sharedPref.edit()
            .putString("devices", gson.toJson(updatedDevices))
            .apply()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}