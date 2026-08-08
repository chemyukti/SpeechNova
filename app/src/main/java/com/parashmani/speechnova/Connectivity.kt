/*
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  SPEECHNOVA — IS THERE A NETWORK RIGHT NOW?                              ║
 * ║                                                                          ║
 * ║  Developed by : Mr.Parashmani                                            ║
 * ║  Copyright    : © 2025 Parashmani. All rights reserved.                  ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 */

package com.parashmani.speechnova

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

/**
 * Whether the phone currently has a usable internet connection.
 *
 * SpeechNova's promise is that it keeps working without one, and several
 * things have to be decided differently when it's gone:
 *
 *  - Speech recognition must be asked for *offline* explicitly, or Android
 *    sends the audio to Google's servers and simply fails when it can't.
 *  - A voice that needs the network to speak has to be passed over, however
 *    good it sounds when there is one.
 *
 * Note this reports whether a network is *validated*, not merely connected —
 * a hotel wifi that hasn't been logged into is worse than no network at all,
 * because requests hang rather than failing fast.
 */
fun isOnline(context: Context): Boolean = runCatching {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return false
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}.getOrDefault(false)

/**
 * Watches for the connection coming and going, calling back with the new state.
 *
 * The moment a connection drops is the only moment the offline setup advice is
 * actually useful — telling someone to download a language pack while they
 * still have signal is easy to ignore, and telling them after they have
 * already tapped a dead microphone is too late.
 *
 * Returns a function that unregisters the listener; the caller must call it.
 */
fun watchConnectivity(context: Context, onChange: (Boolean) -> Unit): () -> Unit {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return {}
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = onChange(true)
        override fun onLost(network: Network) = onChange(false)
        override fun onCapabilitiesChanged(
            network: Network,
            caps: NetworkCapabilities
        ) {
            // A connected-but-unvalidated network (hotel wifi behind a login)
            // is worse than none, so report on validation rather than presence.
            onChange(
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            )
        }
    }
    return runCatching {
        cm.registerDefaultNetworkCallback(callback)
        { runCatching { cm.unregisterNetworkCallback(callback) } }
    }.getOrDefault({})
}
