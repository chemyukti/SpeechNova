# Play Console — resolving the "Ad Content" rejection

Google Play rejected **version code 7** with:

> The ad content in your app is not consistent with the app's content rating.

By default the Google Mobile Ads SDK will serve ads up to a **mature (MA)**
rating. SpeechNova is a general-audience translation tool, so the ads it was
showing could be rated far above the app itself — that mismatch is the
violation.

The fix has a code half and a console half. Both are needed; shipping only one
will get the next build rejected the same way.

## Code side — done in this branch

`app/src/main/java/com/parashmani/speechnova/AdPolicy.kt` declares the policy
and starts the SDK:

- `setMaxAdContentRating(MAX_AD_CONTENT_RATING_G)` — only general-audience ads
  are eligible to serve. This is the setting that directly answers the
  rejection.
- `setTagForChildDirectedTreatment(...FALSE)` — SpeechNova's Play listing does
  not declare children as a target audience, so the app says so explicitly
  rather than leaving it for the ad network to guess. If the target-audience
  answers in the console ever change to include under-13 users, flip
  `AdPolicy.TARGETS_CHILDREN` to `true`.
- `setTagForUnderAgeOfConsent(...UNSPECIFIED)` — Google's guidance is that this
  and the child-directed tag must never both be true.

The configuration is applied **before** `MobileAds.initialize`, because a
request configuration set afterwards does not affect requests already in
flight. Every banner, native and rewarded request in the app is built through
`AdPolicy.request()` so none of them can bypass it.

`versionCode` is bumped to **8** — a rejected version code can never be
re-uploaded.

## Console side — must be done by hand before resubmitting

These live in the AdMob and Play consoles and cannot be set from code:

1. **AdMob → Blocking controls → Sensitive categories.** Block the categories
   that are inconsistent with the app's rating, in particular:
   gambling/betting, alcohol, tobacco, dating, sexually suggestive content,
   weapons, "get rich quick" schemes, and drugs/supplements.
   Apply this per app *and* at the account level, so new ad units inherit it.
2. **AdMob → Blocking controls → Ad content rating.** Set the account/app
   maximum to **G** so the server-side ceiling matches the SDK request.
3. **Play Console → Policy → App content → Ads.** Confirm the app is declared
   as containing ads.
4. **Play Console → Policy → App content → Target audience and content.**
   Make sure the declared target audience matches `AdPolicy.TARGETS_CHILDREN`.
   If children are included, the app enters the Families programme and the
   stricter Families ad rules apply.
5. **Retake the content rating questionnaire** if any answer no longer matches
   the app — Play's notice offers this as the alternative route to
   consistency.

## Verifying before you upload

- Ad inspector (shake the device with a debug build, or
  `MobileAds.openAdInspector`) shows the request configuration that was
  actually applied — check that the max content rating reads `G`.
- Give the AdMob blocking-control changes a few hours to propagate before
  spot-checking live ads.
