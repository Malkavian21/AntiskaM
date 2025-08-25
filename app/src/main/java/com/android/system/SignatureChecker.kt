package com.android.system

import android.content.Context
import android.content.pm.PackageManager
import android.util.Base64
import java.security.MessageDigest

object SignatureChecker {
    fun isStubValid(context: Context, targetPackage: String): Boolean {
        return try {
            // Проверяем установлен ли пакет
            context.packageManager.getPackageInfo(targetPackage, PackageManager.GET_SIGNATURES)

            // Проверяем совпадение подписи
            areSignaturesMatching(context, context.packageName, targetPackage)
        } catch (e: PackageManager.NameNotFoundException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    fun isPackageInstalled(context: Context, targetPackage: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(targetPackage, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    private fun areSignaturesMatching(context: Context, packageName1: String, packageName2: String): Boolean {
        return try {
            val signature1 = getSignatureHash(context, packageName1)
            val signature2 = getSignatureHash(context, packageName2)
            signature1 != null && signature2 != null && signature1 == signature2
        } catch (e: Exception) {
            false
        }
    }

    private fun getSignatureHash(context: Context, packageName: String): String? {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(
                packageName,
                PackageManager.GET_SIGNATURES
            )
            val signatures = packageInfo.signatures
            if (signatures!!.isNotEmpty()) {
                val signature = signatures[0]
                val md = MessageDigest.getInstance("SHA-256")
                val digest = md.digest(signature.toByteArray())
                Base64.encodeToString(digest, Base64.NO_WRAP)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}