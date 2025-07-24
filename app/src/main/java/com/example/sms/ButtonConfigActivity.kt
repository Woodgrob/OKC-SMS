package ru.okc.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.telephony.SubscriptionManager
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID
import android.widget.CheckBox

class ButtonConfigActivity : AppCompatActivity() {
    private lateinit var nameEditText: EditText
    private lateinit var messageEditText: EditText
    private lateinit var devicesSpinner: Spinner
    private lateinit var objectsSpinner: Spinner
    private var buttonId: String? = null
    private val gson = Gson()
    private var devices = listOf<DeviceConfig>()
    private var objects = listOf<ObjectConfig>()
    private lateinit var subscriptionManager: SubscriptionManager
    private var selectedDevice: DeviceConfig? = null
    private var selectedObject: ObjectConfig? = null
    private lateinit var confirmationCheckBox: CheckBox

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_button_config)

        nameEditText = findViewById(R.id.nameEditText)
        messageEditText = findViewById(R.id.messageEditText)
        devicesSpinner = findViewById(R.id.devicesSpinner)
        objectsSpinner = findViewById(R.id.objectsSpinner)
        subscriptionManager = getSystemService(SubscriptionManager::class.java)
        confirmationCheckBox = findViewById(R.id.confirmationCheckBox)

        checkPermissions()
        loadDevicesAndObjects()
        loadButtonConfigIfExists()

        findViewById<Button>(R.id.saveButton).setOnClickListener {
            saveButtonConfig()
        }
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_PHONE_STATE),
                Constants.PHONE_STATE_PERMISSION_CODE
            )
        }
    }

    private fun loadDevicesAndObjects() {
        val sharedPref = getSharedPreferences("SMSAppPrefs", MODE_PRIVATE)

        // Загрузка устройств
        val devicesJson = sharedPref.getString("devices", "[]") ?: "[]"
        val devicesType = object : TypeToken<List<DeviceConfig>>() {}.type
        devices = gson.fromJson(devicesJson, devicesType) ?: listOf()

        // Загрузка объектов
        val objectsJson = sharedPref.getString("objects", "[]") ?: "[]"
        val objectsType = object : TypeToken<List<ObjectConfig>>() {}.type
        objects = gson.fromJson(objectsJson, objectsType) ?: listOf()

        setupSpinners()
    }

    private fun setupSpinners() {
        // Добавляем пустой элемент в начало списка
        val devicesList = listOf(DeviceConfig("", "Выберите устройство", "", 0)) + devices
        val devicesAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            devicesList.map { it.name }
        )
        devicesAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        devicesSpinner.adapter = devicesAdapter

        devicesSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                vibrate()
                selectedDevice = if (position > 0) devicesList[position] else null
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Обработка случая, когда ничего не выбрано
                selectedDevice = null
            }
        }

        // Аналогично для объектов
        val objectsList = listOf(ObjectConfig("", "Выберите объект")) + objects
        val objectsAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            objectsList.map { it.name }
        )
        objectsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        objectsSpinner.adapter = objectsAdapter

        objectsSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                vibrate()
                selectedObject = if (position > 0) objectsList[position] else null
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Обработка случая, когда ничего не выбрано
                selectedObject = null
            }
        }
    }

    private fun loadButtonConfigIfExists() {
        buttonId = intent.getStringExtra("buttonId")
        buttonId?.let { loadButtonConfig() }
    }

    private fun loadButtonConfig() {
        val sharedPref = getSharedPreferences("SMSAppPrefs", MODE_PRIVATE)
        val json = sharedPref.getString("buttonConfigs", "[]") ?: "[]"
        val type = object : TypeToken<List<ButtonConfig>>() {}.type
        val configs = gson.fromJson<List<ButtonConfig>>(json, type) ?: listOf()

        configs.find { it.id == buttonId }?.let { config ->
            nameEditText.setText(config.name)
            messageEditText.setText(config.message)
            // Устанавливаем состояние чекбокса из конфига
            confirmationCheckBox.isChecked = config.requireConfirmation

            // Установка выбранного устройства
            config.deviceId.takeIf { it.isNotEmpty() }?.let { deviceId ->
                val deviceIndex = devices.indexOfFirst { it.id == deviceId } + 1
                if (deviceIndex > 0) {
                    devicesSpinner.setSelection(deviceIndex)
                }
            }

            // Установка выбранного объекта
            config.objectId.takeIf { it.isNotEmpty() }?.let { objectId ->
                val objectIndex = objects.indexOfFirst { it.id == objectId } + 1
                if (objectIndex > 0) {
                    objectsSpinner.setSelection(objectIndex)
                }
            }
        }
    }

    private fun saveButtonConfig() {
        if (selectedDevice == null || selectedObject == null) {
            Toast.makeText(this, "Выберите устройство и объект", Toast.LENGTH_SHORT).show()
            return
        }

        val name = nameEditText.text.toString().trim()
        val message = messageEditText.text.toString().trim()

        if (name.isEmpty() || message.isEmpty()) {
            Toast.makeText(this, "Заполните все поля", Toast.LENGTH_SHORT).show()
            return
        }

        val sharedPref = getSharedPreferences("SMSAppPrefs", MODE_PRIVATE)
        val json = sharedPref.getString("buttonConfigs", "[]") ?: "[]"
        val type = object : TypeToken<List<ButtonConfig>>() {}.type
        val configs = gson.fromJson<List<ButtonConfig>>(json, type) ?: listOf()

        val existingConfig = buttonId?.let { id -> configs.find { it.id == id } }
        val currentOrder = existingConfig?.order ?: configs.size

        val newConfig = ButtonConfig(
            id = buttonId ?: UUID.randomUUID().toString(),
            name = name,
            message = message,
            deviceId = selectedDevice!!.id,
            objectId = selectedObject!!.id,
            order = currentOrder,
            requireConfirmation = confirmationCheckBox.isChecked // Сохраняем состояние чекбокса
        )

        saveToSharedPrefs(newConfig)
        setResult(RESULT_OK)
        finish()
    }

    private fun saveToSharedPrefs(newConfig: ButtonConfig) {
        val sharedPref = getSharedPreferences("SMSAppPrefs", MODE_PRIVATE)
        val json = sharedPref.getString("buttonConfigs", "[]") ?: "[]"
        val type = object : TypeToken<List<ButtonConfig>>() {}.type
        val configs = gson.fromJson<List<ButtonConfig>>(json, type) ?: mutableListOf()

        val updatedConfigs = configs.toMutableList().apply {
            // Если конфиг уже существует, заменяем его, сохраняя позицию
            val existingIndex = indexOfFirst { it.id == newConfig.id }
            if (existingIndex != -1) {
                set(existingIndex, newConfig)
            } else {
                // Если это новый конфиг, добавляем в конец
                add(newConfig)
            }
        }

        sharedPref.edit()
            .putString("buttonConfigs", gson.toJson(updatedConfigs))
            .apply()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == Constants.PHONE_STATE_PERMISSION_CODE && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadDevicesAndObjects()
        }
    }
}