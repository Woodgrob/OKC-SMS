
        package ru.okc.app

        import android.Manifest
                import android.app.Activity
                import android.content.Intent
                import android.content.pm.PackageManager
                import android.net.Uri
                import android.os.Build
                import android.os.Bundle
                import android.os.Environment
                import android.provider.Settings
                import android.util.Log
                import android.view.View
                import android.view.Window
                import android.view.Gravity
                import android.widget.Button
                import android.widget.CheckBox
                import android.widget.CompoundButton
                import android.widget.LinearLayout
                import android.widget.ProgressBar
                import android.widget.TextView
                import android.widget.Toast
                import androidx.appcompat.app.AlertDialog
                import androidx.appcompat.app.AppCompatActivity
                import androidx.core.app.ActivityCompat
                import androidx.core.content.ContextCompat
                import androidx.core.content.FileProvider
                import androidx.documentfile.provider.DocumentFile
                import com.google.gson.Gson
                import com.google.gson.reflect.TypeToken
                import kotlinx.coroutines.CoroutineScope
                import kotlinx.coroutines.Dispatchers
                import kotlinx.coroutines.launch
                import kotlinx.coroutines.withContext
                import java.io.File
                import java.io.FileOutputStream
                import java.io.IOException
                import java.text.SimpleDateFormat
                import java.util.Date
                import java.util.Locale
                import kotlinx.coroutines.launch
                import kotlinx.coroutines.withContext
                import android.os.VibrationEffect
                import android.os.Vibrator
                import android.os.VibratorManager
                import android.view.MotionEvent
                import android.content.Context


        class ImportExportActivity : AppCompatActivity() {
            private lateinit var treeContainer: LinearLayout
            private lateinit var btnExport: Button
            private lateinit var btnImport: Button
            private lateinit var progressBar: ProgressBar
            private lateinit var statusText: TextView
            private lateinit var titleText: TextView
            private lateinit var btnSelectAll: Button
            private lateinit var btnDeselectAll: Button
            private val gson = Gson()
            private var objects = listOf<ObjectConfig>()
            private var devices = listOf<DeviceConfig>()
            private var buttonConfigs = listOf<ButtonConfig>()
            private var isExportMode = true
            private var selectedExportFormat = "json"
            private var importData: ExportData? = null

            companion object {
                private const val TAG = "ImportExportActivity"
                private const val REQUEST_CODE_EXPORT = 1001
                private const val REQUEST_CODE_IMPORT = 1002
                private const val REQUEST_CODE_SHARE = 1003
                private const val PROVIDER_AUTHORITY = "com.example.myapplication.fileprovider"
                private const val STORAGE_PERMISSION_CODE = 1004

                private const val PREFS_NAME = "SMSAppPrefs"
                private const val PREFS_OBJECTS_KEY = "objects"
                private const val PREFS_DEVICES_KEY = "devices"
                private const val PREFS_BUTTONS_KEY = "buttonConfigs"

                private const val MAX_IMPORT_ITEMS = 500 // Лимит для защиты от слишком больших файлов
            }

            data class ExportData(
                val objects: List<ObjectConfig> = emptyList(),
                val devices: List<DeviceConfig> = emptyList(),
                val buttons: List<ButtonConfig> = emptyList()
            )

            override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)
                setContentView(R.layout.activity_import_export)
                supportActionBar?.title = getString(R.string.app_name)
                supportActionBar?.setDisplayShowTitleEnabled(true)
                supportActionBar?.elevation = 0f

                isExportMode = intent.getBooleanExtra("mode", true)
                titleText = findViewById(R.id.titleText)
                supportActionBar?.setDisplayHomeAsUpEnabled(true)
                supportActionBar?.title = if (isExportMode) "Экспорт данных" else "Импорт данных"

                try {
                    treeContainer = findViewById(R.id.treeContainer) ?: throw IllegalStateException("treeContainer not found")
                    btnExport = findViewById(R.id.btnExport) ?: throw IllegalStateException("btnExport not found")
                    btnImport = findViewById(R.id.btnImport) ?: throw IllegalStateException("btnImport not found")
                    progressBar = findViewById(R.id.progressBar) ?: throw IllegalStateException("progressBar not found")
                    statusText = findViewById(R.id.statusText) ?: throw IllegalStateException("statusText not found")

                    isExportMode = intent.getBooleanExtra("mode", true)
                    btnExport.visibility = if (isExportMode) View.VISIBLE else View.GONE
                    btnImport.visibility = if (!isExportMode) View.VISIBLE else View.GONE

                    if (isExportMode) {
                        titleText.text = "Выберите данные для экспорта:"
                        loadData()
                        setupTreeUI()
                        statusText.text = "Выберите объекты для экспорта"
                    } else {
                        titleText.text = "Выберите данные для импорта:"
                        statusText.text = "Выберите файл для импорта"
                    }

                    btnExport.setOnClickListener {
                        vibrate()
                        if (checkStoragePermission()) {
                            showExportOptions()
                        }
                    }

                    btnImport.setOnClickListener {
                        vibrate()
                        if (checkStoragePermission()) {
                            startFilePicker()
                            statusText.text = ""
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка инициализации", e)
                    Toast.makeText(this, "Ошибка инициализации: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
            private fun vibrate(duration: Long = 50) {
                try {
                    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                        vibratorManager.defaultVibrator
                    } else {
                        @Suppress("DEPRECATION")
                        getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(duration)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error vibrating device", e)
                }
            }
            private fun loadData() {
                val sharedPref = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

                objects = sharedPref.getString(PREFS_OBJECTS_KEY, "[]")?.let {
                    gson.fromJson<List<ObjectConfig>>(it, object : TypeToken<List<ObjectConfig>>() {}.type)
                } ?: emptyList()

                devices = sharedPref.getString(PREFS_DEVICES_KEY, "[]")?.let {
                    gson.fromJson<List<DeviceConfig>>(it, object : TypeToken<List<DeviceConfig>>() {}.type)
                } ?: emptyList()

                buttonConfigs = sharedPref.getString(PREFS_BUTTONS_KEY, "[]")?.let {
                    gson.fromJson<List<ButtonConfig>>(it, object : TypeToken<List<ButtonConfig>>() {}.type)
                } ?: emptyList()
            }
            private fun startFilePicker() {
                try {
                    Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                        putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                            "application/json",
                            "text/xml",  // Добавляем тип для XML файлов
                            "application/xml"  // Альтернативный тип для XML
                        ))
                    }.let {
                        startActivityForResult(it, REQUEST_CODE_IMPORT)
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Ошибка выбора файла: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    Log.e(TAG, "Ошибка выбора файла", e)
                }
            }

            private fun importDataFromUri(uri: Uri) {
                showProgress("Импорт данных...")

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val fileName = DocumentFile.fromSingleUri(this@ImportExportActivity, uri)?.name ?: ""
                        val fileContent = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                            ?: throw IOException("Не удалось прочитать файл")

                        val parsedData = when {
                            fileName.endsWith(".xml") -> parseXmlToExportData(fileContent)
                            fileName.endsWith(".json") -> gson.fromJson(fileContent, ExportData::class.java)
                                ?: throw IllegalArgumentException("Неверный формат JSON")
                            else -> throw IllegalArgumentException("Неподдерживаемый формат файла")
                        }

                        // Валидация данных
                        if (parsedData.objects.isEmpty() && parsedData.devices.isEmpty() && parsedData.buttons.isEmpty()) {
                            throw IllegalArgumentException("Файл не содержит данных для импорта")
                        }

                        withContext(Dispatchers.Main) {
                            hideProgress()
                            showImportSelectionDialog(parsedData)
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            hideProgress()
                            statusText.text = "Ошибка импорта"
                            Toast.makeText(
                                this@ImportExportActivity,
                                "Ошибка импорта: ${e.localizedMessage}",
                                Toast.LENGTH_LONG
                            ).show()
                            Log.e(TAG, "Ошибка импорта", e)
                        }
                    }
                }
            }

            private fun showImportSelectionDialog(data: ExportData) {
                vibrate()
                val dialogView = layoutInflater.inflate(R.layout.dialog_import_selection, null)
                val treeContainer = dialogView.findViewById<LinearLayout>(R.id.importTreeContainer)
                val countTextView = dialogView.findViewById<TextView>(R.id.importCountText)
                val selectAllCheckbox = dialogView.findViewById<CheckBox>(R.id.selectAllCheckbox)
                val deselectAllCheckbox = dialogView.findViewById<CheckBox>(R.id.deselectAllCheckbox)

                selectAllCheckbox.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        vibrate()
                        deselectAllCheckbox.isChecked = false
                        selectAllImportItems(treeContainer, true)
                        updateImportCount(treeContainer, countTextView, data)
                    }
                }

                deselectAllCheckbox.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        vibrate()
                        selectAllCheckbox.isChecked = false
                        selectAllImportItems(treeContainer, false)
                        updateImportCount(treeContainer, countTextView, data)
                    }
                }

                treeContainer.removeAllViews()
                setupImportTreeUI(treeContainer, data, countTextView)

                val dialog = AlertDialog.Builder(this)
                    .setView(dialogView)
                    .setPositiveButton("Импортировать") { _, _ ->
                        vibrate()
                        confirmAndImport(getFilteredImportData(treeContainer, data))
                    }
                    .setNegativeButton("Отмена") { _, _ -> vibrate() }
                    .create()

                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
                dialog.setCustomTitle(TextView(this).apply {
                    text = "Выберите данные для импорта:"
                    textSize = 18f
                    setPadding(32, 32, 32, 16)
                    gravity = Gravity.CENTER
                })

                updateImportCount(treeContainer, countTextView, data)
                dialog.show()
            }

            private fun selectAllItems(select: Boolean) {
                for (i in 0 until treeContainer.childCount) {
                    val child = treeContainer.getChildAt(i)

                    when {
                        // Главный чекбокс "Выбрать всё" - пропускаем
                        i == 0 -> continue

                        // Обычные CheckBox (непривязанные устройства и кнопки)
                        child is CheckBox -> {
                            child.isChecked = select
                        }

                        // Контейнеры объектов
                        child is LinearLayout -> {
                            val objectCheckBox = child.getChildAt(0) as? CheckBox ?: continue
                            objectCheckBox.isChecked = select

                            val devicesLayout = child.getChildAt(1) as? LinearLayout ?: continue
                            for (j in 0 until devicesLayout.childCount) {
                                val deviceContainer = devicesLayout.getChildAt(j) as? LinearLayout ?: continue
                                val deviceCheckBox = deviceContainer.getChildAt(0) as? CheckBox ?: continue
                                deviceCheckBox.isChecked = select

                                val buttonsLayout = deviceContainer.getChildAt(1) as? LinearLayout ?: continue
                                for (k in 0 until buttonsLayout.childCount) {
                                    val buttonCheckBox = buttonsLayout.getChildAt(k) as? CheckBox ?: continue
                                    buttonCheckBox.isChecked = select
                                }
                            }
                        }
                    }
                }
            }
            private fun selectAllImportItems(container: LinearLayout, select: Boolean) {
                for (i in 0 until container.childCount) {
                    val objectContainer = container.getChildAt(i) as? LinearLayout ?: continue
                    val objectCheckBox = objectContainer.getChildAt(0) as? CheckBox ?: continue
                    objectCheckBox.isChecked = select

                    val devicesLayout = objectContainer.getChildAt(1) as? LinearLayout ?: continue
                    for (j in 0 until devicesLayout.childCount) {
                        val deviceContainer = devicesLayout.getChildAt(j) as? LinearLayout ?: continue
                        val deviceCheckBox = deviceContainer.getChildAt(0) as? CheckBox ?: continue
                        deviceCheckBox.isChecked = select

                        val buttonsLayout = deviceContainer.getChildAt(1) as? LinearLayout ?: continue
                        for (k in 0 until buttonsLayout.childCount) {
                            val buttonCheckBox = buttonsLayout.getChildAt(k) as? CheckBox ?: continue
                            buttonCheckBox.isChecked = select
                        }
                    }
                }
            }

            private fun setupImportTreeUI(container: LinearLayout, data: ExportData, countTextView: TextView) {
                container.removeAllViews()

                if (data.objects.isEmpty() && data.devices.isEmpty() && data.buttons.isEmpty()) {
                    container.addView(TextView(this).apply {
                        text = "Нет данных для импорта"
                        setPadding(32, 16, 16, 16)
                    })
                    return
                }

                val checkChangeListener = CompoundButton.OnCheckedChangeListener { _, _ ->
                    updateImportCount(container, countTextView, data)
                }

                // Секция объектов
                if (data.objects.isNotEmpty()) {
                    val objectsHeader = TextView(this).apply {
                        text = "Объекты и привязанные устройства/кнопки"
                        setPadding(16, 16, 16, 8)
                        textSize = 16f
                    }
                    container.addView(objectsHeader)

                    data.objects.forEach { obj ->
                        val objectContainer = LinearLayout(this).apply {
                            orientation = LinearLayout.VERTICAL
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                setMargins(0, 8, 0, 16)
                            }
                        }

                        val objectCheckBox = CheckBox(this).apply {
                            text = obj.name
                            tag = "obj_${obj.id}"
                            isChecked = true
                            setPadding(32, 12, 16, 12)
                            setOnCheckedChangeListener { buttonView, isChecked ->
                                vibrate()
                                val devicesLayout = objectContainer.getChildAt(1) as? LinearLayout
                                devicesLayout?.visibility = if (isChecked) View.VISIBLE else View.GONE
                                updateImportCount(container, countTextView, data)
                            }
                            setOnTouchListener { v, event ->
                                when (event.action) {
                                    MotionEvent.ACTION_DOWN -> {
                                        v.isPressed = true
                                        v.animate()
                                            .scaleX(0.95f)
                                            .scaleY(0.95f)
                                            .setDuration(100)
                                            .start()
                                        true
                                    }
                                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
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

                        val devicesLayout = LinearLayout(this).apply {
                            orientation = LinearLayout.VERTICAL
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                setMargins(64, 0, 0, 0)
                            }
                            visibility = View.VISIBLE
                        }

                        data.devices.filter { device ->
                            data.buttons.any { it.objectId == obj.id && it.deviceId == device.id }
                        }.forEach { device ->
                            val deviceContainer = LinearLayout(this).apply {
                                orientation = LinearLayout.VERTICAL
                                layoutParams = LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                ).apply {
                                    setMargins(0, 8, 0, 8)
                                }
                            }

                            val deviceCheckBox = CheckBox(this).apply {
                                text = device.name
                                tag = "${obj.id}_${device.id}"
                                isChecked = true
                                setPadding(64, 8, 16, 8)
                                setOnCheckedChangeListener(checkChangeListener)
                                setOnTouchListener { v, event ->
                                    when (event.action) {
                                        MotionEvent.ACTION_DOWN -> {
                                            v.isPressed = true
                                            v.animate()
                                                .scaleX(0.95f)
                                                .scaleY(0.95f)
                                                .setDuration(100)
                                                .start()
                                            true
                                        }
                                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
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

                            val buttonsLayout = LinearLayout(this).apply {
                                orientation = LinearLayout.VERTICAL
                                layoutParams = LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                ).apply {
                                    setMargins(96, 0, 0, 0)
                                }
                                visibility = View.VISIBLE
                            }

                            data.buttons.filter { it.objectId == obj.id && it.deviceId == device.id }.forEach { btn ->
                                buttonsLayout.addView(CheckBox(this).apply {
                                    text = btn.name
                                    tag = "${obj.id}_${device.id}_${btn.id}"
                                    isChecked = true
                                    setPadding(96, 8, 16, 8)
                                    setOnCheckedChangeListener(checkChangeListener)
                                    setOnTouchListener { v, event ->
                                        when (event.action) {
                                            MotionEvent.ACTION_DOWN -> {
                                                v.isPressed = true
                                                v.animate()
                                                    .scaleX(0.95f)
                                                    .scaleY(0.95f)
                                                    .setDuration(100)
                                                    .start()
                                                true
                                            }
                                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
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
                                })
                            }

                            deviceContainer.addView(deviceCheckBox)
                            deviceContainer.addView(buttonsLayout)
                            devicesLayout.addView(deviceContainer)
                        }

                        objectContainer.addView(objectCheckBox)
                        objectContainer.addView(devicesLayout)
                        container.addView(objectContainer)
                    }
                }

                // Секция непривязанных устройств
                val unboundDevices = data.devices.filter { device ->
                    data.buttons.none { it.deviceId == device.id } ||
                            (data.objects.isEmpty() && data.buttons.isEmpty()) // Показываем все устройства если нет объектов и кнопок
                }

                if (unboundDevices.isNotEmpty()) {
                    val devicesHeader = TextView(this).apply {
                        text = "Непривязанные устройства"
                        setPadding(16, 16, 16, 8)
                        textSize = 16f
                    }
                    container.addView(devicesHeader)

                    unboundDevices.forEach { device ->
                        container.addView(CheckBox(this).apply {
                            text = device.name
                            tag = "device_${device.id}"
                            isChecked = true
                            setPadding(32, 8, 16, 8)
                            setOnCheckedChangeListener(checkChangeListener)
                            setOnTouchListener { v, event ->
                                when (event.action) {
                                    MotionEvent.ACTION_DOWN -> {
                                        v.isPressed = true
                                        v.animate()
                                            .scaleX(0.95f)
                                            .scaleY(0.95f)
                                            .setDuration(100)
                                            .start()
                                        true
                                    }
                                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
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
                        })
                    }
                }

                // Секция непривязанных кнопок
                val unboundButtons = data.buttons.filter { btn ->
                    data.objects.none { it.id == btn.objectId } || data.devices.none { it.id == btn.deviceId }
                }

                if (unboundButtons.isNotEmpty()) {
                    val buttonsHeader = TextView(this).apply {
                        text = "Непривязанные кнопки"
                        setPadding(16, 16, 16, 8)
                        textSize = 16f
                    }
                    container.addView(buttonsHeader)

                    unboundButtons.forEach { btn ->
                        container.addView(CheckBox(this).apply {
                            text = btn.name
                            tag = "button_${btn.id}"
                            isChecked = true
                            setPadding(32, 8, 16, 8)
                            setOnCheckedChangeListener(checkChangeListener)
                            setOnTouchListener { v, event ->
                                when (event.action) {
                                    MotionEvent.ACTION_DOWN -> {
                                        v.isPressed = true
                                        v.animate()
                                            .scaleX(0.95f)
                                            .scaleY(0.95f)
                                            .setDuration(100)
                                            .start()
                                        true
                                    }
                                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
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
                        })
                    }
                }
            }

            private fun updateImportCount(container: LinearLayout, countTextView: TextView, data: ExportData) {
                val filteredData = getFilteredImportData(container, data)
                countTextView.text = "Выбрано: объектов - ${filteredData.objects.size}, устройств - ${filteredData.devices.size}, кнопок - ${filteredData.buttons.size}"
            }

            private fun getFilteredImportData(container: LinearLayout, data: ExportData): ExportData {
                val selectedObjectIds = mutableListOf<String>()
                val selectedDeviceIds = mutableListOf<String>()
                val selectedButtonIds = mutableListOf<String>()

                for (i in 0 until container.childCount) {
                    val view = container.getChildAt(i)

                    // Пропускаем заголовки (TextView)
                    if (view is TextView) continue

                    if (view is CheckBox) {
                        when {
                            view.tag?.toString()?.startsWith("obj_") == true && view.isChecked -> {
                                view.tag?.toString()?.substringAfter("obj_")?.let { selectedObjectIds.add(it) }
                            }
                            view.tag?.toString()?.startsWith("device_") == true && view.isChecked -> {
                                view.tag?.toString()?.substringAfter("device_")?.let { selectedDeviceIds.add(it) }
                            }
                            view.tag?.toString()?.startsWith("button_") == true && view.isChecked -> {
                                view.tag?.toString()?.substringAfter("button_")?.let { selectedButtonIds.add(it) }
                            }
                        }
                    } else if (view is LinearLayout) {
                        // Обработка контейнеров объектов
                        for (j in 0 until view.childCount) {
                            val child = view.getChildAt(j)
                            if (child is CheckBox && child.tag?.toString()?.startsWith("obj_") == true && child.isChecked) {
                                child.tag?.toString()?.substringAfter("obj_")?.let { selectedObjectIds.add(it) }

                                // Получаем контейнер устройств (следующий элемент после CheckBox)
                                if (j + 1 < view.childCount) {
                                    val devicesLayout = view.getChildAt(j + 1) as? LinearLayout
                                    devicesLayout?.let { processDevicesLayout(it, selectedDeviceIds, selectedButtonIds) }
                                }
                            }
                        }
                    }
                }

                return ExportData(
                    objects = data.objects.filter { it.id in selectedObjectIds },
                    devices = data.devices.filter { device ->
                        device.id in selectedDeviceIds ||
                                (selectedObjectIds.isEmpty() && selectedButtonIds.isEmpty()) // Если не выбраны объекты и кнопки, но есть устройства
                    },
                    buttons = data.buttons.filter { btn ->
                        btn.id in selectedButtonIds ||
                                (btn.objectId in selectedObjectIds) ||
                                (btn.deviceId in selectedDeviceIds)
                    }
                )
            }

            private fun confirmAndImport(data: ExportData) {
                vibrate()
                if (data.objects.isEmpty() && data.devices.isEmpty() && data.buttons.isEmpty()) {
                    Toast.makeText(this, "Не выбрано элементов для импорта", Toast.LENGTH_SHORT).show()
                    return
                }

                val message = buildString {
                    append("Будет импортировано:")
                    if (data.objects.isNotEmpty()) append("\n• Объектов: ${data.objects.size}")
                    if (data.devices.isNotEmpty()) append("\n• Устройств: ${data.devices.size}")
                    if (data.buttons.isNotEmpty()) append("\n• Кнопок: ${data.buttons.size}")
                    append("\n\nПродолжить?")
                }

                AlertDialog.Builder(this)
                    .setTitle("Подтверждение импорта")
                    .setMessage(message)
                    .setPositiveButton("Да") { _, _ ->
                        vibrate()
                        performImport(data)
                    }
                    .setNegativeButton("Нет") { _, _ -> vibrate() }
                    .show()
            }

            private fun performImport(data: ExportData) {
                showProgress("Импорт данных...")

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        saveImportedData(data)

                        withContext(Dispatchers.Main) {
                            hideProgress()
                            statusText.text = "Импорт завершен успешно"
                            Toast.makeText(this@ImportExportActivity, "Импорт завершен", Toast.LENGTH_LONG).show()
                            setResult(RESULT_OK)
                            finish()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            hideProgress()
                            statusText.text = "Ошибка импорта"
                            Toast.makeText(this@ImportExportActivity, "Ошибка импорта: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            Log.e(TAG, "Ошибка импорта", e)
                        }
                    }
                }
            }

            private fun processDevicesLayout(devicesLayout: LinearLayout,
                                             selectedDeviceIds: MutableList<String>,
                                             selectedButtonIds: MutableList<String>) {
                for (k in 0 until devicesLayout.childCount) {
                    val deviceContainer = devicesLayout.getChildAt(k) as? LinearLayout ?: continue

                    for (l in 0 until deviceContainer.childCount) {
                        val child = deviceContainer.getChildAt(l)
                        if (child is CheckBox) {
                            when {
                                child.tag?.toString()?.contains("_") == true && child.isChecked -> {
                                    // Обработка device_ и button_ тегов
                                    val parts = child.tag.toString().split("_")
                                    when {
                                        parts.size == 2 -> selectedDeviceIds.add(parts[1]) // device
                                        parts.size == 3 -> selectedButtonIds.add(parts[2]) // button
                                    }
                                }
                            }
                        } else if (child is LinearLayout) {
                            // Обработка контейнера кнопок
                            for (m in 0 until child.childCount) {
                                val button = child.getChildAt(m) as? CheckBox ?: continue
                                if (button.isChecked && button.tag?.toString()?.contains("_") == true) {
                                    val parts = button.tag.toString().split("_")
                                    if (parts.size == 3) {
                                        selectedButtonIds.add(parts[2])
                                    }
                                }
                            }
                        }
                    }
                }
            }

            private fun saveImportedData(data: ExportData) {
                val sharedPref = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                val editor = sharedPref.edit()

                try {
                    val existingObjects = sharedPref.getString(PREFS_OBJECTS_KEY, "[]")?.let {
                        gson.fromJson<List<ObjectConfig>>(it, object : TypeToken<List<ObjectConfig>>() {}.type)
                    } ?: emptyList()

                    val existingDevices = sharedPref.getString(PREFS_DEVICES_KEY, "[]")?.let {
                        gson.fromJson<List<DeviceConfig>>(it, object : TypeToken<List<DeviceConfig>>() {}.type)
                    } ?: emptyList()

                    val existingButtons = sharedPref.getString(PREFS_BUTTONS_KEY, "[]")?.let {
                        gson.fromJson<List<ButtonConfig>>(it, object : TypeToken<List<ButtonConfig>>() {}.type)
                    } ?: emptyList()

                    // Объединяем данные, сохраняя порядок и избегая дубликатов
                    val updatedObjects = (existingObjects + data.objects)
                        .distinctBy { it.id }
                        .sortedBy { it.order }

                    val updatedDevices = (existingDevices + data.devices)
                        .distinctBy { it.id }

                    val updatedButtons = (existingButtons + data.buttons)
                        .distinctBy { it.id }
                        .sortedBy { it.order }

                    editor.apply {
                        putString(PREFS_OBJECTS_KEY, gson.toJson(updatedObjects))
                        putString(PREFS_DEVICES_KEY, gson.toJson(updatedDevices))
                        putString(PREFS_BUTTONS_KEY, gson.toJson(updatedButtons))
                        apply()
                    }

                    Log.d(TAG, "Импортировано: ${data.objects.size} объектов, ${data.devices.size} устройств, ${data.buttons.size} кнопок")
                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка сохранения данных", e)
                    throw e
                }
            }

            private fun showExportOptions() {
                vibrate()
                try {
                    val selectedObjects = getSelectedObjects()
                    val selectedDevices = getSelectedDevices()
                    val selectedButtons = getSelectedButtons()

                    // Проверяем, что выбрано хотя бы что-то (объекты, устройства или кнопки)
                    if (selectedObjects.isEmpty() && selectedDevices.isEmpty() && selectedButtons.isEmpty()) {
                        Toast.makeText(this, "Выберите хотя бы один элемент для экспорта", Toast.LENGTH_SHORT).show()
                        return
                    }

                    AlertDialog.Builder(this)
                        .setTitle("Формат экспорта")
                        .setItems(arrayOf("JSON", "XML")) { _, which ->
                            vibrate()
                            selectedExportFormat = when (which) {
                                0 -> "json"
                                1 -> "xml"
                                else -> "json"
                            }
                            showExportActionDialog()
                        }
                        .setOnCancelListener {
                            Log.d(TAG, "Диалог выбора формата отменён")
                        }
                        .show()
                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка при показе диалога экспорта", e)
                    Toast.makeText(this, "Ошибка: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }

            private fun prepareExportData(saveToDownloads: Boolean = false) {
                val selectedObjects = getSelectedObjects()
                val selectedDevices = getSelectedDevices()
                val selectedButtons = getSelectedButtons()

                // Проверка, что выбрано хотя бы что-то
                if (selectedObjects.isEmpty() && selectedDevices.isEmpty() && selectedButtons.isEmpty()) {
                    Toast.makeText(this, "Не выбрано ни одного элемента для экспорта", Toast.LENGTH_SHORT).show()
                    return
                }

                val exportData = ExportData(
                    objects = objects.filter { it.id in selectedObjects }.sortedBy { it.order },
                    devices = devices.filter { device ->
                        device.id in selectedDevices ||
                                buttonConfigs.any { btn ->
                                    btn.deviceId == device.id &&
                                            (btn.id in selectedButtons || btn.objectId in selectedObjects)
                                }
                    },
                    buttons = buttonConfigs.filter { btn ->
                        btn.id in selectedButtons ||
                                (btn.objectId in selectedObjects &&
                                        (btn.deviceId in selectedDevices || selectedDevices.isEmpty()))
                    }.sortedBy { it.order }
                )

                when (selectedExportFormat) {
                    "json" -> exportToJsonFile(exportData, saveToDownloads)
                    "xml" -> exportToXmlFile(exportData, saveToDownloads)
                }
            }

            private fun exportToJsonFile(exportData: ExportData, saveToDownloads: Boolean) {
                showProgress("Экспорт данных...")

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                        val fileName = "sms_app_export_$timestamp.json"
                        val exportDir = getExportDir()
                        val exportFile = File(exportDir, fileName).apply {
                            writeText(gson.toJson(exportData))
                        }

                        withContext(Dispatchers.Main) {
                            hideProgress()
                            statusText.text = "Экспорт завершен успешно"
                            if (saveToDownloads) {
                                Toast.makeText(
                                    this@ImportExportActivity,
                                    "Файл сохранен: ${exportFile.absolutePath}",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                shareExportedFile(exportFile)
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            handleExportError("Ошибка экспорта: ${e.localizedMessage}")
                        }
                    }
                }
            }


            private fun exportToXmlFile(exportData: ExportData, saveToDownloads: Boolean) {
                showProgress("Экспорт данных...")

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val xml = buildXmlFromExportData(exportData)
                        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                        val exportFile = File(getExportDir(), "sms_app_export_$timestamp.xml").apply {
                            writeText(xml)
                        }

                        withContext(Dispatchers.Main) {
                            hideProgress()
                            statusText.text = "Экспорт завершен успешно"
                            if (saveToDownloads) {
                                Toast.makeText(
                                    this@ImportExportActivity,
                                    "Файл сохранен в Загрузки",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                shareExportedFile(exportFile)
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            handleExportError("Ошибка экспорта XML: ${e.localizedMessage}")
                        }
                    }
                }
            }

            private fun getExportDir(): File {
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).apply {
                        mkdirs()
                    }
                } else {
                    File(Environment.getExternalStorageDirectory(), "Download").apply {
                        mkdirs()
                    }
                }
            }

            private fun shareExportedFile(file: File) {
                try {
                    val fileUri = FileProvider.getUriForFile(this, PROVIDER_AUTHORITY, file)
                    Intent(Intent.ACTION_SEND).apply {
                        putExtra(Intent.EXTRA_STREAM, fileUri)
                        type = when (selectedExportFormat) {
                            "json" -> "application/json"
                            "xml" -> "application/xml"
                            else -> "*/*"
                        }
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }.let {
                        startActivityForResult(Intent.createChooser(it, "Поделиться экспортом"), REQUEST_CODE_SHARE)
                    }
                } catch (e: Exception) {
                    handleExportError("Ошибка при отправке файла: ${e.localizedMessage}")
                }
            }

            private fun handleExportError(message: String) {
                hideProgress()
                statusText.text = "Ошибка экспорта"
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                Log.e(TAG, message)
            }

            private fun escapeXml(input: String): String {
                return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&apos;")
            }

            private fun checkStoragePermission(): Boolean {
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (Environment.isExternalStorageManager()) {
                        true
                    } else {
                        Log.d(TAG, "Запрос разрешения на доступ ко всем файлам (Android 11+)")
                        Toast.makeText(this, "Требуется разрешение на доступ ко всем файлам", Toast.LENGTH_LONG).show()
                        requestAllFilesAccess()
                        false
                    }
                } else {
                    val hasPermission = (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
                    if (!hasPermission) {
                        Log.d(TAG, "Запрос разрешения WRITE_EXTERNAL_STORAGE")
                        ActivityCompat.requestPermissions(
                            this,
                            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                            STORAGE_PERMISSION_CODE
                        )
                    }
                    hasPermission
                }
            }

            private fun requestAllFilesAccess(): Boolean {
                return try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                    false
                } catch (e: Exception) {
                    false
                }
            }

            private fun checkReadWritePermission(): Boolean {
                val writePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                val readPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)

                if (writePermission == PackageManager.PERMISSION_GRANTED &&
                    readPermission == PackageManager.PERMISSION_GRANTED) {
                    return true
                }

                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ),
                    STORAGE_PERMISSION_CODE
                )
                return false
            }

            override fun onRequestPermissionsResult(
                requestCode: Int,
                permissions: Array<out String>,
                grantResults: IntArray
            ) {
                super.onRequestPermissionsResult(requestCode, permissions, grantResults)
                when (requestCode) {
                    STORAGE_PERMISSION_CODE -> {
                        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                            if (isExportMode) showExportOptions() else startFilePicker()
                        } else {
                            Toast.makeText(this, "В разрешении отказано", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            private fun setupTreeUI() {
                treeContainer.removeAllViews()

                if (objects.isEmpty() && devices.isEmpty() && buttonConfigs.isEmpty()) {
                    statusText.text = "Нет данных для экспорта"
                    return
                }

                // Добавляем главный чекбокс "Выбрать всё"
                val masterCheckBox = CheckBox(this).apply {
                    text = "Выбрать всё"
                    setPadding(16, 16, 16, 16)
                    setOnCheckedChangeListener { _, isChecked ->
                        selectAllItems(isChecked)
                    }
                }
                treeContainer.addView(masterCheckBox)

                // Секция объектов
                if (objects.isNotEmpty()) {
                    val objectsHeader = TextView(this).apply {
                        text = "Объекты и привязанные устройства/кнопки"
                        setPadding(16, 16, 16, 8)
                        textSize = 16f
                    }
                    treeContainer.addView(objectsHeader)

                    objects.forEach { obj ->
                        val objectContainer = LinearLayout(this).apply {
                            orientation = LinearLayout.VERTICAL
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                setMargins(0, 8, 0, 16)
                            }
                        }

                        val objectCheckBox = CheckBox(this).apply {
                            text = obj.name
                            tag = "obj_${obj.id}"
                            setPadding(32, 16, 16, 16)
                            setOnCheckedChangeListener { _, isChecked ->
                                val devicesLayout = objectContainer.getChildAt(1) as? LinearLayout
                                devicesLayout?.visibility = if (isChecked) View.VISIBLE else View.GONE
                            }
                        }

                        val devicesLayout = LinearLayout(this).apply {
                            orientation = LinearLayout.VERTICAL
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                setMargins(64, 0, 0, 0)
                            }
                            visibility = View.GONE
                        }

                        // Добавляем устройства, связанные с этим объектом
                        devices.filter { device ->
                            buttonConfigs.any { it.objectId == obj.id && it.deviceId == device.id }
                        }.forEach { device ->
                            val deviceContainer = LinearLayout(this).apply {
                                orientation = LinearLayout.VERTICAL
                                layoutParams = LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                ).apply {
                                    setMargins(0, 8, 0, 8)
                                }
                            }

                            val deviceCheckBox = CheckBox(this).apply {
                                text = device.name
                                tag = "${obj.id}_${device.id}"
                                setPadding(64, 8, 16, 8)
                                setOnCheckedChangeListener { _, isChecked ->
                                    val buttonsLayout = deviceContainer.getChildAt(1) as? LinearLayout
                                    buttonsLayout?.visibility = if (isChecked) View.VISIBLE else View.GONE
                                }
                            }

                            val buttonsLayout = LinearLayout(this).apply {
                                orientation = LinearLayout.VERTICAL
                                layoutParams = LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                ).apply {
                                    setMargins(96, 0, 0, 0)
                                }
                                visibility = View.GONE
                            }

                            // Добавляем кнопки для этого устройства
                            buttonConfigs.filter { it.objectId == obj.id && it.deviceId == device.id }.forEach { btn ->
                                buttonsLayout.addView(CheckBox(this).apply {
                                    text = btn.name
                                    tag = "${obj.id}_${device.id}_${btn.id}"
                                    setPadding(96, 8, 16, 8)
                                })
                            }

                            deviceContainer.addView(deviceCheckBox)
                            deviceContainer.addView(buttonsLayout)
                            devicesLayout.addView(deviceContainer)
                        }

                        objectContainer.addView(objectCheckBox)
                        objectContainer.addView(devicesLayout)
                        treeContainer.addView(objectContainer)
                    }
                }

                // Секция непривязанных устройств
                val unboundDevices = devices.filter { device ->
                    buttonConfigs.none { it.deviceId == device.id }
                }

                if (unboundDevices.isNotEmpty()) {
                    val devicesHeader = TextView(this).apply {
                        text = "Непривязанные устройства"
                        setPadding(16, 16, 16, 8)
                        textSize = 16f
                    }
                    treeContainer.addView(devicesHeader)

                    unboundDevices.forEach { device ->
                        treeContainer.addView(CheckBox(this).apply {
                            text = device.name
                            tag = "device_${device.id}"
                            setPadding(32, 8, 16, 8)
                        })
                    }
                }

                // Секция непривязанных кнопок
                val unboundButtons = buttonConfigs.filter { btn ->
                    objects.none { it.id == btn.objectId } || devices.none { it.id == btn.deviceId }
                }

                if (unboundButtons.isNotEmpty()) {
                    val buttonsHeader = TextView(this).apply {
                        text = "Непривязанные кнопки"
                        setPadding(16, 16, 16, 8)
                        textSize = 16f
                    }
                    treeContainer.addView(buttonsHeader)

                    unboundButtons.forEach { btn ->
                        treeContainer.addView(CheckBox(this).apply {
                            text = btn.name
                            tag = "button_${btn.id}"
                            setPadding(32, 8, 16, 8)
                        })
                    }
                }
            }
            private fun buildXmlFromExportData(exportData: ExportData): String {
                return buildString {
                    append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                    append("<SMSAppData>\n")

                    append("  <objects>\n")
                    exportData.objects.sortedBy { it.order }.forEach { obj ->
                        append("""
                <object>
                  <id>${escapeXml(obj.id)}</id>
                  <name>${escapeXml(obj.name)}</name>
                  <order>${obj.order}</order>
                </object>
            """.trimIndent() + "\n")
                    }
                    append("  </objects>\n")

                    append("  <devices>\n")
                    exportData.devices.forEach { device ->
                        append("""
                <device>
                  <id>${escapeXml(device.id)}</id>
                  <name>${escapeXml(device.name)}</name>
                  <phoneNumber>${escapeXml(device.phoneNumber)}</phoneNumber>
                  <simSlot>${device.simSlot}</simSlot>
                </device>
            """.trimIndent() + "\n")
                    }
                    append("  </devices>\n")

                    append("  <buttons>\n")
                    exportData.buttons.sortedBy { it.order }.forEach { btn ->
                        append("""
                <button>
                  <id>${escapeXml(btn.id)}</id>
                  <name>${escapeXml(btn.name)}</name>
                  <message>${escapeXml(btn.message)}</message>
                  <deviceId>${escapeXml(btn.deviceId)}</deviceId>
                  <objectId>${escapeXml(btn.objectId)}</objectId>
                  <order>${btn.order}</order>
                  <requireConfirmation>${btn.requireConfirmation}</requireConfirmation>
                </button>
            """.trimIndent() + "\n")
                    }
                    append("  </buttons>\n")

                    append("</SMSAppData>")
                }
            }

            private fun parseXmlToExportData(xmlContent: String): ExportData {
                if (xmlContent.length > 5_000_000) { // ~5MB limit
                    throw IllegalArgumentException("Файл слишком большой для импорта")
                }

                val objects = mutableListOf<ObjectConfig>()
                val devices = mutableListOf<DeviceConfig>()
                val buttons = mutableListOf<ButtonConfig>()

                val objectPattern = "<object>.*?<id>(.*?)</id>.*?<name>(.*?)</name>.*?(?:<order>(.*?)</order>)?.*?</object>".toRegex(RegexOption.DOT_MATCHES_ALL)
                val devicePattern = "<device>.*?<id>(.*?)</id>.*?<name>(.*?)</name>.*?<phoneNumber>(.*?)</phoneNumber>.*?(?:<simSlot>(.*?)</simSlot>)?.*?</device>".toRegex(RegexOption.DOT_MATCHES_ALL)
                val buttonPattern = "<button>.*?<id>(.*?)</id>.*?<name>(.*?)</name>.*?<message>(.*?)</message>.*?<deviceId>(.*?)</deviceId>.*?<objectId>(.*?)</objectId>.*?(?:<order>(.*?)</order>)?.*?(?:<requireConfirmation>(.*?)</requireConfirmation>)?.*?</button>".toRegex(RegexOption.DOT_MATCHES_ALL)

                objectPattern.findAll(xmlContent).forEach { match ->
                    objects.add(ObjectConfig(
                        id = match.groupValues[1].ifEmpty { "" },
                        name = match.groupValues[2].ifEmpty { "" },
                        order = match.groupValues[3].toIntOrNull() ?: 0
                    ))
                }

                devicePattern.findAll(xmlContent).forEach { match ->
                    devices.add(DeviceConfig(
                        id = match.groupValues[1].ifEmpty { "" },
                        name = match.groupValues[2].ifEmpty { "" },
                        phoneNumber = match.groupValues[3].ifEmpty { "" },
                        simSlot = match.groupValues[4].toIntOrNull() ?: 0
                    ))
                }

                buttonPattern.findAll(xmlContent).forEach { match ->
                    buttons.add(ButtonConfig(
                        id = match.groupValues[1].ifEmpty { "" },
                        name = match.groupValues[2].ifEmpty { "" },
                        message = match.groupValues[3].ifEmpty { "" },
                        deviceId = match.groupValues[4].ifEmpty { "" },
                        objectId = match.groupValues[5].ifEmpty { "" },
                        order = match.groupValues[6].toIntOrNull() ?: 0,
                        requireConfirmation = match.groupValues[7].toBoolean()
                    ))
                }

                if (objects.size + devices.size + buttons.size > MAX_IMPORT_ITEMS) {
                    throw IllegalArgumentException("Слишком много элементов для импорта (максимум $MAX_IMPORT_ITEMS)")
                }
                return ExportData(objects, devices, buttons)
            }

            private fun getSelectedObjects(): List<String> {
                val selectedObjects = mutableListOf<String>()

                for (i in 1 until treeContainer.childCount) {
                    val child = treeContainer.getChildAt(i)

                    when {
                        // Обычные CheckBox для объектов
                        child is CheckBox && child.tag?.toString()?.startsWith("obj_") == true && child.isChecked -> {
                            child.tag?.toString()?.substringAfter("obj_")?.let { selectedObjects.add(it) }
                        }

                        // Контейнеры объектов (LinearLayout)
                        child is LinearLayout -> {
                            val objectCheckBox = child.getChildAt(0) as? CheckBox ?: continue
                            if (objectCheckBox.isChecked && objectCheckBox.tag?.toString()?.startsWith("obj_") == true) {
                                objectCheckBox.tag?.toString()?.substringAfter("obj_")?.let { selectedObjects.add(it) }
                            }
                        }
                    }
                }

                return selectedObjects
            }

            private fun getSelectedDevices(): List<String> {
                val selectedDevices = mutableListOf<String>()

                for (i in 1 until treeContainer.childCount) {
                    val child = treeContainer.getChildAt(i)

                    when {
                        // Обычные CheckBox для устройств (непривязанные)
                        child is CheckBox && child.tag?.toString()?.startsWith("device_") == true && child.isChecked -> {
                            child.tag?.toString()?.substringAfter("device_")?.let { selectedDevices.add(it) }
                        }

                        // Контейнеры объектов (LinearLayout)
                        child is LinearLayout -> {
                            val objectCheckBox = child.getChildAt(0) as? CheckBox ?: continue
                            if (objectCheckBox.isChecked) {
                                val devicesLayout = child.getChildAt(1) as? LinearLayout ?: continue

                                for (j in 0 until devicesLayout.childCount) {
                                    val deviceContainer = devicesLayout.getChildAt(j) as? LinearLayout ?: continue
                                    val deviceCheckBox = deviceContainer.getChildAt(0) as? CheckBox ?: continue

                                    if (deviceCheckBox.isChecked) {
                                        deviceCheckBox.tag?.toString()?.let {
                                            if (it.contains("_")) {
                                                val parts = it.split("_")
                                                if (parts.size >= 2) {
                                                    selectedDevices.add(parts[1])
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                return selectedDevices
            }


            private fun showExportActionDialog() {
                vibrate()
                try {
                    AlertDialog.Builder(this)
                        .setTitle("Выберите действие")
                        .setItems(arrayOf("Сохранить в Загрузки", "Отправить файл")) { _, which ->
                            vibrate()
                            if (which == 0) {
                                prepareExportData(saveToDownloads = true)
                            } else {
                                prepareExportData(saveToDownloads = false)
                            }
                        }
                        .setOnDismissListener {
                            Log.d(TAG, "Диалог экспорта закрыт")
                        }
                        .show()
                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка при показе диалога экспорта", e)
                    Toast.makeText(this, "Ошибка: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }

            private fun getSelectedButtons(): List<String> {
                val selectedButtons = mutableListOf<String>()

                for (i in 1 until treeContainer.childCount) {
                    val child = treeContainer.getChildAt(i)

                    when {
                        // Обычные CheckBox для кнопок (непривязанные)
                        child is CheckBox && child.tag?.toString()?.startsWith("button_") == true && child.isChecked -> {
                            child.tag?.toString()?.substringAfter("button_")?.let { selectedButtons.add(it) }
                        }

                        // Контейнеры объектов (LinearLayout)
                        child is LinearLayout -> {
                            val objectCheckBox = child.getChildAt(0) as? CheckBox ?: continue
                            if (objectCheckBox.isChecked) {
                                val devicesLayout = child.getChildAt(1) as? LinearLayout ?: continue

                                for (j in 0 until devicesLayout.childCount) {
                                    val deviceContainer = devicesLayout.getChildAt(j) as? LinearLayout ?: continue
                                    val deviceCheckBox = deviceContainer.getChildAt(0) as? CheckBox ?: continue

                                    if (deviceCheckBox.isChecked) {
                                        val buttonsLayout = deviceContainer.getChildAt(1) as? LinearLayout ?: continue

                                        for (k in 0 until buttonsLayout.childCount) {
                                            val buttonCheckBox = buttonsLayout.getChildAt(k) as? CheckBox ?: continue
                                            if (buttonCheckBox.isChecked) {
                                                buttonCheckBox.tag?.toString()?.let {
                                                    if (it.contains("_")) {
                                                        val parts = it.split("_")
                                                        if (parts.size == 3) {
                                                            selectedButtons.add(parts[2])
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                return selectedButtons
            }
            private fun showProgress(message: String) {
                progressBar.visibility = View.VISIBLE
                statusText.text = message
            }

            private fun hideProgress() {
                progressBar.visibility = View.GONE
            }

            override fun onSupportNavigateUp(): Boolean {
                onBackPressed()
                return true
            }

            override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
                super.onActivityResult(requestCode, resultCode, data)
                when (requestCode) {
                    REQUEST_CODE_IMPORT -> {
                        if (resultCode == Activity.RESULT_OK) {
                            data?.data?.let { uri ->
                                importDataFromUri(uri)
                            }
                        }
                    }
                    REQUEST_CODE_SHARE -> {
                        // Обработка после отправки файла
                        statusText.text = if (resultCode == Activity.RESULT_OK) {
                            "Файл отправлен"
                        } else {
                            "Отправка отменена"
                        }
                    }
                }
            }
        }