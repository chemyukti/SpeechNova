# Text and image assets

Paste-ready. Every character count below was measured, not estimated. Every
claim was checked against the source before it went in — see the notes at the
bottom for which line came from which file.

---

## Headlines — 5 of 5, limit 30 characters

| # | Headline | Chars | Why this one |
|---|---|---|---|
| 1 | `Speak. It translates aloud.` | 27 | The core action, in three words. Leads with the verb. |
| 2 | `Put it on the table and talk` | 28 | Face to Face. The one line no competitor can copy. |
| 3 | `Learn the Hindi alphabet` | 24 | Learner intent, which converts differently from travel intent. |
| 4 | `Translate 44 languages` | 22 | The count in `MainActivity.langList`. Not "all languages". |
| 5 | `Voice, text or camera` | 21 | Breadth, for the slot where a specific claim is wasted. |

Google mixes headlines with descriptions freely and shows them in any
combination, so each one has to stand alone. None of these depends on another
line to make sense.

### Spares, for when an asset comes back rated Low

| Headline | Chars | Note |
|---|---|---|
| `Scan a sign, read it at once` | 28 | Pairs with the camera creative. |
| `Which syllable was wrong?` | 25 | Strong for learners, weak for travellers. |
| `Two people, one phone` | 21 | Another angle on Face to Face. |
| `Say it. Hear it translated.` | 27 | Straight rewrite of headline 1. |
| `Works offline after setup` | 25 | Honest. "Works offline" on its own would not be. |

---

## Descriptions — 5 of 5, limit 90 characters

| # | Description | Chars |
|---|---|---|
| 1 | `Tap the microphone and speak. Your words are translated and read out loud.` | 74 |
| 2 | `Put the phone on the table. It listens to both of you and translates. No buttons.` | 81 |
| 3 | `Photograph a sign or a menu and read it in your own language.` | 61 |
| 4 | `See the letters of your language, tap any one to hear it, then practise saying it.` | 82 |
| 5 | `Works with no connection once you download the language packs from Settings.` | 76 |

Description 5 is the one to leave alone. "Works offline" full stop is a
misrepresentation: translation needs downloaded packs, and offline *speech*
additionally needs the phone's own offline voice input, which SpeechNova cannot
install for the user. `STORE_LISTING.md` spells this out and the reasoning is
the same here — promise offline, deliver a beep, collect one star.

---

## Hindi set — optional, and get it checked

Language targeting keys off the user's interface language. If you target Hindi
as well as English (recommended for India), a Hindi asset set will serve to
Hindi-interface users. These are within limits and read naturally, but **have a
native speaker read them before you spend on them.** Ad copy in a language
nobody on the project speaks fluently is how you end up with a headline that is
technically correct and slightly funny.

**Headlines**

| Headline | Chars |
|---|---|
| `बोलिए, अनुवाद सुनिए` | 19 |
| `फ़ोन मेज़ पर रखिए` | 17 |
| `44 भाषाओं में अनुवाद` | 20 |
| `हिंदी वर्णमाला सीखिए` | 20 |
| `आवाज़, टेक्स्ट या कैमरा` | 23 |

**Descriptions**

| Description | Chars |
|---|---|
| `माइक दबाइए और बोलिए। आपके शब्दों का अनुवाद होकर ज़ोर से पढ़ा जाता है।` | 69 |
| `फ़ोन को मेज़ पर रखिए। यह दोनों को सुनता है और अनुवाद करता है, कोई बटन नहीं।` | 75 |
| `किसी बोर्ड या मेन्यू की तस्वीर लीजिए और उसे अपनी भाषा में पढ़िए।` | 64 |
| `अपनी भाषा के अक्षर देखिए, किसी पर टैप करके सुनिए, फिर बोलकर अभ्यास कीजिए।` | 73 |
| `भाषा पैक डाउनलोड करने के बाद बिना इंटरनेट के भी काम करता है।` | 60 |

---

## Image assets

Generated with `python3 ads/render_ads.py --images`, into `ads/out/`. Twelve
files: four designs at each of the three ratios an App campaign accepts.

| Ratio | Size produced | Google minimum | Files |
|---|---|---|---|
| 1:1 | 1200 × 1200 | 200 × 200 | `speechnova_*_1x1.jpg` |
| 1.91:1 | 1200 × 628 | 600 × 314 | `speechnova_*_1.91x1.jpg` |
| 4:5 | 1200 × 1500 | 320 × 400 | `speechnova_*_4x5.jpg` |

Designs: `table_flat` (the two-sided conversation screen), `brand` (icon and
name), `syllable` (the pronunciation correction), `scan` (sign to translation).
All land around 60–130 KB against a 5 MB ceiling.

Upload at least one per ratio. Uploading all twelve is better — App campaigns
rotate assets and report each one's performance, and you cannot learn which
angle works from a single image.

### What is deliberately not in them

- **No Google Play badge.** If you want one in an image, take the official
  artwork from Google's brand pages and respect the clear space. Redrawing it
  is a trademark problem.
- **No device frame that resembles a real handset.** The mock is a plain
  rounded slab for the same reason.
- **No screenshots of the app.** The Play listing screenshots are pulled into
  the campaign automatically, so these images do a different job: they carry
  one idea each, at a size that survives a 300-pixel-wide Display placement.

---

## Where each claim comes from

Worth keeping straight, because Google Ads' Misrepresentation policy is about
the ad, and Play's is about the listing, and both of them are about the build.

| Claim | Verified against |
|---|---|
| 44 languages | `MainActivity.kt`, `langList` — counted, not estimated |
| Reads translations aloud | `selectVoice` / TTS path in `MainActivity.kt` |
| Face to Face listens continuously, no buttons | `Screen.FACE2FACE`; `PLAY_POLICY.md` confirms the ad gate was removed in v9 |
| Camera scan: Latin, Devanagari, Chinese, Japanese, Korean | the five `text-recognition*` dependencies in `app/build.gradle.kts` |
| Syllable-level feedback for Hindi, Marathi, Bengali | `Pronunciation.kt` — Devanagari and Bengali scripts |
| Offline needs downloaded packs | `OfflineLanguages.kt`, `SpeechPacks.kt`, and the caveat in `STORE_LISTING.md` |
| Contains ads | `AdPolicy.kt`; declared in Play Console |

If a feature changes, change the copy in the same commit. An ad that outlived
its feature is a policy violation with a paper trail.
