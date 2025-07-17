package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.telephony.SubscriptionManager
import android.util.TypedValue
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.util.Log
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.os.Build

class ButtonManagementActivity : AppCompatActivity() {
    private lateinit var buttonsContainer: LinearLayout
    private var buttonConfigs = mutableListOf<ButtonConfig>()
    private val gson = Gson()
    private lateinit var subscriptionManager: SubscriptionManager

    private val configActivityResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            loadButtonConfigs()
            updateButtonsList()
            Toast.makeText(this, "Изменения сохранены", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_button_management)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Управление кнопками"
        }

        subscriptionManager = getSystemService(SubscriptionManager::class.java)
        buttonsContainer = findViewById(R.id.buttonsContainer)

        loadButtonConfigs()
        updateButtonsList()

        findViewById<Button>(R.id.addNewButton).setOnClickListener {
            vibrate()
            configActivityResult.launch(Intent(this, ButtonConfigActivity::class.java))
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private fun loadButtonConfigs() {
        val sharedPref = getSharedPreferences("SMSAppPrefs", MODE_PRIVATE)
        val buttonConfigsJson = sharedPref.getString("buttonConfigs", "[]") ?: "[]"
        val objectsJson = sharedPref.getString("objects", "[]") ?: "[]"
        val devicesJson = sharedPref.getString("devices", "[]") ?: "[]"

        val buttonType = object : TypeToken<List<ButtonConfig>>() {}.type
        val objectType = object : TypeToken<List<ObjectConfig>>() {}.type
        val deviceType = object : TypeToken<List<DeviceConfig>>() {}.type

        val loadedConfigs = gson.fromJson<List<ButtonConfig>>(buttonConfigsJson, buttonType) ?: listOf()
        val objects = gson.fromJson<List<ObjectConfig>>(objectsJson, objectType) ?: listOf()
        val devices = gson.fromJson<List<DeviceConfig>>(devicesJson, deviceType) ?: listOf()

        // Оптимизация: преобразуем списки в Map для быстрого поиска
        val devicesMap = devices.associateBy { it.id }
        val objectsMap = objects.associateBy { it.id }

        buttonConfigs = loadedConfigs
            .mapNotNull { config ->
                val deviceConfig = devicesMap[config.deviceId]
                if (deviceConfig == null) {
                    null // Удаляем кнопки с несуществующим устройством
                } else {
                    val objectName = objectsMap[config.objectId]?.name ?: "Неизвестный объект"
                    config.copy(
                        objectNames = objectName,
                        deviceNames = deviceConfig.name
                    )
                }
            }
            .sortedBy { it.order } // Сортировка по полю order
            .toMutableList()
    }

    private fun updateButtonsList() {
        buttonsContainer.removeAllViews()

        if (buttonConfigs.isEmpty()) {
            buttonsContainer.addView(createEmptyView())
            return
        }

        buttonConfigs.groupBy { config -> config.objectNames }.forEach { (objectName, configs) ->
            buttonsContainer.addView(createObjectGroupView(objectName, configs))
        }
    }

    private fun createEmptyView(): TextView {
        return TextView(this).apply {
            text = "Нет сохраненных кнопок"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_MaterialComponents_Body1)
            setPadding(0, 16.dpToPx(), 0, 0)
        }
    }

    private fun createObjectGroupView(objectName: String, configs: List<ButtonConfig>): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 24.dpToPx() }

            addView(createObjectNameView(objectName))
            configs.forEachIndexed { index, config ->
                addView(createButtonRowView(index, config))
            }
        }
    }

    private fun createObjectNameView(objectName: String): TextView {
        return TextView(this).apply {
            text = objectName
            textSize = 18f
            setPadding(0, 16.dpToPx(), 0, 8.dpToPx())
        }
    }

    private fun createButtonRowView(index: Int, config: ButtonConfig): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8.dpToPx() }

            addView(createButtonView(index, config))
            addView(createEditButton(config))
            addView(createDeleteButton(config))
        }
    }

    private fun createButtonView(index: Int, config: ButtonConfig): Button {
        return Button(this).apply {
            text = "${index + 1}. ${config.name} (${config.deviceNames})"
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
            }

            // Стилизация кнопки
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setSingleLine(false)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setLineSpacing(0f, 1.1f)

            // Используем ripple эффект из ресурсов
            background = ContextCompat.getDrawable(context, R.drawable.ripple_effect_green)
            elevation = 4f
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                stateListAnimator = null
            }

            // Анимация нажатия
            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        vibrate()
                        v.isPressed = true
                        v.animate()
                            .scaleX(0.95f)
                            .scaleY(0.95f)
                            .setDuration(100)
                            .start()
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        v.isPressed = false
                        v.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(100)
                            .withEndAction {
                                showButtonDetails(config)
                            }
                            .start()
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        v.isPressed = false
                        v.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(100)
                            .start()
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun createEditButton(config: ButtonConfig): Button {
        return Button(this).apply {
            text = "✏️"
            background = ContextCompat.getDrawable(context, R.drawable.ripple_effect_green)
            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        vibrate()
                        v.isPressed = true
                        v.animate()
                            .scaleX(0.95f)
                            .scaleY(0.95f)
                            .setDuration(100)
                            .start()
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        v.isPressed = false
                        v.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(100)
                            .withEndAction {
                                editButton(config)
                            }
                            .start()
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        v.isPressed = false
                        v.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(100)
                            .start()
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun createDeleteButton(config: ButtonConfig): Button {
        return Button(this).apply {
            text = "❌"
            background = ContextCompat.getDrawable(context, R.drawable.ripple_effect_green)
            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        vibrate()
                        v.isPressed = true
                        v.animate()
                            .scaleX(0.95f)
                            .scaleY(0.95f)
                            .setDuration(100)
                            .start()
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        v.isPressed = false
                        v.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(100)
                            .withEndAction {
                                showDeleteConfirmationDialog(config)
                            }
                            .start()
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        v.isPressed = false
                        v.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(100)
                            .start()
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun showDeleteConfirmationDialog(config: ButtonConfig) {
        vibrate()
        AlertDialog.Builder(this@ButtonManagementActivity)
            .setTitle("Удаление кнопки")
            .setMessage("Вы уверены, что хотите удалить кнопку \"${config.name}\"?")
            .setPositiveButton("Удалить") { _, _ ->
                // Полностью удаляем кнопку
                val sharedPref = getSharedPreferences("SMSAppPrefs", MODE_PRIVATE)
                val json = sharedPref.getString("buttonConfigs", "[]") ?: "[]"
                val type = object : TypeToken<List<ButtonConfig>>() {}.type
                val allConfigs = gson.fromJson<List<ButtonConfig>>(json, type) ?: mutableListOf()

                val updatedConfigs = allConfigs.toMutableList().apply {
                    removeAll { it.id == config.id }
                }

                sharedPref.edit()
                    .putString("buttonConfigs", gson.toJson(updatedConfigs))
                    .apply()

                // Обновляем локальный список и UI
                buttonConfigs.removeAll { it.id == config.id }
                updateButtonsList()
                setResult(RESULT_OK)
                Toast.makeText(this@ButtonManagementActivity, "Кнопка удалена", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Отмена", null)
            .create()
            .apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(
                        ContextCompat.getColor(this@ButtonManagementActivity, R.color.green_500)
                    )
                    getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(
                        ContextCompat.getColor(this@ButtonManagementActivity, R.color.green_500)
                    )
                }
            }
            .show()
    }

    private fun showButtonDetails(config: ButtonConfig) {
        vibrate()
        val dialog = AlertDialog.Builder(this, R.style.CustomAlertDialogStyle)
            .setTitle("Детали кнопки")
            .setMessage("Объект: ${config.objectNames}\nУстройство: ${config.deviceNames}\nСообщение: ${config.message}")
            .setPositiveButton("OK") { d, _ -> d.dismiss() }
            .create()

        dialog.window?.attributes?.windowAnimations = R.style.DialogAnimation
        dialog.window?.setBackgroundDrawableResource(R.drawable.dialog_background_dark)

        // Устанавливаем ширину диалога
        dialog.show()
        val window = dialog.window
        window?.let {
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val dialogWidth = (screenWidth * 0.7).toInt()

            val layoutParams = WindowManager.LayoutParams()
            layoutParams.copyFrom(it.attributes)
            layoutParams.width = dialogWidth
            layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT
            it.attributes = layoutParams
        }
    }

    private fun editButton(config: ButtonConfig) {
        configActivityResult.launch(
            Intent(this, ButtonConfigActivity::class.java).apply {
                putExtra("buttonId", config.id)
            }
        )
    }

    private fun saveButtonConfigs() {
        try {
            // Обновляем порядок только текущих кнопок
            buttonConfigs.forEachIndexed { index, config ->
                config.order = index
            }

            val sharedPref = getSharedPreferences("SMSAppPrefs", MODE_PRIVATE)
            val json = sharedPref.getString("buttonConfigs", "[]") ?: "[]"
            val type = object : TypeToken<List<ButtonConfig>>() {}.type
            val allConfigs = gson.fromJson<List<ButtonConfig>>(json, type) ?: mutableListOf()

            // Создаем карту для быстрого доступа к обновленным конфигурациям
            val updatedConfigsMap = buttonConfigs.associateBy { it.id }

            // Объединяем списки: обновленные кнопки + неизмененные кнопки других объектов
            val updatedConfigs = allConfigs
                .filter { config ->
                    // Оставляем только кнопки других объектов ИЛИ обновленные кнопки
                    !buttonConfigs.any { it.objectId == config.objectId } ||
                            updatedConfigsMap.containsKey(config.id)
                }
                .map { config ->
                    updatedConfigsMap[config.id] ?: config
                }

            sharedPref.edit()
                .putString("buttonConfigs", gson.toJson(updatedConfigs))
                .apply()

            Toast.makeText(this, "Изменения сохранены", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка сохранения: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e("ButtonManagement", "Ошибка сохранения", e)
        }
    }

    private fun Int.dpToPx(): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        this.toFloat(),
        resources.displayMetrics
    ).toInt()
}