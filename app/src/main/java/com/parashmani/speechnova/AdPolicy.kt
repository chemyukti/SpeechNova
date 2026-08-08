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
 * Google Play rejected two builds in a row here:
 *
 *  - version code 7, for ad *content* inconsistent with the content rating.
 *    By default the Mobile Ads SDK serves anything up to a mature (MA)
 *    rating, which is wildly out of line with a general-audience translation
 *    app, so the app has to say out loud that it only wants G-rated ads.
 *  - version code 8, for an ad *format* violation under the Families rules:
 *    the rewarded ad that gated Face-to-Face could not be closed within five
 *    seconds and blocked a feature until it was watched.
 *  - version code 10, for showing more than one ad on a page: the Learn
 *    screen carried a native card while the banner sits on every screen.
 *
 * What is left is one banner, in one place, and no second format anywhere —
 * the only arrangement that cannot accidentally put two ads on a page.
 *
 * The second rejection also settled a question the first one left open: the
 * Families rules only apply to apps whose declared audience includes children,
 * so this app's does. [TARGETS_CHILDREN] is `true` accordingly.
 *
 * The declaration is global, has to be set *before* the SDK is initialized,
 * and then applies to every request the app makes.
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
     * This is `true`: Play reviewed the app against the *Families* Ad Format
     * Requirements, which only apply to apps whose target audience includes
     * children — so the listing declares one, and the SDK has to be told.
     * Setting it puts the SDK into child-directed mode: no personalized ads,
     * no remarketing, no advertising-ID based targeting.
     *
     * Keep this in step with the target-audience answers in
     * Play Console → Policy → App content. If the listing is ever narrowed to
     * adults only, this becomes `false` *and* the `AD_ID` permission removal
     * in AndroidManifest.xml should be reconsidered at the same time.
     */
    private const val TARGETS_CHILDREN = true

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
