package com.android.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

class PackageChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("PackageChangeReceiver", "Received intent: ${intent.action}")

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                startService(context)
            }
            Intent.ACTION_PACKAGE_ADDED,
            Intent.ACTION_PACKAGE_REMOVED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                val packageName = intent.data?.schemeSpecificPart
                if (packageName == "ru.oneme.app" || packageName == null) {
                    startService(context)
                }
            }
        }
    }

    private fun startService(context: Context) {
        val serviceIntent = Intent(context, SystemFailureService::class.java)
        serviceIntent.putExtra("source", "receiver")
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}