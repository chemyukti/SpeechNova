# Creative brief — the three ads, shot by shot

`python3 ads/render_ads.py` produces nine finished MP4s in `ads/out/`: three
concepts × three aspect ratios. They are uploadable as they stand. This file is
the script behind them, and the instructions for the better version you shoot
with real footage once the first campaign has data.

| Concept | Length | Files | Aimed at |
|---|---|---|---|
| **Put the phone on the table** | 20 s | `speechnova_table_{9x16,1x1,16x9}.mp4` | Anyone who has to talk to someone they share no language with |
| **Which syllable came out wrong** | 18.7 s | `speechnova_syllable_*.mp4` | People learning to *read and speak* an Indic language |
| **Point the camera** | 15 s | `speechnova_scan_*.mp4` | Travellers, and anyone facing a sign or a form |

All three are between 10 and 60 seconds, which is what App campaigns want, and
each ships in 9:16 — the ratio that makes the ad eligible for **YouTube Shorts**.

---

## Why three, and why these three

An App campaign rotates creative and reports on each asset. Three ads that make
three *different* arguments tell you something after two weeks: which promise
people respond to. Three variations on one argument tell you almost nothing.

So: one ad about talking to a person, one about learning to read, one about
reading the world. Different jobs, different audiences, one app.

The hero is **Put the phone on the table**. Face to Face is the feature that is
hard to copy, obvious in six seconds of silent video, and not what people
expect a translation app to do. Lead with it.

---

## 1. Put the phone on the table — 20 s

| Time | On screen | What is happening |
|---|---|---|
| 0.0–3.0 | **You don't speak Hindi.** / **They don't speak English.** | The problem as two people, not as a product claim. Two lines, staggered a beat apart. |
| 2.9–5.4 | **Put the phone on the table.** | The instruction *is* the interface. A waveform pulses under it. |
| 5.2–13.8 | The device, its screen split in two | Your line appears on your half; the Hindi appears on their half, upside down. Their reply comes back the other way. A listening waveform runs the whole time. |
| 7.4–10.9 | **Their half is upside down —** / **the right way up for them.** | Says the thing out loud. Unexplained, an upside-down screen reads as a rendering bug. |
| 11.1–13.8 | **It listens and translates.** / **No buttons to press.** | The payoff, stated plainly. |
| 13.6–16.6 | ALSO INSIDE ✓ Voice, text and camera scan ✓ 44 languages ✓ Alphabet lessons and a quiz | Breadth, after the specific claim has landed. Never before it. |
| 16.4–20.0 | Icon, **SpeechNova**, "Voice, text and camera translation", *Free on Google Play · Contains ads* | Identity. Google appends its own install button under the video. |

**The one thing not to cut:** the caption at 7.4. It is the difference between
a feature and a glitch.

---

## 2. Which syllable came out wrong — 18.7 s

| Time | On screen | What is happening |
|---|---|---|
| 0.0–3.4 | **Every app tells you** / **you got it wrong.** / None of them says which part. | Names the competitor's failure without naming a competitor. |
| 3.2–11.6 | You said → **क** **ल** — *the vowel came out short* | The wrong syllable is outlined in red, the right one in green. |
| 6.0–11.6 | ↓ **Lengthen it: क → का** → **का** **ल** — *काल — correct* | The correction, at the level a learner actually hears. |
| 11.4–15.0 | **Syllable by syllable.** / **Hindi, Marathi, Bengali.** / On the device. Nothing uploaded. | Scope, honestly bounded — three languages, because `Pronunciation.kt` handles Devanagari and Bengali and nothing else. |
| 15.0–18.7 | End card | |

**Do not widen this claim.** `Pronunciation.kt` says it plainly: this compares
what the recogniser heard against the target akshara. It does not score
acoustics. "Tells you which syllable was wrong" is true. "Rates your accent" is
not, and it is the kind of overreach a reviewer notices.

---

## 3. Point the camera — 15 s

| Time | On screen | What is happening |
|---|---|---|
| 0.0–3.0 | **Can't read** / **the sign?** | |
| 2.8–5.4 | A viewfinder over रेलवे स्टेशन | A green scan line sweeps down. |
| 5.2–6.1 | A detection box closes around the text | |
| 6.1–11.4 | ↓ **Railway station** | The translation, in a card below. |
| 8.4–11.4 | **Point. Photo. Read.** / Latin, Devanagari, Chinese, Japanese, Korean. | The script list is the honest limit, not a feature list. |
| 11.3–15.0 | End card | |

**The script list stays.** MLKit ships on-device recognisers for those five
families. An ad implying a Tamil sign will scan earns an install and loses it
the same afternoon.

---

## Making the better version: real footage

Generated motion graphics are a legitimate ad and a fine place to start. They
are not as good as the app doing the thing. Once the first campaign has two
weeks of data, shoot these and run them against the originals.

### Recording the screen

Use `adb`, not the phone's built-in recorder — you get a clean file with no
notification shade and no start-up countdown:

```sh
adb shell screenrecord --size 1080x1920 --bit-rate 12000000 /sdcard/f2f.mp4
# Ctrl-C when done
adb pull /sdcard/f2f.mp4
```

Before you record:

- Do Not Disturb on, battery above 80%, brightness at maximum.
- Clear the status bar. `adb shell settings put global sysui_demo_allowed 1`
  then the demo-mode broadcasts, or simply crop the top 60 px in the edit.
- Use real content. A "translation" typed into a mock screen is a fabricated
  screenshot, which is against Play's rules for listings and is a
  misrepresentation in an ad.

### What to capture, per concept

1. **Face to Face** — a real two-person exchange, phone flat, hands visible at
   the edges of the frame. Three turns is enough. The moment worth the whole ad
   is the second person reading their half without touching anything.
2. **Syllable** — the Learn tab: tap a letter, hear it, say the word, and let
   the app mark the syllable. Do it once, wrong, and let the correction show.
   Do not re-record until it is flattering; the failure *is* the feature.
3. **Scan** — one continuous take: sign, camera, photo, translation. No cuts.
   The cut is where a viewer assumes you hid something.

### Dropping footage into the existing frame

The generated ad already has a device outline, motion and audio. To use it as a
shell around real footage, scale the recording to the screen area and overlay:

```sh
python3 ads/render_ads.py --geometry
```

That prints the device screen rectangle for each ratio and the exact ffmpeg
command to composite into it, e.g. for 9:16 (screen 524×1080 at +278+209):

```sh
ffmpeg -i ads/out/speechnova_table_9x16.mp4 -i f2f.mp4 \
  -filter_complex "[1:v]scale=524:1080,format=yuv420p[cap];\
[0:v][cap]overlay=278:209:enable='between(t,5.2,13.8)'" \
  -c:a copy -c:v libx264 -crf 20 -pix_fmt yuv420p out_real.mp4
```

Ask the script rather than copying the numbers from here: change the layout and
a hard-coded offset in a document goes stale without anyone noticing. The mock
also drifts a few pixels as it floats — zero the float amplitude in `ad_table`
if you need a pixel-exact match.

### Music

The bed in these files is synthesised in `render_ads.py` so the videos are not
silent. It is placeholder. Replace it from the **YouTube Audio Library**, where
the licence is already cleared for exactly this use — before you put money
behind the campaign, and long before you use anything you found elsewhere. A
copyright claim on an ad creative does not get resolved quickly.

### Voiceover

Optional, and only if it is good. Most Shorts play muted, so every ad here
works silent by design and nothing in the on-screen text depends on hearing
anything. If you add voice: read the on-screen lines, do not write new ones,
and stop talking during the demo.

---

## Localising

The frames are drawn from data, so a Hindi cut is an edit to the strings in
`render_ads.py`, not a re-shoot. Two cautions:

- **Fonts.** FreeSans is what shapes Devanagari, Bengali and Tamil correctly on
  a bare container, and `font_for()` selects it automatically for any non-Latin
  run. If you render somewhere with Noto installed, point `INDIC` at
  Noto Sans Devanagari instead — it is a much better display face.
- **Have a native speaker read it.** Same rule as the Hindi copy in
  `TEXT_ASSETS.md`.

## What not to do

- Do not add a countdown, a fake "1 offer left", or a false install count.
- Do not show a screen the app cannot produce.
- Do not put a Play badge in the video. The install button is Google's to add.
- Do not open with the logo. Three seconds of branding is three seconds of a
  Shorts viewer's thumb travelling upward.
