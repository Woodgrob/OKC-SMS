package ru.okc.app

import android.content.Intent
import android.os.Bundle
import android.view.DragEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID
import android.util.TypedValue
import android.os.Build
import androidx.core.content.ContextCompat
import android.graphics.Color
import androidx.appcompat.app.AlertDialog

class ObjectConfigActivity : AppCompatActivity() {
    private lateinit var nameEditText: EditText
    private lateinit var buttonsContainer: LinearLayout
    private val gson = Gson()
    private var objectId: String? = null
    private var buttonConfigs = mutableListOf<ButtonConfig>()
    private var originalObjectName: String = "" // Для хранения исходного названия
    private var originalButtonConfigs =
        mutableListOf<ButtonConfig>() // Для хранения исходного списка кнопок

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_object_config)

        // Настройка ActionBar
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = if (intent.getStringExtra("objectId") != null)
                "Редактировать объект"
            else
                "Новый объект"
        }

        nameEditText = findViewById(R.id.objectNameEditText)
        buttonsContainer = findViewById(R.id.buttonsContainer)
        objectId = intent.getStringExtra("objectId")

        // Загружаем исходные данные
        loadOriginalData()

        loadButtonConfigs()
        updateButtonsList()

        findViewById<Button>(R.id.saveButton).setOnClickListener {
            if (validateInput()) {
                saveObject()
            }
        }

        findViewById<Button>(R.id.cancelButton).setOnClickListener {
            restoreOriginalData() // Восстанавливаем исходные данные
            finish()
        }
    }

    private fun loadOriginalData() {
        // Сохраняем исходное название объекта
        objectId?.let {
            val sharedPref = getSharedPreferences("SMSAppPrefs", MODE_PRIVATE)
            val json = sharedPref.getString("objects", "[]") ?: "[]"
            val type = object : TypeToken<List<ObjectConfig>>() {}.type
            val objects = gson.fromJson<List<ObjectConfig>>(json, type) ?: listOf()

            objects.find { it.id == objectId }?.let { obj ->
                originalObjectName = obj.name
                nameEditText.setText(obj.name)
            }
        }

        // Сохраняем исходный список кнопок
        val sharedPref = getSharedPreferences("SMSAppPrefs", MODE_PRIVATE)
        val json = sharedPref.getString("buttonConfigs", "[]") ?: "[]"
        val type = object : TypeToken<List<ButtonConfig>>() {}.type
        val allConfigs = gson.fromJson<List<ButtonConfig>>(json, type) ?: listOf()
        originalButtonConfigs = allConfigs.filter { it.objectId == objectId }.toMutableList()
    }

    private fun restoreOriginalData() {
        if (objectId != null) {
            // Восстанавливаем исходные данные только если это редактирование существующего объекта
            val sharedPref = getSharedPreferences("SMSAppPrefs", MODE_PRIVATE)

            // Восстанавливаем объекты
            val objectsJson = sharedPref.getString("objects", "[]") ?: "[]"
            val objectsType = object : TypeToken<List<ObjectConfig>>() {}.type
            val objects =
                gson.fromJson<List<ObjectConfig>>(objectsJson, objectsType) ?: mutableListOf()

            val updatedObjects = objects.toMutableList().apply {
                removeAll { it.id == objectId }
                add(ObjectConfig(objectId!!, originalObjectName))
            }

            // Восстанавливаем кнопки
            val buttonsJson = sharedPref.getString("buttonConfigs", "[]") ?: "[]"
            val buttonsType = object : TypeToken<List<ButtonConfig>>() {}.type
            val allButtons =
                gson.fromJson<List<ButtonConfig>>(buttonsJson, buttonsType) ?: mutableListOf()

            val updatedButtons = allButtons.toMutableList().apply {
                // Удаляем все кнопки этого объекта
                removeAll { it.objectId == objectId }
                // Добавляем исходные кнопки
                addAll(originalButtonConfigs)
            }

            // Сохраняем восстановленные данные
            sharedPref.edit()
                .putString("objects", gson.toJson(updatedObjects))
                .putString("buttonConfigs", gson.toJson(updatedButtons))
                .apply()
        }
    }

    private fun validateInput(): Boolean {
        return if (nameEditText.text.toString().trim().isEmpty()) {
            showError("Введите название объекта")
            false
        } else {
            true
        }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun loadObject() {
        val sharedPref = getSharedPreferences("SMSAppPrefs", MODE_PRIVATE)
        val json = sharedPref.getString("objects", "[]") ?: "[]"
        val type = object : TypeToken<List<ObjectConfig>>() {}.type
        val objects = gson.fromJson<List<ObjectConfig>>(json, type) ?: listOf()

        objects.find { it.id == objectId }?.let { obj ->
            nameEditText.setText(obj.name)
        }
    }

    private fun loadButtonConfigs() {
        val sharedPref = getSharedPreferences("SMSAppPrefs", MODE_PRIVATE)
        val json = sharedPref.getString("buttonConfigs", "[]") ?: "[]"
        val type = object : TypeToken<List<ButtonConfig>>() {}.type
        val allConfigs = gson.fromJson<List<ButtonConfig>>(json, type) ?: listOf()

        buttonConfigs = allConfigs
            .filter { it.objectId == objectId }
            .sortedBy { it.order } // Сортируем по порядку
            .toMutableList()
    }

    private fun updateButtonsList() {
        buttonsContainer.removeAllViews()

        if (buttonConfigs.isEmpty()) {
            val emptyText = TextView(this).apply {
                text = "Нет кнопок для этого объекта"
                setPadding(0, 16.dpToPx(), 0, 0)
            }
            buttonsContainer.addView(emptyText)
            return
        }

        buttonConfigs.forEachIndexed { index, config ->
            val buttonRow = createButtonRow(index, config)
            buttonsContainer.addView(buttonRow)
        }
    }

    private fun createButtonRow(index: Int, config: ButtonConfig): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 8.dpToPx()
            }

            // Устанавливаем тег с позицией
            tag = index

            // Сохраняем ссылку на текущий View
            val currentRow = this

            // Включаем возможность перетаскивания
            isLongClickable = true
            setOnLongClickListener { view ->
                // Начинаем перетаскивание
                val shadowBuilder = View.DragShadowBuilder(view)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    startDragAndDrop(null, shadowBuilder, view, 0)
                } else {
                    @Suppress("DEPRECATION")
                    startDrag(null, shadowBuilder, view, 0)
                }
                true
            }

            // Добавляем слушатель перетаскивания
            setOnDragListener { v, event ->
                when (event.action) {
                    DragEvent.ACTION_DRAG_STARTED -> {
                        // Скрываем оригинальный элемент при начале перетаскивания
                        if (v === currentRow) {
                            v.alpha = 0.5f // Полупрозрачность перетаскиваемого элемента
                        }
                        true
                    }

                    DragEvent.ACTION_DRAG_ENTERED -> {
                        // Подсвечиваем область, куда можно переместить
                        v.setBackgroundColor(Color.LTGRAY) // Яркий цвет при наведении
                        true
                    }

                    DragEvent.ACTION_DRAG_EXITED -> {
                        // Убираем подсветку
                        v.setBackgroundColor(Color.TRANSPARENT)
                        true
                    }

                    DragEvent.ACTION_DRAG_ENDED -> {
                        // Показываем элемент снова
                        v.alpha = 1f // Возвращаем непрозрачность
                        v.setBackgroundColor(Color.TRANSPARENT)
                        true
                    }

                    DragEvent.ACTION_DROP -> {
                        // Получаем позиции
                        val fromPos = (event.localState as View).tag as Int
                        val toPos = v.tag as Int

                        if (fromPos != toPos) {
                            // Обновляем данные
                            val movedItem = buttonConfigs.removeAt(fromPos)
                            buttonConfigs.add(toPos, movedItem)

                            // Обновляем порядок
                            buttonConfigs.forEachIndexed { i, btn ->
                                btn.order = i
                            }

                            saveButtonOrder()
                            updateButtonsList()
                        }
                        true
                    }

                    else -> false
                }
            }

            // Текст с информацией о кнопке
            TextView(this@ObjectConfigActivity).apply {
                text = "${index + 1}. ${config.name}"
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
                setPadding(16.dpToPx(), 8.dpToPx(), 16.dpToPx(), 8.dpToPx())
                addView(this)
            }

            // Кнопка удаления
            Button(this@ObjectConfigActivity).apply {
                text = "❌"
                setOnClickListener {
                    vibrate() // Добавляем вибрацию при нажатии
                    removeButton(config)
                }
                addView(this)
            }
        }
    }

    private fun removeButton(config: ButtonConfig) {
        AlertDialog.Builder(this)
            .setTitle("Удаление кнопки")
            .setMessage("Вы уверены, что хотите удалить кнопку \"${config.name}\"?")
            .setPositiveButton("Удалить") { _, _ ->
                vibrate()
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

                buttonConfigs.removeAll { it.id == config.id }
                updateButtonsList()
                Toast.makeText(this, "Кнопка удалена", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Отмена") { dialog, _ ->
                vibrate()
                dialog.dismiss()
            }
            .create()
            .apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(
                        ContextCompat.getColor(this@ObjectConfigActivity, R.color.green_500)
                    )
                    getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(
                        ContextCompat.getColor(this@ObjectConfigActivity, R.color.green_500)
                    )
                }
            }
            .show()
    }

    private fun saveButtonOrder() {
        val sharedPref = getSharedPreferences("SMSAppPrefs", MODE_PRIVATE)
        val json = sharedPref.getString("buttonConfigs", "[]") ?: "[]"
        val type = object : TypeToken<List<ButtonConfig>>() {}.type
        val allConfigs = gson.fromJson<List<ButtonConfig>>(json, type) ?: mutableListOf()

        // Создаем карту для быстрого обновления
        val updatedConfigsMap = buttonConfigs.associateBy { it.id }

        // Обновляем только нужные конфигурации
        val updatedConfigs = allConfigs.map { config ->
            updatedConfigsMap[config.id] ?: config
        }

        sharedPref.edit()
            .putString("buttonConfigs", gson.toJson(updatedConfigs))
            .apply()
    }

    private fun saveObject() {
        val sharedPref = getSharedPreferences("SMSAppPrefs", MODE_PRIVATE)
        val json = sharedPref.getString("objects", "[]") ?: "[]"
        val type = object : TypeToken<List<ObjectConfig>>() {}.type
        val objects = gson.fromJson<List<ObjectConfig>>(json, type) ?: listOf()

        // Сохраняем исходный порядок или устанавливаем новый для нового объекта
        val currentOrder = objectId?.let { id ->
            objects.find { it.id == id }?.order ?: objects.size
        } ?: objects.size

        val newObject = ObjectConfig(
            id = objectId ?: UUID.randomUUID().toString(),
            name = nameEditText.text.toString().trim(),
            order = currentOrder // Важно: сохраняем исходный порядок
        )

        saveToSharedPrefs(newObject)
        setResult(RESULT_OK)
        finish()
    }

    private fun saveToSharedPrefs(newObject: ObjectConfig) {
        val sharedPref = getSharedPreferences("SMSAppPrefs", MODE_PRIVATE)
        val json = sharedPref.getString("objects", "[]") ?: "[]"
        val type = object : TypeToken<List<ObjectConfig>>() {}.type
        val objects = gson.fromJson<List<ObjectConfig>>(json, type) ?: mutableListOf()

        val updatedObjects = objects.toMutableList().apply {
            // Если объект уже существует, заменяем его, сохраняя позицию
            val existingIndex = indexOfFirst { it.id == newObject.id }
            if (existingIndex != -1) {
                set(existingIndex, newObject)
            } else {
                // Новый объект добавляем в конец
                add(newObject)
            }
        }

        sharedPref.edit()
            .putString("objects", gson.toJson(updatedObjects))
            .apply()
    }

    private fun Int.dpToPx(): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        this.toFloat(),
        resources.displayMetrics
    ).toInt()

}