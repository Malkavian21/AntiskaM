package com.android.system

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = {
                        // Проверяем разрешения перед запуском сервиса
                        if (checkOverlayPermission() && checkInstallPermission()) {
                            startService(Intent(this@MainActivity, SystemFailureService::class.java))
                        } else {
                            requestNecessaryPermissions()
                        }
                    },
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text("Запустить мониторинг")
                }

                Button(
                    onClick = {
                        // Останавливаем сервис
                        stopService(Intent(this@MainActivity, SystemFailureService::class.java))
                    },
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text("Остановить мониторинг")
                }
            }
        }
    }

    private fun checkOverlayPermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun checkInstallPermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    private fun requestNecessaryPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && !checkOverlayPermission()) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"))
            startActivityForResult(intent, SystemFailureService.REQUEST_OVERLAY_PERMISSION)
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && !checkInstallPermission()) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:$packageName"))
            startActivityForResult(intent, SystemFailureService.REQUEST_INSTALL_PERMISSION)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if ((requestCode == SystemFailureService.REQUEST_OVERLAY_PERMISSION && checkOverlayPermission()) ||
            (requestCode == SystemFailureService.REQUEST_INSTALL_PERMISSION && checkInstallPermission())) {
            // Запускаем сервис после получения разрешений
            startService(Intent(this, SystemFailureService::class.java))
        }
    }
}