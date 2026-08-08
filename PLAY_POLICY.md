# Play Console — ads compliance for SpeechNova

Three rejections, all about ads:

| Version code | Issue | Cause |
| --- | --- | --- |
| 7 | Ad Content — inconsistent with the app's content rating | SDK was free to serve up to a mature (MA) rating |
| 8 | Families Ad Format Requirements — unclosable ads that interfere with app use | The rewarded ad gating Face-to-Face |
| 10 | Families Ad Format Requirements — multiple ads per page | The Learn screen carried a native card while the banner sits on every screen |

The version 8 notice is the important one: the **Families Policy Requirements
apply to this app**, which means its Play listing declares an audience that
includes children. That is a much stricter regime than the content-rating fix
alone assumed.

## What the Families rules forbid, and what this app now does

| Rule | Status |
| --- | --- |
| No full-screen ad that can't be closed within 5 seconds | **Rewarded ad removed entirely** — it is unclosable for its full run by design |
| Ads must not interfere with app use | Face-to-Face is no longer gated behind an ad; nothing in the app is |
| G-rated ad content only | `setMaxAdContentRating(MAX_AD_CONTENT_RATING_G)` |
| No behavioural targeting / remarketing to children | `setTagForChildDirectedTreatment(...TRUE)` |
| No advertising ID transmitted | `AD_ID` permission removed in the manifest |
| One ad per page | **Native ad removed entirely.** One banner, in one place, is the only arrangement that cannot accidentally put two ads on a page |
| Ads must be clearly distinguishable from app content | Prominent "ADVERTISEMENT" label above the banner |
| No design that produces inadvertent clicks | Close button moved off the ad and enlarged to 48dp; banner separated from the nav bar |
| Certified ads SDK | Google Mobile Ads, bumped 23.0.0 → 23.6.0 |

## Code side — done in this branch

`app/src/main/java/com/parashmani/speechnova/AdPolicy.kt` is the single place
the policy is declared, applied before `MobileAds.initialize` (a configuration
set afterwards does not affect requests already in flight). Every remaining ad
request is built through `AdPolicy.request()` so none can bypass it.

`AdPolicy.TARGETS_CHILDREN` is `true` and must stay in step with the
target-audience answers in Play Console → Policy → App content. If the listing
is ever narrowed to adults only, revisit the `AD_ID` removal at the same time.

`versionCode` is **11** — 7, 8 and 10 are all burnt and can never be re-uploaded.

## Console side — must be done by hand before resubmitting

None of this can be set from code:

1. **AdMob → Apps → SpeechNova → Ad units.** **Delete or pause the rewarded ad
   unit** (`ca-app-pub-8499432704301966/2705659156`). The app no longer
   requests it; leaving it live invites the same finding again.
2. **AdMob → Blocking controls → Sensitive categories.** Block the categories
   inconsistent with a children's audience — gambling/betting, alcohol,
   tobacco, dating, sexually suggestive content, weapons, drugs/supplements,
   "get rich quick" schemes. Set this at account level so new ad units inherit
   it.
3. **AdMob → Blocking controls → Ad content rating.** Set the account/app
   ceiling to **G**, matching what the SDK requests.
4. **AdMob → App settings → "Child-directed treatment" / "Ad content for
   families".** Mark the app as child-directed so serving is restricted
   server-side too, not only per request.
5. **Families Self-Certified Ads SDKs list.** Confirm the bundled Google Mobile
   Ads version is at or above the current listed minimum. The list sets a
   floor that moves over time — check it per release rather than assuming.
6. **Play Console → Policy → App content → Target audience and content.**
   Confirm the declared audience matches `TARGETS_CHILDREN = true`.
7. **Play Console → Policy → App content → Ads.** Confirm the app is declared
   as containing ads.
8. **Retake the content rating questionnaire** if any answer no longer matches
   the app.

## Verifying before upload

- Ad inspector (`MobileAds.openAdInspector`, or shake a debug build) shows the
  request configuration that actually applied — confirm max content rating
  reads `G` and child-directed reads true.
- Confirm `AD_ID` is absent from the merged manifest:
  `./gradlew :app:processReleaseManifest` then grep
  `app/build/intermediates/merged_manifest/release/AndroidManifest.xml`.
- Walk every screen and confirm nothing full-screen ever appears, and that
  Face-to-Face opens straight into the conversation UI with no gate.
- Give AdMob blocking-control changes a few hours to propagate before
  spot-checking live ads.


## Wanting more ad revenue without breaking this again

The app is already at the ceiling the Families rules allow: **one ad per page**,
and the banner is on every screen. There is no compliant way to add a second
ad anywhere. Three formats are worth naming explicitly so they aren't tried:

- **Interstitials** — technically permitted if closable after five seconds, but
  this app has already been rejected twice for ad *format*. A third attempt is
  not worth the review risk.
- **Rewarded ads** — unclosable for their full run by design. This is what got
  version 8 rejected.
- **App-open ads** — full screen before the app is usable. Exactly the
  "interferes with app use" pattern the notices describe.

What is left, and what has been done in code: the banner is now an **anchored
adaptive banner** rather than a fixed 320x50. It fills the device width, is
taller, and lets the network serve a better-paying creative — more revenue from
the same single impression.

Everything else is console-side:

1. **AdMob → Mediation.** Add networks so the slot is auctioned rather than
   filled by one buyer. This is the single biggest lever available.
2. **Check fill rate before assuming low revenue is a bug.** A child-directed,
   G-rated, non-personalised request has a much smaller pool of buyers than a
   default one. That is the cost of Families compliance, not a fault.
3. **Leave banner refresh at the AdMob default.** Refreshing faster than 30
   seconds violates AdMob policy and risks the account, not just a release.
