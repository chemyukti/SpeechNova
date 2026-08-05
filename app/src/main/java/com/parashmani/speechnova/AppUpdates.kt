/*
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  SPEECHNOVA — IN-APP UPDATES                                             ║
 * ║                                                                          ║
 * ║  Developed by : Mr.Parashmani                                            ║
 * ║  Copyright    : © 2025 Parashmani. All rights reserved.                  ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 */

package com.parashmani.speechnova

import android.app.Activity
import android.util.Log
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * Offers the user a newer version from inside the app, instead of hoping they
 * notice it in the Play Store.
 *
 * A *flexible* update is used rather than an immediate one: the download runs
 * in the background while the app stays usable, and the user is only
 * interrupted at the very end to restart. Blocking someone out of a translator
 * until they update is the wrong trade — they may be standing in front of the
 * person they're trying to talk to.
 *
 * Everything here fails quietly. A build that isn't installed from Play (a
 * sideloaded debug APK, for instance) has no update service to talk to, and
 * that must not look like a broken app.
 */
class AppUpdates(private val activity: Activity) {

    companion object {
        private const val REQUEST_CODE = 4711
        private const val TAG = "SpeechNova"
    }

    private val manager: AppUpdateManager by lazy {
        AppUpdateManagerFactory.create(activity)
    }

    /** Set once an update has finished downloading and is waiting to install. */
    var onReadyToInstall: (() -> Unit)? = null

    private val installListener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) {
            onReadyToInstall?.invoke()
        }
    }

    /**
     * Asks Play whether a newer version exists and, if so, starts the download.
     * Safe to call on every launch.
     */
    fun check() {
        runCatching {
            manager.registerListener(installListener)
            manager.appUpdateInfo
                .addOnSuccessListener { info ->
                    val available =
                        info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                    if (available && info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
                        runCatching {
                            manager.startUpdateFlowForResult(
                                info,
                                AppUpdateType.FLEXIBLE,
                                activity,
                                REQUEST_CODE
                            )
                        }.onFailure {
                            Log.w(TAG, "Could not start the update flow", it)
                        }
                    }
                }
                .addOnFailureListener {
                    // No Play service to ask — a sideloaded or debug build.
                    Log.i(TAG, "Update check unavailable: ${it.message}")
                }
        }.onFailure {
            Log.i(TAG, "Update check skipped: ${it.message}")
        }
    }

    /**
     * Picks up an update that finished downloading while the app was in the
     * background, so the "restart to finish" prompt isn't lost.
     */
    fun resume() {
        runCatching {
            manager.appUpdateInfo.addOnSuccessListener { info ->
                if (info.installStatus() == InstallStatus.DOWNLOADED) {
                    onReadyToInstall?.invoke()
                }
            }
        }
    }

    /** Restarts the app to install the downloaded update. */
    fun completeUpdate() {
        runCatching { manager.completeUpdate() }
            .onFailure { Log.w(TAG, "Could not complete the update", it) }
    }

    /** Call from the activity's onDestroy so the listener isn't leaked. */
    fun dispose() {
        runCatching { manager.unregisterListener(installListener) }
    }
}
