/*
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  SPEECHNOVA — AD CONTENT POLICY                                          ║
 * ║                                                                          ║
 * ║  Developed by : Mr.Parashmani                                            ║
 * ║  Copyright    : © 2025 Parashmani. All rights reserved.                  ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 */

package com.parashmani.speechnova

import android.content.Context
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration

/**
 * Single place where the rules for *what kind of ads SpeechNova is allowed to
 * show* are declared.
 *
 * Google Play rejected version code 7 with "the ad content in your app is not
 * consistent with the app's content rating". By default the Mobile Ads SDK will
 * serve anything up to a mature (MA) rating, which is wildly out of line with
 * a general-audience translation app — so the app has to say out loud that it
 * only wants general-audience ads. That declaration is global, it has to be set
 * *before* the SDK is initialized, and it then applies to every banner, native
 * and rewarded request the app makes.
 *
 * Note that the code side is only half of the fix: sensitive ad categories are
 * blocked publisher-side in the AdMob console. See PLAY_POLICY.md for the
 * console checklist that goes with this file.
 */
object AdPolicy {

    /**
     * Whether the Play Store listing declares an audience that includes
     * children (under 13, or the local equivalent).
     *
     * SpeechNova is a general-audience translation tool, so this is `false`:
     * we tell the SDK explicitly that the app is *not* child-directed rather
     * than leaving it unspecified, because "unspecified" leaves the decision
     * to the ad network.
     *
     * If the Play Console target-audience answers ever change to include
     * children, flip this to `true` — that switches the SDK into
     * child-directed mode (no personalized ads, no remarketing), which is
     * what the Families Policy Requirements demand.
     */
    private const val TARGETS_CHILDREN = false

    /**
     * Applies the content restrictions and then starts the Mobile Ads SDK.
     * Call this once, from [MainActivity.onCreate], instead of calling
     * `MobileAds.initialize` directly — the ordering matters, a request
     * configuration set after initialization does not affect ads already
     * in flight.
     */
    fun initialize(context: Context) {
        MobileAds.setRequestConfiguration(buildRequestConfiguration())
        MobileAds.initialize(context) {}
    }

    /**
     * Every ad request in the app is built here so none of them can quietly
     * skip the policy above.
     */
    fun request(): AdRequest = AdRequest.Builder().build()

    private fun buildRequestConfiguration(): RequestConfiguration =
        RequestConfiguration.Builder()
            // "G" = suitable for general audiences, the strictest filter the
            // SDK offers. This is what keeps the served ads in line with an
            // "Everyone" content rating.
            .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_G)
            .setTagForChildDirectedTreatment(
                if (TARGETS_CHILDREN) {
                    RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE
                } else {
                    RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE
                }
            )
            // Google's guidance is that this and the child-directed tag must
            // never both be true, so it stays unspecified: when the app is
            // marked child-directed the child-directed rules already apply,
            // and when it is not, the consent flow decides.
            .setTagForUnderAgeOfConsent(
                RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_UNSPECIFIED
            )
            .build()
}
