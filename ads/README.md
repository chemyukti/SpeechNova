# ads/ — advertising SpeechNova on YouTube and Google Ads

Everything needed to run a Google Ads App campaign for SpeechNova, including
the ad videos themselves.

## What is here

| File | What it is |
|---|---|
| **`GOOGLE_ADS_SETUP.md`** | The console runbook. Prerequisites, conversion tracking, campaign structure, budget, policy, launch checklist, first-30-days plan. Start here. |
| **`CREATIVE_BRIEF.md`** | The three ads shot by shot, plus how to reshoot them with real footage. |
| **`TEXT_ASSETS.md`** | Paste-ready headlines and descriptions with measured character counts, a Hindi set, and the image-asset specs. |
| **`render_ads.py`** | Generates the videos and images. |
| **`out/`** | The rendered assets, committed so you can upload them without a toolchain. Regenerate with the command below after any edit. |

## Producing the assets

```sh
pip install pillow numpy imageio-ffmpeg
python3 ads/render_ads.py            # 9 videos: 3 concepts x 3 aspect ratios
python3 ads/render_ads.py --images   # 12 images: 4 designs x 3 aspect ratios
```

Useful while editing:

```sh
python3 ads/render_ads.py --ad table --ratio 9x16 --preview 12.4   # one PNG frame
python3 ads/render_ads.py --geometry                               # device screen rect
```

Videos land in the three ratios an App campaign accepts — 9:16, 1:1 and 16:9 —
and all are between 10 and 60 seconds. The 9:16 cut is what makes the ad
eligible for YouTube Shorts.

## The short version, in order

1. Link Play Console to Google Ads and confirm the install conversion records.
   Nothing else matters until this works.
2. Render the assets. Upload the three MP4s per concept to YouTube as
   **unlisted**, embedding on, and answer the made-for-kids question
   deliberately — `GOOGLE_ADS_SETUP.md` §5 explains why it is not obvious.
3. Build one App-installs campaign: Android, India, English + Hindi, target CPI
   set 20–30% high, daily budget at least ten times that target.
4. Paste in 5 headlines, 5 descriptions, the images and the YouTube URLs.
5. Leave it alone for fourteen days. This is the instruction people ignore.

## Two things that are easy to get wrong

**This app is declared child-directed.** `AdPolicy.TARGETS_CHILDREN` is `true`
and Play reviewed it against the Families rules. That follows the app into
Google Ads: no remarketing lists built from its users, no third-party trackers
on made-for-kids inventory, and G-rated creative. It does not stop you
advertising — App campaigns expose no age targeting to anyone — but it does rule
out a few tactics you will otherwise read about. `GOOGLE_ADS_SETUP.md` §0.

**Every claim in the creative is checked against the build.** 44 languages
because that is the length of `MainActivity.langList`; five script families for
the camera scan because that is what the MLKit dependencies cover; three
languages for syllable feedback because that is what `Pronunciation.kt`
handles; offline always qualified with the language-pack caveat. Google Ads'
Misrepresentation policy applies to the ad the same way Play's Metadata policy
applies to the listing. If a feature changes, change the copy in the same
commit.
