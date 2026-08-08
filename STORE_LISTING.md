# Play Store listing

Draft text plus the declarations Play requires. Everything here follows Google
Play's Metadata policy: no keyword stuffing, no ALL CAPS, no emoji in the
title, no "best" or "#1" or "free", no testimonials, no price or promo text,
and no claims the app cannot back up.

Check every claim against the build before you paste it in. If a feature is
cut, cut the line.

---

## App title

Limit: 30 characters.

```
SpeechNova: Voice Translator
```

28 characters. No emoji, no keywords bolted on, no superlatives.

**Do not use:** "Best Translator", "Free Voice Translator", "Translator All
Languages Offline Free". All of these break the Metadata policy.

## Short description

Limit: 80 characters. Shown under the title in search.

```
Speak or type to translate, and learn Indian alphabets letter by letter.
```

71 characters.

## Full description

Limit: 4000 characters. Draft below.

```
SpeechNova translates what you say, type, or photograph, and helps you learn
the alphabet of the language you are translating into.

TRANSLATE BY VOICE
Tap the microphone and speak. Your words are translated and read out loud.
Turn on "Speak several sentences" if you would rather say a few lines and
translate them together.

FACE TO FACE
Put the phone flat on the table between you and the person you are talking to.
It listens and translates continuously, with no buttons to press. Your words
appear on your side, the translation on theirs, the right way up for each of
you. Tap swap when it is their turn to speak.

TYPE OR PASTE
Paste a message, an email or a website into the Text screen. SpeechNova works
out which language it is on its own, then translates it.

SCAN PRINTED TEXT
Point the camera at a sign, a menu or a form and take a photo. The text is read
and translated. Available for Latin, Devanagari, Chinese, Japanese and Korean
scripts.

READY-MADE PHRASES
Sentences grouped by situation for travel, food, health and everyday talk, for
when you would rather not speak at all.

LEARN THE ALPHABET
See the letters of your language and tap any one to hear it. Practise words by
saying them out loud. For Hindi, Marathi and Bengali the app shows you syllable
by syllable which part came out wrong and what to change, instead of only
saying right or wrong.

QUIZ AND SCORES
Test what you have learned. Everyone who uses the phone gets their own score,
so a family or a class can compete. Scores stay on the device.

ADD YOUR OWN WORDS
Type any English word. It is translated once and saved, then you can hear it,
practise it and be quizzed on it.

WORKING WITHOUT INTERNET
Translation works with no connection once you have downloaded the language
packs, from Settings, Offline languages. Translating between two languages
needs a pack for each one.

Speaking without internet also needs your phone's own offline voice input,
which is a separate download in your phone's settings. English is installed on
most phones. Other languages usually are not. Settings, Set up offline use,
explains how to add it.

LANGUAGES
Hindi, Bengali, Tamil, Telugu, Marathi, Gujarati, Kannada, Urdu, English,
Afrikaans, Arabic, Bulgarian, Catalan, Chinese, Croatian, Czech, Danish, Dutch,
Filipino, Finnish, French, German, Greek, Hebrew, Hungarian, Indonesian,
Italian, Japanese, Korean, Malay, Norwegian, Persian, Polish, Portuguese,
Romanian, Russian, Slovak, Spanish, Swahili, Swedish, Thai, Turkish, Ukrainian
and Vietnamese.

Translation between two languages other than English passes through English, so
the result can be less accurate than translating to or from English directly.

This app contains ads.

Developed by Mr.Parashmani.
```

### Why it is written this way

- No language list padded with names the app does not support. Listing a
  language it cannot translate is a misrepresentation, and users notice.
- The offline section says what actually happens, including the part the app
  cannot do for you. Promising offline speech and then beeping at the user is
  the fastest route to one-star reviews.
- The pivot-through-English caveat is stated. It is a real quality limit.
- "This app contains ads" is required when the app has ads.
- No "unique", "revolutionary", "world's first". None of it is defensible and
  it reads as spam.

---

## What Play needs before this can go live

### Required

1. **Privacy policy URL.** Mandatory, and doubly so for an app in the Families
   programme. It must be a working public link, reachable without a login, and
   the same policy must be linked in the app. See the draft below.
2. **App content, Ads.** Declare that the app contains ads. It does.
3. **App content, Target audience and content.** The Families rules already
   apply to this app, so the declared audience includes children. Keep this in
   step with `AdPolicy.TARGETS_CHILDREN`, which is `true`.
4. **Content rating questionnaire.** Retake it if any answer has changed. The
   app has no violence, no sexual content, no gambling, no user-to-user
   communication and no location sharing.
5. **Data safety form.** Draft answers below. Verify them yourself; a wrong
   data safety form is itself a policy violation.
6. **Store graphics.** Icon 512x512, feature graphic 1024x500, at least two
   phone screenshots. Screenshots must show the real app. No mocked-up
   screens, no device frames with fake content, no text promising features
   that do not exist.

### Data safety, draft answers

Check each one against the build before submitting.

| Question | Answer | Why |
|---|---|---|
| Does the app collect or share user data? | Yes | The ads SDK does, even in child-directed mode |
| Personal info (name, email) | No | Names typed for the quiz stay on the device and are never sent |
| Location | No | No location permission |
| Photos | Collected, not shared, not stored | The camera scan reads a photo on the device and deletes it |
| Audio | Not collected by the app | Speech goes to the phone's own recognition service, not to SpeechNova |
| App activity, app interactions | Shared | Ads SDK |
| Device or other IDs | Shared | Ads SDK. The advertising ID permission is removed, so this is limited |
| Is data encrypted in transit? | Yes | |
| Can users request deletion? | Yes | Everything the app stores is on the device and goes when the app is uninstalled |

Audio is worth being careful about. SpeechNova never uploads recordings. The
phone's recognition service may, depending on the user's own settings, and that
is the phone's disclosure to make, not the app's. Do not claim the app collects
audio, and do not claim nothing ever leaves the phone.

### Privacy policy, what it has to say

Write it plainly and host it somewhere permanent. It has to cover:

- The app stores names, scores and saved words on the device only.
- Camera photos are read on the device and not uploaded.
- Speech is passed to the phone's own recognition service. Link Google's
  privacy policy for that.
- Translation runs on the device using downloaded language packs.
- Ads are served by Google AdMob. Link AdMob's policy. State that the app is
  configured as child-directed, so ads are not personalised and the advertising
  ID is not used.
- A contact email address.
- The date it was last updated.

---

## Before you upload

- The listing must match the build. If a feature named here is not in
  versionCode 11, take the line out.
- Screenshots must be real. Take them on a device.
- Walk all five tabs and confirm exactly one ad on each screen. Multiple ads
  per page is what got versionCode 10 rejected.
- Read `PLAY_POLICY.md` for the AdMob console work, which is separate from
  this and still has to be done by hand.
