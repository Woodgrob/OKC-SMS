package ru.okc.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class DeviceManagementActivity : AppCompatActivity() {
    private lateinit var devicesContainer: LinearLayout
    private var devices = mutableListOf<DeviceConfig>()
    private val gson = Gson()

    private val configActivityResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            if (result.resultCode == RESULT_OK) {
                loadDevices()
                updateDevicesList()
                Toast.makeText(this, "Устройство сохранено", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка обработки результата", e)
            Toast.makeText(this, "Ошибка обновления списка устройств", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        setContentView(R.layout.activity_device_management)

        devicesContainer = findViewById(R.id.devicesContainer)
        val addButton = findViewById<Button>(R.id.addNewDevice)

        loadDevices()
        updateDevicesList()

        addButton.setOnClickListener {
            configActivityResult.launch(Intent(this, DeviceConfigActivity::class.java))
        }
    }

    private fun loadDevices() {
        val sharedPref = getSharedPreferences("SMSAppPrefs", MODE_PRIVATE)
        val json = sharedPref.getString("devices", "[]") ?: "[]"
        val type = object : TypeToken<List<DeviceConfig>>() {}.type
        devices = gson.fromJson(json, type) ?: mutableListOf()
    }

    private fun updateDevicesList() {
        devicesContainer.removeAllViews()

        if (devices.isEmpty()) {
            val emptyText = TextView(this).apply {
                text = "Нет сохраненных устройств"
                setPadding(0, 16.dpToPx(), 0, 0)
                textSize = 16f
            }
            devicesContainer.addView(emptyText)
            return
        }

        devices.forEachIndexed { index, device ->
            val deviceRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 16.dpToPx()
                }
            }

            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )

                TextView(context).apply {
                    text = "${index + 1}. ${device.name}"
                    textSize = 18f
                    setPadding(0, 8.dpToPx(), 0, 4.dpToPx())
                    addView(this)
                }

                TextView(context).apply {
                    text = "Телефон: ${device.phoneNumber} | SIM: ${device.simSlot + 1}"
                    textSize = 14f
                    setPadding(0, 4.dpToPx(), 0, 8.dpToPx())
                    addView(this)
                }

                deviceRow.addView(this)
            }

            Button(this).apply {
                text = "✏️"
                setOnClickListener { editDevice(device) }
                deviceRow.addView(this)
            }

            Button(this).apply {
                text = "❌"
                setOnClickListener { deleteDevice(device) }
                deviceRow.addView(this)
            }

            devicesContainer.addView(deviceRow)
        }
    }

    private fun editDevice(device: DeviceConfig) {
        vibrate()
        val intent = Intent(this, DeviceConfigActivity::class.java).apply {
            putExtra("deviceId", device.id)
        }
        configActivityResult.launch(intent)
    }

    private fun deleteDevice(device: DeviceConfig) {
        vibrate()
        AlertDialog.Builder(this)
            .setTitle("Удаление устройства")
            .setMessage("Вы уверены, что хотите удалить устройство ${device.name}?")
            .setPositiveButton("Удалить") { _, _ ->
                devices.removeAll { it.id == device.id }
                saveDevices()
                updateDevicesList()
                setResult(RESULT_OK)
                Toast.makeText(this, "Устройство удалено", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Отмена", null)
            .create()
            .apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(ContextCompat.getColor(this@DeviceManagementActivity, R.color.green_500))
                    getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(ContextCompat.getColor(this@DeviceManagementActivity, R.color.green_500))
                }
            }
            .show()
    }

    private fun saveDevices() {
        val sharedPref = getSharedPreferences("SMSAppPrefs", MODE_PRIVATE)
        sharedPref.edit().apply {
            putString("devices", gson.toJson(devices))
            apply()
        }
    }

    private fun Int.dpToPx(): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            this.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    companion object {
        private const val TAG = "DeviceManagementActivity"
    }
}