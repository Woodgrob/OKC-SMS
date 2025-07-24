package ru.okc.app

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Bundle
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.BroadcastReceiver
import android.app.PendingIntent
import android.content.IntentFilter
import android.content.Context
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.FrameLayout
import android.view.MotionEvent
import android.view.Gravity
import android.text.TextUtils
import android.graphics.Typeface
import android.graphics.Color
import android.widget.ImageButton
import android.widget.PopupMenu
import android.view.WindowManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.StaticLayout
import android.text.Layout
import androidx.core.widget.NestedScrollView


fun Context.vibrate(duration: Long = 50) {
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
        Log.e("Vibration", "Error vibrating device", e)
    }
}

class MainActivity : AppCompatActivity() {
    private lateinit var viewPager: ViewPager2
    private lateinit var pageIndicator: TabLayout
    private val objectButtonsMap = mutableMapOf<String, MutableList<ButtonConfig>>()
    private val gson = Gson()
    private var subscriptionManager: SubscriptionManager? = null
    private val LAST_PRESSED_PREFS = "LastPressedPrefs"
    private lateinit var smsSentReceiver: BroadcastReceiver

    companion object {
        private const val TAG = "MainActivity"
        private const val ITEMS_PER_PAGE = 6 // 2 столбца × 3 строки
        private const val HORIZONTAL_GRID_SPACING = 10 // Расстояние между столбцами в dp
        private const val VERTICAL_GRID_SPACING = 10   // Расстояние между строками в dp
        private const val BUTTON_HEIGHT = 100 // Высота кнопки в dp
    }
    private fun showMenu(anchor: View) {
        vibrate() // Добавляем вибрацию при открытии меню
        PopupMenu(this, anchor).apply {
            menuInflater.inflate(R.menu.main_menu, menu)
            setOnMenuItemClickListener { item ->
                vibrate() // Вибрация при выборе пункта меню
                onOptionsItemSelected(item)
            }
            show()
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            setContentView(R.layout.activity_main)

            // Инициализация ActionBar
//            supportActionBar?.apply {
//                title = getString(R.string.app_name)
//                setDisplayShowTitleEnabled(true)
//            }
           // Обработка клика по кнопке меню
            findViewById<ImageButton>(R.id.menuButton)?.setOnClickListener {
                showMenu(it)
            }


            // Инициализация ViewPager и TabLayout
            viewPager = findViewById(R.id.objectsViewPager)
                ?: throw IllegalStateException("ViewPager2 not found")
            pageIndicator = findViewById(R.id.pageIndicator)
                ?: throw IllegalStateException("TabLayout not found")

            // Инициализация SubscriptionManager
            subscriptionManager = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    getSystemService(SubscriptionManager::class.java)
                } else {
                    Log.w(TAG, "SubscriptionManager not supported")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "SubscriptionManager init error", e)
                null
            }

            // Проверка разрешений
            try {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                    != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.SEND_SMS),
                        Constants.SMS_PERMISSION_CODE
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Permission check error", e)
            }

            // Инициализация BroadcastReceiver (упрощенная версия)
            smsSentReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    Log.d(TAG, "SMS sent result: $resultCode")
                    when (resultCode) {
                        Activity.RESULT_OK -> {
                            // Только обновляем UI, сохранение уже сделано при нажатии
                            loadButtonConfigs()
                            setupViewPager()
                        }
                        else -> {
                            Log.e(TAG, "SMS sending failed with code: $resultCode")
                        }
                    }
                }
            }

            try {
                registerReceiver(smsSentReceiver, IntentFilter("SMS_SENT"))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register receiver", e)
            }

            // Основная инициализация
            loadButtonConfigs()
            setupViewPager()

            // Отображение версии приложения
            try {
                val versionName = packageManager.getPackageInfo(packageName, 0).versionName
                findViewById<TextView>(R.id.versionTextView)?.text = "Версия: $versionName"
            } catch (e: Exception) {
                Log.e(TAG, "Version info error", e)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Critical init error", e)
            Toast.makeText(this, "Приложение запущено с ограничениями", Toast.LENGTH_LONG).show()
            try {
                loadButtonConfigs()
                setupViewPager()
            } catch (e: Exception) {
                Log.e(TAG, "Fallback init failed", e)
            }
        }
    }

    // Не забываем отменить регистрацию в onDestroy
    override fun onDestroy() {
        super.onDestroy()
        try {
            if(::smsSentReceiver.isInitialized) {
                unregisterReceiver(smsSentReceiver)
            }
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Receiver not registered", e)
        }
    }
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }
    private val buttonConfigResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            if (result.resultCode == RESULT_OK) {
                loadButtonConfigs()
                setupViewPager()
                Toast.makeText(this, "Изменения в кнопках сохранены", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка обработки результата ButtonConfig", e)
            Toast.makeText(this, "Ошибка обновления кнопок", Toast.LENGTH_SHORT).show()
        }
    }

    private val objectManagementResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            if (result.resultCode == RESULT_OK) {
                loadButtonConfigs()
                setupViewPager()
                Toast.makeText(this, "Изменения в объектах сохранены", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка обработки результата ObjectManagement", e)
            Toast.makeText(this, "Ошибка обновления объектов", Toast.LENGTH_SHORT).show()
        }
    }

    private val importExportResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            loadButtonConfigs()
            setupViewPager()
            Toast.makeText(this, "Данные успешно обновлены", Toast.LENGTH_SHORT).show()
        }
    }

    private val deviceManagementResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            if (result.resultCode == RESULT_OK) {
                loadButtonConfigs()
                setupViewPager()
                Toast.makeText(this, "Изменения в устройствах сохранены", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка обработки результата DeviceManagement", e)
            Toast.makeText(this, "Ошибка обновления устройств", Toast.LENGTH_SHORT).show()
        }
    }

    private inner class GridSpacingItemDecoration(
        private val spanCount: Int,
        private val horizontalSpacing: Int,  // расстояние между столбцами
        private val verticalSpacing: Int,     // расстояние между строками
        private val includeEdge: Boolean
    ) : RecyclerView.ItemDecoration() {

        override fun getItemOffsets(
            outRect: Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State
        ) {
            val position = parent.getChildAdapterPosition(view)
            val column = position % spanCount

            if (includeEdge) {
                // Горизонтальные отступы (между столбцами)
                outRect.left = horizontalSpacing - column * horizontalSpacing / spanCount
                outRect.right = (column + 1) * horizontalSpacing / spanCount

                // Вертикальные отступы (между строками)
                outRect.bottom = verticalSpacing

                // Уменьшенный верхний отступ только для первой строки
                if (position < spanCount) {
                    outRect.top = 20.dpToPx() // Уменьшенное значение
                }
            } else {
                outRect.left = column * horizontalSpacing / spanCount
                outRect.right = horizontalSpacing - (column + 1) * horizontalSpacing / spanCount
                if (position >= spanCount) {
                    outRect.top = verticalSpacing
                }
            }
        }
    }

    private inner class ObjectsPagerAdapter(private val objectNames: List<String>) :
        RecyclerView.Adapter<ObjectsPagerAdapter.PageViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
            // Создаем RecyclerView для страницы
            val recyclerView = RecyclerView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                // Настраиваем GridLayoutManager с 2 колонками
                val gridLayoutManager = GridLayoutManager(context, 2).apply {
                    orientation = GridLayoutManager.VERTICAL
                }
                layoutManager = gridLayoutManager

                // Добавляем отступы между элементами
                addItemDecoration(
                    GridSpacingItemDecoration(
                        spanCount = 2,
                        horizontalSpacing = HORIZONTAL_GRID_SPACING.dpToPx(),
                        verticalSpacing = VERTICAL_GRID_SPACING.dpToPx(),
                        includeEdge = true
                    )
                )

                // Улучшаем производительность
                setHasFixedSize(true)
                itemAnimator = null
                overScrollMode = View.OVER_SCROLL_NEVER
            }

            return PageViewHolder(recyclerView)
        }

        override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
            val start = position * ITEMS_PER_PAGE
            val end = minOf(start + ITEMS_PER_PAGE, objectNames.size)
            val pageObjects = objectNames.subList(start, end)

            holder.recyclerView.adapter = ObjectsAdapter(pageObjects)
        }

        override fun getItemCount(): Int {
            return (objectNames.size + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE
        }

        inner class PageViewHolder(val recyclerView: RecyclerView) : RecyclerView.ViewHolder(recyclerView)
    }

    private inner class ObjectsAdapter(private val objectNames: List<String>) :
        RecyclerView.Adapter<ObjectsAdapter.ObjectViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ObjectViewHolder {
            val layout = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
            }

            val button = Button(parent.context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    BUTTON_HEIGHT.dpToPx()
                ).apply {
                    setPadding(16.dpToPx(), 24.dpToPx(), 16.dpToPx(), 24.dpToPx())
                }

                // Явные настройки текста
                setTextColor(Color.WHITE)
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setSingleLine(false)
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setLineSpacing(0f, 1.1f) // Добавляем межстрочный интервал

                // Используем ripple эффект из ресурсов
                background = ContextCompat.getDrawable(context, R.drawable.ripple_effect_green)

                // Восстанавливаем elevation для тени
                elevation = 4f
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    stateListAnimator = null // Можно использовать кастомный или оставить null
                }
            }
            layout.addView(button)

            val infoText = TextView(parent.context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 4.dpToPx()
                }
                setTextColor(Color.WHITE)
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(0, 4.dpToPx(), 0, 0)
                maxLines = 2
                setLineSpacing(0f, 1.1f) // добавляем межстрочный интервал
            }
            layout.addView(infoText)

            return ObjectViewHolder(layout, button, infoText)
        }

        override fun onBindViewHolder(holder: ObjectViewHolder, position: Int) {
            val objectName = objectNames[position]
            holder.button.text = objectName // Устанавливаем название объекта

            // Анимация нажатия (остается без изменений)
            holder.button.setOnTouchListener { v, event ->
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
                                showButtonsForObject(objectName)
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

            val objectId = getObjectIdByName(objectName)
            if (objectId != null) {
                val lastPressedInfo = getLastPressedInfo(objectId)
                if (lastPressedInfo != null) {
                    val (buttonName, timestamp) = lastPressedInfo
                    val dateTime = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
                        .format(Date(timestamp))
                    holder.infoText.text = "$dateTime\n$buttonName"
                } else {
                    holder.infoText.text = " "
                }
            } else {
                holder.infoText.text = ""
            }
        }

        override fun getItemCount(): Int = objectNames.size

        inner class ObjectViewHolder(
            val layout: LinearLayout,
            val button: Button,
            val infoText: TextView
        ) : RecyclerView.ViewHolder(layout)
    }

    private fun getObjectIdByName(objectName: String): String? {
        val sharedPref = getSharedPreferences("SMSAppPrefs", MODE_PRIVATE)
        val objectsJson = sharedPref.getString("objects", "[]") ?: "[]"
        Log.d(TAG, "Objects JSON: $objectsJson") // Логируем сырые данные

        val objects = gson.fromJson<List<ObjectConfig>>(
            objectsJson,
            object : TypeToken<List<ObjectConfig>>() {}.type
        ) ?: emptyList()

        val foundObject = objects.find { it.name == objectName }
        Log.d(TAG, "Searching for object: $objectName, found: ${foundObject?.id}")
        return foundObject?.id
    }

    private fun Int.dpToPx(): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        this.toFloat(),
        resources.displayMetrics
    ).toInt()

    private fun setupViewPager() {
        val objectNames = objectButtonsMap.keys.toList()
        viewPager.adapter = ObjectsPagerAdapter(objectNames)

        TabLayoutMediator(pageIndicator, viewPager) { tab, position ->
            tab.text = "Страница ${position + 1}"
        }.attach()
    }

    private fun checkSmsPermission() {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.SEND_SMS),
                    Constants.SMS_PERMISSION_CODE
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка проверки разрешений", e)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            Constants.SMS_PERMISSION_CODE -> {
                if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Без разрешения отправка SMS невозможна", Toast.LENGTH_LONG).show()
                }
            }
        }
    }


    override fun onResume() {
        super.onResume()
        loadButtonConfigs()
        setupViewPager()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        vibrate()
        return when (item.itemId) {
            R.id.menu_manage_buttons -> {
                startActivitySafely(ButtonManagementActivity::class.java, buttonConfigResult)
                true
            }
            R.id.menu_manage_objects -> {
                startActivitySafely(ObjectManagementActivity::class.java, objectManagementResult)
                true
            }
            R.id.menu_manage_devices -> {
                startActivitySafely(DeviceManagementActivity::class.java, deviceManagementResult)
                true
            }
            R.id.menu_export -> {
                val intent = Intent(this, ImportExportActivity::class.java).apply {
                    putExtra("mode", true)
                }
                importExportResult.launch(intent)
                true
            }
            R.id.menu_import -> {
                val intent = Intent(this, ImportExportActivity::class.java).apply {
                    putExtra("mode", false)
                }
                importExportResult.launch(intent)
                true
            }
            R.id.menu_about -> {
                showAboutDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun startActivitySafely(
        activityClass: Class<*>,
        resultLauncher: androidx.activity.result.ActivityResultLauncher<Intent>
    ) {
        try {
            val intent = Intent(this, activityClass)
            resultLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка запуска активности ${activityClass.simpleName}", e)
            throw e
        }
    }

    private fun loadButtonConfigs() {
        try {
            val sharedPref = getSharedPreferences("SMSAppPrefs", MODE_PRIVATE)

            // Загружаем объекты
            val objectsJson = sharedPref.getString("objects", "[]") ?: "[]"
            val objects = gson.fromJson<List<ObjectConfig>>(
                objectsJson,
                object : TypeToken<List<ObjectConfig>>() {}.type
            )?.sortedBy { it.order } ?: emptyList()

            // Загружаем конфигурации кнопок
            val configsJson = sharedPref.getString("buttonConfigs", "[]") ?: "[]"
            val configs = gson.fromJson<List<ButtonConfig>>(
                configsJson,
                object : TypeToken<List<ButtonConfig>>() {}.type
            ) ?: emptyList()

            // Очищаем и заполняем карту объектов и кнопок
            objectButtonsMap.clear()

            // Добавляем ВСЕ объекты, даже если у них нет кнопок
            objects.forEach { obj ->
                val buttonsForObject = configs
                    .filter { it.objectId == obj.id }
                    .sortedBy { it.order }
                objectButtonsMap[obj.name] = buttonsForObject.toMutableList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка загрузки конфигураций", e)
            Toast.makeText(this, "Ошибка загрузки данных", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showButtonsForObject(objectName: String) {
        try {
            // Получаем кнопки для объекта (может быть пустым списком)
            val buttons = objectButtonsMap[objectName]?.sortedBy { it.order } ?: run {
                Toast.makeText(this, "Нет привязанных кнопок!", Toast.LENGTH_SHORT).show()
                return
            }

            if (buttons.isEmpty()) {
                Toast.makeText(this, "Нет привязанных кнопок!", Toast.LENGTH_SHORT).show()
                return
            }

            val dialog = AlertDialog.Builder(this, R.style.CustomAlertDialogStyle)
                .setTitle("Объект: $objectName")
                .create()

            dialog.window?.attributes?.windowAnimations = R.style.DialogAnimation

            val scrollView = NestedScrollView(this).apply {
                overScrollMode = View.OVER_SCROLL_NEVER
            }
            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16.dpToPx(), 8.dpToPx(), 16.dpToPx(), 8.dpToPx())
            }

            buttons.forEachIndexed { index, buttonConfig ->
                val buttonContainer = FrameLayout(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = 8.dpToPx()
                    }
                    elevation = 4f
                    background = ContextCompat.getDrawable(context, R.drawable.button_container_background)
                }

                Button(this).apply {
                    text = "${index + 1}. ${buttonConfig.name}"
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        minimumHeight = 48.dpToPx()
                    }

                    setTextColor(Color.WHITE)
                    textSize = 16f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setSingleLine(false)
                    maxLines = Integer.MAX_VALUE
                    ellipsize = TextUtils.TruncateAt.END
                    setLineSpacing(0f, 1.1f)
                    background = ContextCompat.getDrawable(context, R.drawable.ripple_effect_green)
                    elevation = 4f
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        stateListAnimator = null
                    }

                    viewTreeObserver.addOnPreDrawListener {
                        val lineCount = if (text.isNotEmpty()) {
                            val paint = paint
                            val width = measuredWidth - paddingLeft - paddingRight
                            if (width > 0) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                                        .setAlignment(Layout.Alignment.ALIGN_CENTER)
                                        .setLineSpacing(0f, 1.1f)
                                        .setIncludePad(false)
                                        .build()
                                        .lineCount
                                } else {
                                    @Suppress("DEPRECATION")
                                    StaticLayout(
                                        text,
                                        paint,
                                        width,
                                        Layout.Alignment.ALIGN_CENTER,
                                        1.1f,
                                        0f,
                                        false
                                    ).lineCount
                                }
                            } else {
                                1
                            }
                        } else {
                            1
                        }

                        val newHeight = if (lineCount > 1) {
                            (48 + (lineCount - 1) * 20).dpToPx()
                        } else {
                            48.dpToPx()
                        }

                        if (height != newHeight) {
                            layoutParams.height = newHeight
                            requestLayout()
                        }
                        true
                    }

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
                                        vibrate()
                                        saveLastPressedInfo(buttonConfig.objectId, buttonConfig.name)
                                        Handler(Looper.getMainLooper()).post {
                                            loadButtonConfigs()
                                            setupViewPager()
                                        }

                                        // Проверяем requireConfirmation из текущей конфигурации
                                        if (buttonConfig.requireConfirmation) {
                                            showConfirmationDialog(buttonConfig) {
                                                sendSms(buttonConfig)
                                                dialog.dismiss()
                                            }
                                        } else {
                                            sendSms(buttonConfig)
                                            dialog.dismiss()
                                        }
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

                    buttonContainer.addView(this)
                }

                layout.addView(buttonContainer)

                if (index < buttons.size - 1) {
                    View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            1.dpToPx()
                        ).apply {
                            setMargins(0, 8.dpToPx(), 0, 8.dpToPx())
                        }
                        setBackgroundColor(ContextCompat.getColor(context, R.color.divider_color))
                        layout.addView(this)
                    }
                }
            }

            Button(this).apply {
                text = "Отмена"
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    topMargin = 8.dpToPx()
                    bottomMargin = 4.dpToPx()
                }
                setPadding(48.dpToPx(), 8.dpToPx(), 48.dpToPx(), 8.dpToPx())

                setTextColor(Color.WHITE)
                background = ContextCompat.getDrawable(context, R.drawable.ripple_effect_blue)

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
                                    dialog.dismiss()
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

                layout.addView(this)
            }

            scrollView.addView(layout)
            dialog.setView(scrollView)
            dialog.window?.setBackgroundDrawableResource(R.drawable.dialog_background_dark)

            dialog.show()
            dialog.window?.let {
                val displayMetrics = resources.displayMetrics
                val screenWidth = displayMetrics.widthPixels
                val dialogWidth = (screenWidth * 0.8).toInt()

                val layoutParams = WindowManager.LayoutParams()
                layoutParams.copyFrom(it.attributes)
                layoutParams.width = dialogWidth
                layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT
                it.attributes = layoutParams
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing buttons dialog", e)
            Toast.makeText(this, "Ошибка отображения кнопок", Toast.LENGTH_SHORT).show()
        }
    }
    private fun showConfirmationDialog(config: ButtonConfig, onConfirm: () -> Unit) {
        vibrate()
        AlertDialog.Builder(this, R.style.CustomAlertDialogStyle)
            .setTitle("Подтверждение отправки")
            .setMessage("Вы уверены, что хотите отправить команду \"${config.name}\"?")
            .setPositiveButton("Отправить") { _, _ ->
                onConfirm()
            }
            .setNegativeButton("Отмена", null)
            .create()
            .apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(
                        ContextCompat.getColor(this@MainActivity, R.color.green_500)
                    )
                    getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(
                        ContextCompat.getColor(this@MainActivity, R.color.green_500)
                    )
                }
                window?.setBackgroundDrawableResource(R.drawable.dialog_background_dark)
                show()
            }
    }
    private fun sendSms(config: ButtonConfig) {
        try {
            // Проверка разрешений
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
                checkSmsPermission()
                return
            }

            val sharedPref = getSharedPreferences("SMSAppPrefs", MODE_PRIVATE)

            // Получение устройства
            val devices = gson.fromJson<List<DeviceConfig>>(
                sharedPref.getString("devices", "[]"),
                object : TypeToken<List<DeviceConfig>>() {}.type
            ) ?: emptyList()

            val device = devices.find { it.id == config.deviceId } ?: run {
                Toast.makeText(this, "Устройство не найдено", Toast.LENGTH_LONG).show()
                return
            }

            val phoneNumber = device.phoneNumber.ifEmpty {
                Toast.makeText(this, "Номер не указан", Toast.LENGTH_LONG).show()
                return
            }

            val message = config.message.ifEmpty { "" }
            val simSlot = device.simSlot.coerceAtLeast(0)

            // Безопасная работа с SubscriptionManager
            val subscriptions = subscriptionManager?.activeSubscriptionInfoList ?: emptyList()

            if (subscriptions.size > simSlot) {
                val sentIntent = PendingIntent.getBroadcast(
                    this,
                    0,
                    Intent("SMS_SENT").apply {
                        putExtra("objectId", config.objectId)
                        putExtra("buttonName", config.name)
                    },
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )

                try {
                    SmsManager.getSmsManagerForSubscriptionId(subscriptions[simSlot].subscriptionId)
                        .sendTextMessage(phoneNumber, null, message, sentIntent, null)

                    Toast.makeText(this, "Отправка SMS...", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e(TAG, "SMS sending failed", e)
                    Toast.makeText(this, "Ошибка отправки", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, "SIM-карта недоступна", Toast.LENGTH_LONG).show()
            }

        } catch (e: Exception) {
            Log.e(TAG, "SMS error", e)
            Toast.makeText(this, "Ошибка: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }



    private fun saveLastPressedInfo(objectId: String, buttonName: String) {
        val sharedPref = getSharedPreferences(LAST_PRESSED_PREFS, MODE_PRIVATE)
        sharedPref.edit().apply {
            putString("last_pressed_$objectId", "$buttonName|${System.currentTimeMillis()}")
            apply()
        }
    }

    private fun getLastPressedInfo(objectId: String): Pair<String, Long>? {
        val sharedPref = getSharedPreferences(LAST_PRESSED_PREFS, MODE_PRIVATE)
        val value = sharedPref.getString("last_pressed_$objectId", null) ?: return null
        val parts = value.split("|")
        return if (parts.size == 2) {
            Pair(parts[0], parts[1].toLong())
        } else {
            null
        }
    }
    private fun showAboutDialog() {
        vibrate()
        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            "N/A"
        }

        AlertDialog.Builder(this, R.style.CustomAlertDialogStyle)
            .setTitle("О программе")
            .setMessage("""
            Версия приложения: $versionName
            
            Организатор Котортких сообщений - приложение для управления 
            устройствами через SMS команды.
            
            Это полностью бесплатное програмное обеспечение!
            Распространяется по лицензии GPLv3.
            
             Разработчики: Алексей М. , Евгений Г. 
             
                                2025 г. Саратов.
        """.trimIndent())
            .setPositiveButton("OK", null)
            .create()
            .apply {
                window?.setBackgroundDrawableResource(R.drawable.dialog_background_dark)
                show()
            }
    }
}