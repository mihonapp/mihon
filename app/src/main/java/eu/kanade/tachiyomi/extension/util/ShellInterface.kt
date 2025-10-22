/*
This file contains code derived from the project "Install with Options"
(https://github.com/zacharee/InstallWithOptions), licensed under the MIT License:

Copyright (c) 2024 Zachary Wander

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

Modifications Copyright (c) 2025 Mihon Open Source Project
Licensed under the Apache License, Version 2.0
You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
*/

package eu.kanade.tachiyomi.extension.util

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL
import android.content.res.AssetFileDescriptor
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.UserHandle
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.IShellInterface
import eu.kanade.tachiyomi.extension.installer.ACTION_INSTALL_RESULT
import rikka.shizuku.SystemServiceHelper
import java.io.OutputStream
import kotlin.system.exitProcess

class ShellInterface : IShellInterface.Stub() {

    private val context = createContext()
    private val userId = UserHandle::class.java
        .getMethod("myUserId")
        .invoke(null) as Int
    private val packageName = BuildConfig.APPLICATION_ID

    @SuppressLint("PrivateApi")
    override fun install(apk: AssetFileDescriptor) {
        val pmInterface = Class.forName("android.content.pm.IPackageManager\$Stub")
            .getMethod("asInterface", IBinder::class.java)
            .invoke(null, SystemServiceHelper.getSystemService("package"))

        val packageInstaller = Class.forName("android.content.pm.IPackageManager")
            .getMethod("getPackageInstaller")
            .invoke(pmInterface)

        val params = PackageInstaller.SessionParams(MODE_FULL_INSTALL).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                setPackageSource(PackageInstaller.PACKAGE_SOURCE_STORE)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                setInstallerPackageName(packageName)
            }
        }

        val sessionId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            packageInstaller::class.java.getMethod(
                "createSession",
                PackageInstaller.SessionParams::class.java,
                String::class.java,
                String::class.java,
                Int::class.java,
            ).invoke(packageInstaller, params, packageName, packageName, userId) as Int
        } else {
            packageInstaller::class.java.getMethod(
                "createSession",
                PackageInstaller.SessionParams::class.java,
                String::class.java,
                Int::class.java,
            ).invoke(packageInstaller, params, packageName, userId) as Int
        }

        val session = packageInstaller::class.java
            .getMethod("openSession", Int::class.java)
            .invoke(packageInstaller, sessionId)

        (
            session::class.java.getMethod(
                "openWrite",
                String::class.java,
                Long::class.java,
                Long::class.java,
            ).invoke(session, "extension", 0L, apk.length) as ParcelFileDescriptor
            ).let { fd ->
            val revocable = Class.forName("android.os.SystemProperties")
                .getMethod("getBoolean", String::class.java, Boolean::class.java)
                .invoke(null, "fw.revocable_fd", false) as Boolean

            if (revocable) {
                ParcelFileDescriptor.AutoCloseOutputStream(fd)
            } else {
                Class.forName("android.os.FileBridge\$FileBridgeOutputStream")
                    .getConstructor(ParcelFileDescriptor::class.java)
                    .newInstance(fd) as OutputStream
            }
        }
            .use { output ->
                apk.createInputStream().use { input -> input.copyTo(output) }
            }

        val statusIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_INSTALL_RESULT).setPackage(packageName),
            PendingIntent.FLAG_MUTABLE,
        )

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
            session::class.java.getMethod("commit", IntentSender::class.java, Boolean::class.java)
                .invoke(session, statusIntent.intentSender, false)
        } else {
            session::class.java.getMethod("commit", IntentSender::class.java)
                .invoke(session, statusIntent.intentSender)
        }
    }

    override fun destroy() {
        exitProcess(0)
    }

    @SuppressLint("PrivateApi")
    private fun createContext(): Context {
        val activityThread = Class.forName("android.app.ActivityThread")
        val systemMain = activityThread.getMethod("systemMain").invoke(null)
        val systemContext = activityThread.getMethod("getSystemContext").invoke(systemMain) as Context

        val shellUserHandle = UserHandle::class.java
            .getConstructor(Int::class.java)
            .newInstance(userId)

        val shellContext = systemContext::class.java.getMethod(
            "createPackageContextAsUser",
            String::class.java,
            Int::class.java,
            UserHandle::class.java,
        ).invoke(
            systemContext,
            "com.android.shell",
            Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY,
            shellUserHandle,
        ) as Context

        return shellContext.createPackageContext("com.android.shell", 0)
    }
}
