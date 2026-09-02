# Google Ads — everything SpeechNova needs before money moves

This is the console-side runbook. Nothing here can be done from code. Work it
top to bottom; the sections are in dependency order, not importance order.

A companion to `PLAY_POLICY.md`, which covers the ads the app *shows*. This
file covers the ads the app *buys*. They are different products with different
rules and it is easy to confuse them.

---

## 0. The constraint that shapes every decision below

`AdPolicy.TARGETS_CHILDREN` is `true`, and Play reviewed the app against the
**Families Policy Requirements**. That declaration follows the app into Google
Ads, and it removes options other advertisers have:

| What | Consequence for this app |
|---|---|
| Personalised advertising | Google will not serve personalised ads to children under 13. You cannot opt into it for that segment, and you should not try. |
| Age or demographic targeting | Not available in App campaigns at all, so this is moot — but do not go looking for a workaround. Targeting ads *only* to under-13s is prohibited outright. |
| Remarketing / audience lists | Do not build remarketing lists from this app's users. Behavioural targeting of children is exactly what the Families rules forbid, and it is what got version 8 rejected on the serving side. |
| Third-party trackers | Ads running on "made for kids" YouTube content cannot use third-party trackers. If you later add a mobile measurement partner SDK, that is a COPPA question before it is a marketing question. |
| Creative content | Your own ad creative has to be G-rated. This is not automatic — it is on you. |

**None of this stops you advertising.** App campaigns are automated and do not
expose age targeting to anyone, so in practice the working difference is: no
remarketing, no MMP without legal thought, and creative that a nine-year-old
could see. That is the whole cost.

Sources: [Ad-serving protections for children](https://support.google.com/adspolicy/answer/14170968),
[Ads and made-for-kids content](https://support.google.com/adspolicy/answer/9683742).

---

## 1. Prerequisites — do not start a campaign until all of these are true

1. **The app is live on Play.** An App campaign can only promote a published
   app. Version 9 is live; if you are waiting on 14, you can still build the
   campaign, but do not switch it on against a listing you are about to change.
2. **A Google Ads account exists, with billing set up.** Note that **currency
   and time zone are permanent**. Pick the currency you actually pay in and the
   time zone you actually read reports in. Getting this wrong means a new
   account later, and you lose all learning with it.
3. **Play Console is linked to Google Ads.**
   Play Console → *Download reports* / *Setup → Advertising*, or from the Ads
   side: Tools → *Data manager* → link Google Play. This is what makes install
   conversions track automatically. Without it you are buying blind.
4. **A privacy policy is live at a public URL** and linked in the Play listing.
   `STORE_LISTING.md` already flags this as mandatory for Play. Ads has its own
   EU user consent policy on top.
5. **Decide about `speechnova.app`.** The manifest declares an `autoVerify`
   deep link for `https://speechnova.app`. If that domain does not exist or does
   not serve a valid `assetlinks.json`, Android silently does not verify the
   link. It costs nothing today — installs campaigns do not need deep links —
   but it blocks engagement campaigns later, and it is a dead link in a manifest
   until you fix it.

---

## 2. Conversion tracking — start smaller than you think

Two routes, and the right one for this app is the boring one.

**Route A — Google Play install conversion only.** Comes free with the Play
link in step 1.3. Tracks first-open. No new SDK, no new permission, **no change
to the Data safety form**. This is the recommendation.

**Route B — Firebase / GA4 for in-app events.** Lets you bid on something
better than an install (say, "completed a first translation"). It also means
adding an analytics SDK to a child-directed app, which means revisiting the
Data safety declarations that `STORE_LISTING.md` carefully worked out, and
setting `google_analytics_default_allow_ad_personalization_signals` to false.

Do not take Route B to launch. Route A gets a campaign live this week. Move to
B only when install volume is steady and you have a specific in-app event
worth paying more for — and treat it as a Play compliance change, because it is
one.

Either way: **set conversion tracking up before the first impression.** A
campaign that runs for a week with no conversion signal has learned nothing and
you cannot backfill it.

---

## 3. Campaign structure

One campaign. Resist the urge to build five.

| Setting | Value | Why |
|---|---|---|
| Campaign type | App | |
| Subtype | **App installs** | Engagement needs a large installed base — Google's docs currently put the floor at 50,000 installs — and working deep links. Not yet. |
| Platform | Android | There is no iOS build. |
| App | `com.parashmani.speechnova` | Search by name, confirm the package. |
| Locations | Start with **India** | It is the addressable market for eight Indic languages, the cost per install is the lowest anywhere, and it is where the alphabet features mean something. |
| Languages | English **and** Hindi | Language targeting in Ads keys off the user's interface language, not the ad's. Selecting both widens delivery; it does not commit you to Hindi creative. |
| Bid strategy | **Target cost per install** | |
| Target CPI | Start high, taper | Set it 20–30% above what you actually want. A target set too low starves the campaign and it never leaves the learning phase. |
| Daily budget | **at least 10× your target CPI** | This is the rule that people ignore and then wonder why nothing serves. At a ₹20 target, that is ₹200/day floor; ₹400–600 is where it starts learning properly. |

### Geography, in the order worth trying

1. **India** — the core case. Highest intent for Hindi/Marathi/Bengali
   pronunciation, lowest CPI.
2. **UAE, Saudi Arabia, Qatar** — very large South Asian working population,
   travel and everyday-translation intent, and the "Face to Face" creative
   lands without any localisation.
3. **US, UK, Canada, Australia** — diaspora and heritage-language learners.
   Several times the CPI of India. Only worth it once you know what a retained
   user is worth to you, which today you do not.

Run one geo at a time until the numbers are stable. Mixing India and the US in
one campaign means the algorithm spends the budget where installs are cheapest
and you learn nothing about the expensive market.

---

## 4. Assets the campaign needs

App campaigns have no ad editor. You supply assets, Google assembles them into
placements across Search, Play, YouTube, Discover and the Display network. What
you give it is the entire creative decision.

| Asset | Limit | What to supply | Where it is |
|---|---|---|---|
| Headlines | up to 5, **30 characters** each | Supply all 5 | `ads/TEXT_ASSETS.md` |
| Descriptions | up to 5, **90 characters** each | Supply all 5 | `ads/TEXT_ASSETS.md` |
| Images | up to 20; 1.91:1, 1:1 and 4:5; JPG/PNG; 5 MB max | At least one per ratio | `ads/TEXT_ASSETS.md` |
| Videos | up to 20; 16:9, 1:1 and 9:16; **10–60 s** | At least one per ratio — the 9:16 is what makes the ad eligible for **YouTube Shorts** | `ads/out/`, see `ads/CREATIVE_BRIEF.md` |
| HTML5 | up to 30 | Skip | — |

Recommended image sizes: 1200×1200 (1:1, min 200×200), 1200×628 (1.91:1, min
600×314), 1200×1500 (4:5, min 320×400).

**If you supply no video, Google generates one from your images and text.** It
is recognisably auto-generated and it is the single most common reason an
otherwise sane App campaign underperforms. Upload real video.

**Screenshots from the Play listing are pulled in automatically.** They still
count as creative. If they are stale, fix them; the ad shows them.

---

## 5. Getting the videos onto YouTube

Google Ads cannot accept a video file. It accepts a YouTube URL.

1. Upload each MP4 from `ads/out/` to the channel that belongs to the app.
2. **Visibility: Unlisted.** Public works too but clutters the channel.
   **Private does not work** — Ads cannot read it.
3. Leave **embedding enabled**. An ad that cannot be embedded cannot serve.
4. **"Is this video made for kids?"** — this is the question worth stopping on.
   It describes the *content of the video*, not the audience of the app. These
   ads address the adult who decides to install: a traveller, a parent, a
   learner. On that reading the answer is **no, not made for kids**. It is your
   call and it has legal weight, so make it deliberately rather than clicking
   past it. Marking a video made-for-kids disables personalised advertising on
   it, which for an ad creative is simply a restriction with no upside.
5. Title each one so you can find it later, e.g.
   `SpeechNova — Face to Face (9:16)`. Viewers of the ad never see the title.
6. Paste the URLs into the campaign, one per aspect ratio.

---

## 6. Google Ads policy — the parts that actually bite

Distinct from Play policy. An app can be perfectly compliant on Play and have
its ads disapproved.

- **Misrepresentation.** Every claim in the creative must be true of the build.
  This is why the copy in `ads/TEXT_ASSETS.md` says "44 languages" (the count in
  `MainActivity.langList`) and not "all languages", and why the scan ad names
  the five script families MLKit actually recognises. "Works offline" without
  the language-pack caveat is the one that would get you.
- **Trademarks.** Do not put "Google Translate" in ad copy. Do not imply
  endorsement by or affiliation with Google. "Free on Google Play" is a
  factual statement of where it is sold and is fine.
- **The Google Play badge.** If you use it in an image asset, use the official
  artwork from Google's brand pages at the specified clear space. Do not redraw
  it. The videos in `ads/out/` deliberately use plain text instead.
- **Superlatives.** "Best", "#1", "fastest" need substantiation you do not
  have. `STORE_LISTING.md` already bans these for the listing; the same
  discipline applies here.
- **Destination.** The Play listing must load and match the ad. It will.
- **Your own creative must be G-rated.** The ad you buy is held to the same
  standard as the ads you serve.

---

## 7. Launch checklist

```
[ ] Play Console linked to Google Ads
[ ] Currency and time zone correct (permanent)
[ ] Billing added
[ ] Install conversion action present and "Recording conversions"
[ ] Privacy policy URL live and linked from the listing
[ ] 5 headlines, all ≤ 30 characters
[ ] 5 descriptions, all ≤ 90 characters
[ ] ≥ 1 image per ratio: 1.91:1, 1:1, 4:5
[ ] 3 videos uploaded to YouTube, unlisted, embeddable, made-for-kids answered
[ ] YouTube URLs added — at least one 9:16 for Shorts eligibility
[ ] Geo: India only for the first run
[ ] Languages: English + Hindi
[ ] Daily budget ≥ 10 × target CPI
[ ] No remarketing or audience lists attached
```

---

## 8. The first thirty days

**Days 1–14 — do not touch it.** The learning phase needs uninterrupted signal.
Every edit to budget, bid, targeting or assets restarts it. This is the single
hardest instruction in this file to follow and the most expensive to ignore.
Expect CPI to be bad and erratic for the first week. That is the algorithm
paying for information.

**Day 14 — first real read.** Look at: installs, cost per install, and the
per-asset performance ratings (Low / Good / Best). Do not read daily numbers
before this point; the variance is larger than any effect you could detect.

**Days 14–30 — one change at a time.**
- Replace assets rated **Low**. Add replacements rather than only deleting, so
  the campaign never drops below the minimum asset counts.
- Adjust the target CPI by no more than 20% in one move, then leave it a week.
- If volume is nil and the target is well above your CPI, the budget is the
  constraint, not the bid.

**After 30 days** — add a second geo as a *separate campaign*, so its numbers
are legible. Do not add it to this one.

### What to stop doing

- Checking it hourly. There is no decision you can make on an hour of data.
- Pausing and restarting. Every restart is a fresh learning phase paid for at
  full price.
- Adding a fourth, fifth and sixth video before the first three have data.
- Chasing a low CPI. An install that never opens the app twice cost you money
  and returned an ad impression you will never serve. Once Route B tracking
  exists, judge on retained users instead.
