package ru.okc.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.DragEvent
import android.view.View
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
import android.graphics.Color


class ObjectManagementActivity : AppCompatActivity() {
    private lateinit var objectsContainer: LinearLayout
    private var objects = mutableListOf<ObjectConfig>()
    private val gson = Gson()

    companion object {
        private const val TAG = "ObjectManagementActivity"
    }

    private val configActivityResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            if (result.resultCode == RESULT_OK) {
                loadObjects()
                updateObjectsList()
                Toast.makeText(this, "Объект сохранен", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка обработки результата", e)
            Toast.makeText(this, "Ошибка обновления списка объектов", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        try {
            setContentView(R.layout.activity_object_management)

            objectsContainer = findViewById(R.id.objectsContainer)
                ?: throw IllegalStateException("Контейнер объектов не найден")

            val addButton = findViewById<Button>(R.id.addNewObject)
                ?: throw IllegalStateException("Кнопка добавления не найдена")

            loadObjects()
            updateObjectsList()

            addButton.setOnClickListener {
                try {
                    configActivityResult.launch(Intent(this, ObjectConfigActivity::class.java))
                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка запуска ObjectConfigActivity", e)
                    Toast.makeText(this, "Ошибка открытия редактора объекта", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка инициализации", e)
            Toast.makeText(this, "Ошибка инициализации: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private fun loadObjects() {
        try {
            val sharedPref = getSharedPreferences("SMSAppPrefs", MODE_PRIVATE)
            val json = sharedPref.getString("objects", "[]") ?: "[]"
            val type = object : TypeToken<List<ObjectConfig>>() {}.type

            // Загружаем объекты с сохранением их исходного порядка
            objects = gson.fromJson<List<ObjectConfig>>(json, type)
                ?.sortedBy { it.order } // Сортируем по полю order
                ?.toMutableList()
                ?: mutableListOf()

        } catch (e: Exception) {
            Log.e(TAG, "Ошибка загрузки объектов", e)
            Toast.makeText(this, "Ошибка загрузки объектов", Toast.LENGTH_SHORT).show()
            throw e
        }
    }

    private fun updateObjectsList() {
        try {
            objectsContainer.removeAllViews()

            if (objects.isEmpty()) {
                val emptyText = TextView(this).apply {
                    text = "Нет сохраненных объектов"
                    setPadding(0, 16.dpToPx(), 0, 0)
                    textSize = 16f
                }
                objectsContainer.addView(emptyText)
                return
            }

            objects.sortedBy { it.order }.forEachIndexed { index, obj ->
                try {
                    val objectRow = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            bottomMargin = 16.dpToPx()
                        }
                        tag = index
                        isLongClickable = true
                        setOnLongClickListener { view ->
                            val shadowBuilder = View.DragShadowBuilder(view)
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                                startDragAndDrop(null, shadowBuilder, view, 0)
                            } else {
                                @Suppress("DEPRECATION")
                                startDrag(null, shadowBuilder, view, 0)
                            }
                            true
                        }
                        setOnDragListener { v, event ->
                            when (event.action) {
                                DragEvent.ACTION_DRAG_STARTED -> {
                                    if (v === this) {
                                        v.alpha = 0.5f // Полупрозрачность перетаскиваемого элемента
                                    }
                                    true
                                }
                                DragEvent.ACTION_DRAG_ENTERED -> {
                                    v.setBackgroundColor(Color.LTGRAY) // Яркий цвет при наведении
                                    true
                                }
                                DragEvent.ACTION_DRAG_EXITED -> {
                                    v.setBackgroundColor(Color.TRANSPARENT)
                                    true
                                }
                                DragEvent.ACTION_DRAG_ENDED -> {
                                    v.alpha = 1f // Возвращаем непрозрачность
                                    v.setBackgroundColor(Color.TRANSPARENT)
                                    true
                                }
                                DragEvent.ACTION_DROP -> {
                                    val fromPos = (event.localState as View).tag as Int
                                    val toPos = v.tag as Int

                                    if (fromPos != toPos) {
                                        val movedItem = objects.removeAt(fromPos)
                                        objects.add(toPos, movedItem)
                                        saveObjects()
                                        updateObjectsList()
                                    }
                                    true
                                }
                                else -> false
                            }
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
                            text = "${index + 1}. ${obj.name}"
                            textSize = 18f
                            setPadding(0, 8.dpToPx(), 0, 8.dpToPx())
                            addView(this)
                        }

                        objectRow.addView(this)
                    }

                    Button(this).apply {
                        text = "✏️"
                        setOnClickListener {
                            vibrate()
                            editObject(obj)
                        }
                        objectRow.addView(this)
                    }

                    Button(this).apply {
                        text = "❌"
                        setOnClickListener {
                            vibrate()
                            deleteObject(obj)
                        }
                        objectRow.addView(this)
                    }

                    objectsContainer.addView(objectRow)
                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка создания элемента списка для объекта ${obj.name}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка обновления списка объектов", e)
            throw e
        }
    }

    private fun editObject(obj: ObjectConfig) {
        val intent = Intent(this, ObjectConfigActivity::class.java).apply {
            putExtra("objectId", obj.id)
        }
        configActivityResult.launch(intent)
    }

    private fun deleteObject(obj: ObjectConfig) {
        AlertDialog.Builder(this)
            .setTitle("Удаление объекта")
            .setMessage("Вы уверены, что хотите удалить объект ${obj.name}? Все его кнопки будут перемещены в 'Неизвестный объект'")
            .setPositiveButton("Удалить") { _, _ ->
                vibrate()
                // 1. Удаляем объект
                objects.removeAll { it.id == obj.id }

                // 2. Обновляем кнопки этого объекта (устанавливаем objectId в "")
                val sharedPref = getSharedPreferences("SMSAppPrefs", MODE_PRIVATE)
                val json = sharedPref.getString("buttonConfigs", "[]") ?: "[]"
                val type = object : TypeToken<List<ButtonConfig>>() {}.type
                val allButtons = gson.fromJson<List<ButtonConfig>>(json, type) ?: listOf()

                val updatedButtons = allButtons.map { button ->
                    if (button.objectId == obj.id) {
                        button.copy(objectId = "", objectNames = "Неизвестный объект")
                    } else {
                        button
                    }
                }

                // 3. Сохраняем изменения
                sharedPref.edit()
                    .putString("objects", gson.toJson(objects))
                    .putString("buttonConfigs", gson.toJson(updatedButtons))
                    .apply()

                updateObjectsList()
                setResult(RESULT_OK)
                Toast.makeText(this, "Объект удален. Кнопки перемещены в 'Неизвестный объект'", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Отмена") { dialog, _ ->
                vibrate()
                dialog.dismiss()
            }
            .create()
            .apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(
                        ContextCompat.getColor(this@ObjectManagementActivity, R.color.green_500)
                    )
                    getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(
                        ContextCompat.getColor(this@ObjectManagementActivity, R.color.green_500)
                    )
                }
            }
            .show()
    }

    private fun saveObjects() {
        try {
            objects.forEachIndexed { index, obj -> obj.order = index }
            val sharedPref = getSharedPreferences("SMSAppPrefs", MODE_PRIVATE)
            sharedPref.edit().apply {
                putString("objects", gson.toJson(objects))
                apply()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка сохранения: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun Int.dpToPx(): Int {
        return try {
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                this.toFloat(),
                resources.displayMetrics
            ).toInt()
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка конвертации dp в px", e)
            this
        }
    }
}