#!/usr/bin/env python3
"""
SpeechNova — YouTube ad renderer.

Produces upload-ready MP4 ad creatives in the three aspect ratios a Google Ads
App campaign accepts: 9:16 (vertical, the one that makes the ad eligible for
YouTube Shorts), 1:1 (square) and 16:9 (horizontal).

Everything drawn here is generated from the app's own material: the palette in
ui/theme and MainActivity, the launcher icon, and claims taken from
STORE_LISTING.md that were checked against the source. Nothing is asserted that
the app does not do — a Google Ads creative that overstates the app is a
Misrepresentation violation, and it is also the fastest route to a one-star
review from someone who installed on the strength of it.

    python3 ads/render_ads.py                 # all ads, all ratios
    python3 ads/render_ads.py --ad table      # one ad
    python3 ads/render_ads.py --ratio 9x16    # one ratio
    python3 ads/render_ads.py --preview 3.5   # single PNG frame at t=3.5s

Requires: pillow, numpy, and ffmpeg (pip install imageio-ffmpeg supplies one).
"""

from __future__ import annotations

import argparse
import math
import os
import shutil
import struct
import subprocess
import sys
import wave
from dataclasses import dataclass, field
from pathlib import Path
from typing import Callable, Sequence

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
OUT = Path(__file__).resolve().parent / "out"
ICON = ROOT / "app" / "src" / "main" / "ic_launcher-playstore.png"

FPS = 30

# ── Palette ────────────────────────────────────────────────────────────────
# Lifted from the app so the ad and the first screen after the install look
# like the same product. MainActivity's HEADER_GRADIENT and nav accents.

INK        = (7, 11, 20)        # deeper than the app's #0b1220, for contrast
INDIGO     = (79, 70, 229)      # #4f46e5
VIOLET     = (124, 58, 237)     # #7c3aed
PURPLE     = (168, 85, 247)     # #a855f7
ROSE       = (219, 39, 119)     # #db2777
EMERALD    = (52, 211, 153)     # #34d399
SKY        = (56, 189, 248)     # #38bdf8
AMBER      = (251, 191, 36)     # #fbbf24
PINK       = (244, 114, 182)    # #f472b6
CYAN       = (34, 211, 238)
WHITE      = (255, 255, 255)
MUTED      = (156, 172, 199)
SCREEN_BG  = (12, 17, 28)

# ── Fonts ──────────────────────────────────────────────────────────────────
# FreeSans is the only face on a stock container that shapes Devanagari,
# Bengali and Tamil correctly, so anything non-Latin goes through it. Latin
# headlines use DejaVu Bold, which is a much stronger display face.

LATIN_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
LATIN_REG  = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
INDIC      = "/usr/share/fonts/truetype/freefont/FreeSans.ttf"

_font_cache: dict[tuple[str, int], ImageFont.FreeTypeFont] = {}


def _is_latin(text: str) -> bool:
    return all(ord(c) < 0x0590 for c in text)


def font_for(text: str, size: int, bold: bool = True) -> ImageFont.FreeTypeFont:
    """Pick a face that can actually shape `text`, and cache it."""
    path = (LATIN_BOLD if bold else LATIN_REG) if _is_latin(text) else INDIC
    key = (path, size)
    if key not in _font_cache:
        _font_cache[key] = ImageFont.truetype(path, size)
    return _font_cache[key]


def indic_stroke(text: str, size: int) -> float:
    """FreeSans has no bold cut, so Indic text is thickened with a stroke."""
    return 0.0 if _is_latin(text) else max(1.0, size * 0.022)


# ── Easing ─────────────────────────────────────────────────────────────────

def clamp(x: float, lo: float = 0.0, hi: float = 1.0) -> float:
    return lo if x < lo else hi if x > hi else x


def ease_out(x: float) -> float:
    return 1 - (1 - clamp(x)) ** 3


def ease_in_out(x: float) -> float:
    x = clamp(x)
    return 4 * x * x * x if x < 0.5 else 1 - (-2 * x + 2) ** 3 / 2


def rise(t: float, start: float, dur: float = 0.55) -> float:
    """Entrance progress for something that appears at `start`."""
    return ease_out((t - start) / dur)


def window(t: float, start: float, end: float, fade: float = 0.35) -> float:
    """Opacity for something visible between `start` and `end`."""
    return min(ease_out((t - start) / fade), ease_out((end - t) / fade))


def lerp(a: float, b: float, x: float) -> float:
    return a + (b - a) * x


def mix(c1: Sequence[int], c2: Sequence[int], x: float) -> tuple[int, int, int]:
    return tuple(int(round(lerp(c1[i], c2[i], clamp(x)))) for i in range(3))


# ── Canvas ─────────────────────────────────────────────────────────────────

@dataclass
class Canvas:
    """A frame in progress, plus the ratio-aware measurements to place things."""

    w: int
    h: int
    img: Image.Image

    @property
    def u(self) -> float:
        """Layout unit. 1u is 1/1080 of the short edge, so type and spacing
        scale between a tall Short and a wide in-stream ad.

        Wide frames get a deliberate boost. Sized purely off the short edge, a
        16:9 composition is technically correct and looks like a vertical ad
        stranded in the middle of a letterbox — the eye reads the empty thirds
        as a mistake. Larger type is what makes the wide cut look intended.
        """
        return min(self.w, self.h) / 1080 * (1.30 if self.wide else 1.0)

    @property
    def cx(self) -> float:
        return self.w / 2

    @property
    def wide(self) -> bool:
        return self.w > self.h * 1.2

    @property
    def text_w(self) -> float:
        """Widest a headline may be. Shorts crops nothing horizontally, but
        type that touches the edge reads as an error on every platform."""
        return self.w * 0.86

    @property
    def safe_top(self) -> float:
        return self.h * (0.10 if self.tall else 0.07)

    @property
    def safe_bottom(self) -> float:
        """On Shorts the bottom strip carries the channel name, caption and
        the app-install button Google appends. Nothing legible goes there."""
        return self.h * (0.82 if self.tall else 0.90)

    @property
    def tall(self) -> bool:
        return self.h > self.w * 1.2

    def overlay(self) -> tuple[Image.Image, ImageDraw.ImageDraw]:
        layer = Image.new("RGBA", (self.w, self.h), (0, 0, 0, 0))
        return layer, ImageDraw.Draw(layer)

    def merge(self, layer: Image.Image, alpha: float = 1.0) -> None:
        if alpha <= 0.001:
            return
        if alpha < 0.999:
            a = layer.getchannel("A").point(lambda v: int(v * alpha))
            layer.putalpha(a)
        self.img.alpha_composite(layer)


# ── Background ─────────────────────────────────────────────────────────────

_BLOBS = [
    # colour,   x drift,          y drift,          radius, phase
    (INDIGO, 0.22, 0.20, 0.75, 0.0),
    (VIOLET, 0.78, 0.30, 0.62, 1.7),
    (ROSE,   0.62, 0.82, 0.55, 3.1),
    (SKY,    0.18, 0.74, 0.50, 4.6),
    (PURPLE, 0.50, 0.50, 0.45, 2.4),
]


def background(w: int, h: int, t: float, energy: float = 1.0) -> Image.Image:
    """Slow-drifting aurora over near-black.

    Computed at a sixth resolution and scaled up: the blobs are soft by
    construction, so the small buffer costs nothing visually and turns the
    background from the slowest part of a frame into a rounding error.
    """
    sw, sh = max(8, w // 6), max(8, h // 6)
    yy, xx = np.mgrid[0:sh, 0:sw].astype(np.float32)
    xx /= sw
    yy /= sh
    acc = np.zeros((sh, sw, 3), dtype=np.float32)
    acc[:] = INK

    for colour, bx, by, br, phase in _BLOBS:
        cx = bx + 0.055 * math.sin(t * 0.28 + phase)
        cy = by + 0.045 * math.cos(t * 0.23 + phase * 1.3)
        # Aspect-corrected distance, so a blob stays round on a 16:9 frame.
        ar = sw / sh
        d = np.sqrt(((xx - cx) * ar) ** 2 + (yy - cy) ** 2) / br
        falloff = np.clip(1.0 - d, 0.0, 1.0) ** 2.4
        acc += falloff[..., None] * np.float32(colour) * (0.55 * energy)

    # Vignette, so the centre of the frame always wins the eye.
    vx = (xx - 0.5) * 2
    vy = (yy - 0.5) * 2
    vig = np.clip(1.06 - 0.46 * (vx * vx + vy * vy), 0.28, 1.0)
    acc *= vig[..., None]

    small = Image.fromarray(np.clip(acc, 0, 255).astype(np.uint8), "RGB")
    return small.resize((w, h), Image.BICUBIC).convert("RGBA")


# ── Drawing helpers ────────────────────────────────────────────────────────

Segment = tuple[str, tuple[int, int, int]]


def line_width(draw: ImageDraw.ImageDraw, segments: Sequence[Segment], size: int) -> float:
    total = 0.0
    for text, _ in segments:
        f = font_for(text, size)
        total += draw.textlength(text, font=f)
    return total


def fit_size(draw: ImageDraw.ImageDraw, segments: Sequence[Segment], size: int,
             max_w: float) -> int:
    """Shrink `size` until the line fits `max_w`.

    Every headline in this file goes through here. Type that runs off the edge
    of a 1080-wide Short is the single most common way a generated creative
    looks broken, and it is entirely avoidable.
    """
    while size > 12 and line_width(draw, segments, size) > max_w:
        size = int(size * 0.94)
    return size


def draw_line(
    draw: ImageDraw.ImageDraw,
    segments: Sequence[Segment],
    size: int,
    cx: float,
    y: float,
    align: str = "center",
    max_w: float | None = None,
    shadow: bool = True,
) -> int:
    """One line of type, each run in its own colour, drawn from a baseline-ish
    top edge at `y`. Runs are measured individually so a mixed Latin/Devanagari
    line stays tight instead of drifting apart. Returns the size actually used.
    """
    if max_w:
        size = fit_size(draw, segments, size, max_w)
    total = line_width(draw, segments, size)
    x0 = cx - total / 2 if align == "center" else cx

    if shadow:
        off = max(1.0, size * 0.045)
        x = x0
        for text, _ in segments:
            f = font_for(text, size)
            draw.text((x + off * 0.4, y + off), text, font=f, fill=(0, 0, 0, 115),
                      stroke_width=int(round(indic_stroke(text, size))),
                      stroke_fill=(0, 0, 0, 115))
            x += draw.textlength(text, font=f)

    x = x0
    for text, colour in segments:
        f = font_for(text, size)
        sw = indic_stroke(text, size)
        draw.text((x, y), text, font=f, fill=colour + (255,),
                  stroke_width=int(round(sw)), stroke_fill=colour + (255,))
        x += draw.textlength(text, font=f)
    return size


def wrap(draw: ImageDraw.ImageDraw, text: str, size: int, max_w: float) -> list[str]:
    words, lines, cur = text.split(), [], ""
    f = font_for(text, size)
    for word in words:
        trial = f"{cur} {word}".strip()
        if draw.textlength(trial, font=f) <= max_w or not cur:
            cur = trial
        else:
            lines.append(cur)
            cur = word
    if cur:
        lines.append(cur)
    return lines


def rounded(draw: ImageDraw.ImageDraw, box, r: float, fill=None, outline=None, width=1) -> None:
    draw.rounded_rectangle(box, radius=r, fill=fill, outline=outline, width=width)


def glow(canvas: Canvas, box, r: float, colour, alpha: float, spread: float) -> None:
    """A soft coloured halo behind a shape. Cheap fake bloom: draw, blur, merge."""
    layer, d = canvas.overlay()
    d.rounded_rectangle(box, radius=r, fill=colour + (int(255 * clamp(alpha)),))
    canvas.img.alpha_composite(layer.filter(ImageFilter.GaussianBlur(spread)))


# ── Phone mock ─────────────────────────────────────────────────────────────

@dataclass
class Phone:
    x: float
    y: float
    w: float
    h: float

    @property
    def screen(self) -> tuple[float, float, float, float]:
        b = self.w * 0.030
        return (self.x + b, self.y + b, self.x + self.w - b, self.y + self.h - b)


def draw_phone(canvas: Canvas, ph: Phone, screen: Callable[[Image.Image, tuple], None],
               alpha: float = 1.0) -> None:
    """A device outline with `screen` painting whatever is inside it.

    Deliberately a plain rounded slab rather than a recognisable handset: ad
    creative that mocks up a specific manufacturer's device is a trademark
    problem nobody needs.
    """
    layer, d = canvas.overlay()
    r = ph.w * 0.085
    glow(canvas, (ph.x, ph.y, ph.x + ph.w, ph.y + ph.h), r, VIOLET, 0.30 * alpha, ph.w * 0.09)
    rounded(d, (ph.x, ph.y, ph.x + ph.w, ph.y + ph.h), r, fill=(18, 22, 34, 255),
            outline=(120, 135, 175, 190), width=max(2, int(ph.w * 0.006)))
    sx0, sy0, sx1, sy1 = ph.screen
    rounded(d, (sx0, sy0, sx1, sy1), r * 0.86, fill=SCREEN_BG + (255,))
    screen(layer, (sx0, sy0, sx1, sy1))
    # Speaker pill.
    pw, phh = ph.w * 0.17, ph.h * 0.008
    d.rounded_rectangle(
        (ph.x + ph.w / 2 - pw / 2, ph.y + ph.h * 0.018,
         ph.x + ph.w / 2 + pw / 2, ph.y + ph.h * 0.018 + phh),
        radius=phh, fill=(70, 80, 105, 255))
    canvas.merge(layer, alpha)


def bubble(d: ImageDraw.ImageDraw, box, text: str, size: int, fill, text_colour,
           align_right: bool = False, alpha: float = 1.0) -> None:
    x0, y0, x1, y1 = box
    a = int(255 * clamp(alpha))
    r = (y1 - y0) * 0.30
    d.rounded_rectangle(box, radius=r, fill=fill + (int(a * 0.92),))
    size = fit_size(d, [(text, text_colour)], size, (x1 - x0) * 0.86)
    f = font_for(text, size)
    tw = d.textlength(text, font=f)
    tx = x1 - tw - (x1 - x0) * 0.07 if align_right else x0 + (x1 - x0) * 0.07
    ty = y0 + ((y1 - y0) - size * 1.18) / 2
    sw = int(round(indic_stroke(text, size)))
    d.text((tx, ty), text, font=f, fill=text_colour + (a,),
           stroke_width=sw, stroke_fill=text_colour + (a,))


# ── Reusable scene pieces ──────────────────────────────────────────────────

def end_card(canvas: Canvas, t: float, dur: float, tagline: str) -> None:
    """Identity, not a hard sell. In an App campaign Google appends its own
    install button and app name below the video, so the last four seconds only
    have to make the icon memorable and the promise legible."""
    u = canvas.u
    p = ease_out(t / 0.7)
    layer, d = canvas.overlay()

    icon_px = int(u * (200 if canvas.tall else 190))
    icon = Image.open(ICON).convert("RGBA").resize((icon_px, icon_px), Image.LANCZOS)
    mask = Image.new("L", (icon_px, icon_px), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, icon_px, icon_px), radius=icon_px * 0.24, fill=255)
    icon.putalpha(mask)

    block_h = icon_px + u * 300
    top = canvas.h / 2 - block_h / 2 + u * 26 * (1 - p)
    ix = canvas.cx - icon_px / 2

    glow(canvas, (ix, top, ix + icon_px, top + icon_px), icon_px * 0.24, SKY, 0.34 * p, u * 46)
    layer.alpha_composite(icon, (int(ix), int(top)))

    name_size = int(u * (108 if canvas.tall else 100))
    y = top + icon_px + u * 54
    draw_line(d, [("SpeechNova", WHITE)], name_size, canvas.cx, y)

    y += name_size * 1.32
    tag_size = int(u * (46 if canvas.tall else 44))
    lines = wrap(d, tagline, tag_size, canvas.w * 0.86)
    for i, ln in enumerate(lines):
        draw_line(d, [(ln, MUTED)], tag_size, canvas.cx, y + i * tag_size * 1.35,
                  max_w=canvas.w * 0.86, shadow=False)

    y += tag_size * 1.35 * len(lines) + u * 44
    pill_size = int(u * 42)
    label = "Free on Google Play  ·  Contains ads"
    pw = d.textlength(label, font=font_for(label, pill_size)) + u * 66
    ph_ = pill_size * 2.2
    d.rounded_rectangle((canvas.cx - pw / 2, y, canvas.cx + pw / 2, y + ph_),
                        radius=ph_ / 2, fill=(255, 255, 255, 26),
                        outline=(255, 255, 255, 70), width=max(1, int(u * 2)))
    draw_line(d, [(label, WHITE)], pill_size, canvas.cx, y + (ph_ - pill_size * 1.2) / 2)

    canvas.merge(layer, min(p, window(t, -1, dur, 0.4)))


def kicker(canvas: Canvas, d: ImageDraw.ImageDraw, text: str, y: float, colour=AMBER) -> None:
    """Small all-caps label above a headline."""
    size = int(canvas.u * 38)
    spaced = "  ".join(text.upper())
    draw_line(d, [(spaced, colour)], size, canvas.cx, y)


def tick_row(canvas: Canvas, d: ImageDraw.ImageDraw, items: Sequence[str], t: float,
             start: float, y: float) -> None:
    """A checklist, aligned on one left edge.

    Centring each row independently is the obvious thing to do and it looks
    wrong: the ticks end up on a ragged diagonal. One shared left edge, derived
    from the longest item, is what makes it read as a list.
    """
    size = int(canvas.u * 52)
    while size > 16 and max(d.textlength(i, font=font_for(i, size))
                            for i in items) + size * 1.7 > canvas.text_w:
        size = int(size * 0.94)
    widest = max(d.textlength(i, font=font_for(i, size)) for i in items) + size * 1.7
    left = canvas.cx - widest / 2
    for i, item in enumerate(items):
        p = rise(t, start + i * 0.28)
        if p <= 0:
            continue
        yy = y + i * size * 2.0 + (1 - p) * canvas.u * 28
        x = left
        r = size * 0.42
        d.ellipse((x, yy + size * 0.10, x + r * 2, yy + size * 0.10 + r * 2),
                  fill=EMERALD + (int(230 * p),))
        d.line((x + r * 0.62, yy + size * 0.10 + r, x + r * 0.92, yy + size * 0.10 + r * 1.35),
               fill=INK + (255,), width=max(2, int(canvas.u * 5)))
        d.line((x + r * 0.92, yy + size * 0.10 + r * 1.35, x + r * 1.42, yy + size * 0.10 + r * 0.62),
               fill=INK + (255,), width=max(2, int(canvas.u * 5)))
        draw_line(d, [(item, WHITE)], size, x + size * 1.7, yy, align="left", shadow=False)


def waveform(canvas: Canvas, d: ImageDraw.ImageDraw, cx: float, cy: float,
             width: float, t: float, alpha: float = 1.0, colour=CYAN) -> None:
    """The listening indicator, echoing the app's own animated waveform."""
    bars = 21
    gap = width / bars
    bw = gap * 0.42
    for i in range(bars):
        n = (i - (bars - 1) / 2) / ((bars - 1) / 2)
        env = math.cos(n * 1.35) ** 2
        amp = env * (0.35 + 0.65 * abs(math.sin(t * 6.2 + i * 0.55)))
        bh = max(canvas.u * 6, width * 0.10 * amp)
        x = cx - width / 2 + i * gap + (gap - bw) / 2
        d.rounded_rectangle((x, cy - bh / 2, x + bw, cy + bh / 2), radius=bw / 2,
                            fill=colour + (int(235 * clamp(alpha)),))


# ══════════════════════════════════════════════════════════════════════════
# Ad 1 — "Put the phone on the table"  (Face to Face, the hero creative)
# ══════════════════════════════════════════════════════════════════════════

def ad_table(canvas: Canvas, t: float) -> None:
    """The hero.

    Face to Face is the feature worth buying traffic for: it is the only one
    that is hard to copy and it is instantly legible in six seconds of silent
    video, which is how most of a Shorts audience will watch. The half of the
    screen that reads upside down is the whole idea, so the ad names it out
    loud rather than hoping a viewer works it out — an unexplained upside-down
    screen reads as a rendering bug, not a feature.
    """
    u = canvas.u

    # 0.0–3.0  the problem, put as two people rather than as a product claim
    if t < 3.3:
        layer, d = canvas.overlay()
        size = int(u * (96 if canvas.tall else 84))
        y = canvas.h / 2 - size * 1.5
        p1, p2 = rise(t, 0.2), rise(t, 1.2)
        if p1 > 0:
            draw_line(d, [("You don't speak ", WHITE), ("Hindi.", AMBER)],
                      size, canvas.cx, y + (1 - p1) * u * 34, max_w=canvas.text_w)
        if p2 > 0:
            draw_line(d, [("They don't speak ", WHITE), ("English.", SKY)],
                      size, canvas.cx, y + size * 1.45 + (1 - p2) * u * 34,
                      max_w=canvas.text_w)
        canvas.merge(layer, window(t, 0.0, 3.0, 0.32))

    # 2.9–5.4  the instruction, which is the whole user interface
    if 2.8 < t < 5.6:
        layer, d = canvas.overlay()
        size = int(u * (104 if canvas.tall else 90))
        p = rise(t, 3.05)
        y = canvas.h / 2 - size * 1.3 + (1 - p) * u * 36
        draw_line(d, [("Put the phone", WHITE)], size, canvas.cx, y, max_w=canvas.text_w)
        draw_line(d, [("on the table.", PINK)], size, canvas.cx, y + size * 1.22,
                  max_w=canvas.text_w)
        if t > 3.9:
            waveform(canvas, d, canvas.cx, y + size * 3.0, canvas.w * 0.32, t,
                     window(t, 3.9, 5.4, 0.28))
        canvas.merge(layer, window(t, 2.9, 5.4, 0.32))

    # 5.2–13.8  the feature actually working
    if 5.1 < t < 14.0:
        a = window(t, 5.2, 13.8, 0.38)
        ph_h = canvas.h * (0.58 if canvas.tall else 0.74 if canvas.wide else 0.62)
        ph_w = ph_h / 2.0
        float_y = math.sin(t * 1.05) * u * 6
        ph = Phone(canvas.cx - ph_w / 2,
                   canvas.h * (0.10 if canvas.tall else 0.04) + float_y, ph_w, ph_h)

        def screen(layer: Image.Image, box) -> None:
            sx0, sy0, sx1, sy1 = box
            sw_, sh_ = sx1 - sx0, sy1 - sy0
            d = ImageDraw.Draw(layer)
            mid = sy0 + sh_ / 2
            bs = int(sh_ * 0.048)

            # Their half is painted into its own buffer and turned through 180°,
            # which is exactly what the app does so the person sitting opposite
            # reads it the right way up.
            top = Image.new("RGBA", (int(sw_), int(sh_ / 2)), (0, 0, 0, 0))
            td = ImageDraw.Draw(top)
            tw, th = top.size
            td.rectangle((0, 0, tw, th), fill=(36, 23, 62, 255))
            td.text((tw * 0.07, th * 0.13), "THEM  ·  Hindi",
                    font=font_for("THEM", int(bs * 0.68)), fill=MUTED + (225,))
            if t > 6.4:
                bubble(td, (tw * 0.07, th * 0.32, tw * 0.93, th * 0.32 + bs * 2.4),
                       "स्टेशन कहाँ है?", bs, VIOLET, WHITE, alpha=window(t, 6.4, 13.8, 0.28))
            if t > 9.2:
                bubble(td, (tw * 0.16, th * 0.63, tw * 0.93, th * 0.63 + bs * 2.4),
                       "सीधे जाइए", bs, (48, 55, 78), WHITE, align_right=True,
                       alpha=window(t, 9.2, 13.8, 0.28))
            layer.alpha_composite(top.rotate(180), (int(sx0), int(sy0)))

            d.line((sx0 + sw_ * 0.07, mid, sx1 - sw_ * 0.07, mid),
                   fill=(130, 145, 185, 130), width=max(1, int(u * 2)))

            # Your half, the right way up. Offsets are fractions of the *half*
            # height so the two sides are laid out identically — which is the
            # point of the feature, and also keeps the lower bubble inside the
            # device instead of hanging off the bottom edge.
            hh = sh_ / 2
            d.text((sx0 + sw_ * 0.07, mid + hh * 0.13), "YOU  ·  English",
                   font=font_for("YOU", int(bs * 0.68)), fill=MUTED + (225,))
            if t > 5.9:
                bubble(d, (sx0 + sw_ * 0.07, mid + hh * 0.32,
                           sx0 + sw_ * 0.93, mid + hh * 0.32 + bs * 2.4),
                       "Where is the station?", bs, INDIGO, WHITE,
                       alpha=window(t, 5.9, 13.8, 0.28))
            if t > 9.7:
                bubble(d, (sx0 + sw_ * 0.16, mid + hh * 0.63,
                           sx0 + sw_ * 0.93, mid + hh * 0.63 + bs * 2.4),
                       "Go straight ahead", bs, (48, 55, 78), WHITE, align_right=True,
                       alpha=window(t, 9.7, 13.8, 0.28))
            waveform(canvas, d, (sx0 + sx1) / 2, sy1 - hh * 0.08, sw_ * 0.44, t, a * 0.9)

        draw_phone(canvas, ph, screen, a)

        layer, d = canvas.overlay()
        cap = int(u * (54 if canvas.tall else 46))
        cy = ph.y + ph.h + u * 44
        cy = min(cy, canvas.safe_bottom - cap * 2.6)
        if t > 7.4:
            draw_line(d, [("Their half is upside down —", WHITE)], cap, canvas.cx, cy,
                      max_w=canvas.text_w)
            draw_line(d, [("the right way up for them.", PINK)], cap, canvas.cx,
                      cy + cap * 1.26, max_w=canvas.text_w)
            canvas.merge(layer, window(t, 7.4, 10.9, 0.30))

        layer, d = canvas.overlay()
        if t > 11.1:
            draw_line(d, [("It listens and translates.", WHITE)], cap, canvas.cx, cy,
                      max_w=canvas.text_w)
            draw_line(d, [("No buttons to press.", EMERALD)], cap, canvas.cx,
                      cy + cap * 1.26, max_w=canvas.text_w)
            canvas.merge(layer, window(t, 11.1, 13.8, 0.30))

    # 13.6–16.6  what else is in the box
    if 13.5 < t < 16.8:
        layer, d = canvas.overlay()
        size = int(u * 52)
        y = canvas.h / 2 - size * 2.4
        kicker(canvas, d, "also inside", y - u * 78, AMBER)
        tick_row(canvas, d, ["Voice, text and camera scan",
                             "44 languages",
                             "Alphabet lessons and a quiz"], t, 13.8, y)
        canvas.merge(layer, window(t, 13.6, 16.6, 0.32))

    # 16.4–20.0  end card
    if t > 16.4:
        end_card(canvas, t - 16.5, 3.5, "Voice, text and camera translation.")


# ══════════════════════════════════════════════════════════════════════════
# Ad 2 — "Which syllable"  (pronunciation coaching)
# ══════════════════════════════════════════════════════════════════════════

def ad_syllable(canvas: Canvas, t: float) -> None:
    """Aimed at learner intent rather than traveller intent.

    The claim shown here is narrow on purpose. The app compares what the
    recogniser heard against the target akshara; it does not score the acoustics
    of a phoneme, and Pronunciation.kt is blunt about that. So the ad shows a
    vowel-length correction, which is exactly what the feature does.
    """
    u = canvas.u

    if t < 3.6:
        layer, d = canvas.overlay()
        size = int(u * (98 if canvas.tall else 86))
        y = canvas.h / 2 - size * 1.6
        if rise(t, 0.2) > 0:
            draw_line(d, [("Every app tells you", WHITE)], size, canvas.cx,
                      y + (1 - rise(t, 0.2)) * u * 32, max_w=canvas.text_w)
        if rise(t, 1.1) > 0:
            draw_line(d, [("you got it wrong.", ROSE)], size, canvas.cx,
                      y + size * 1.30 + (1 - rise(t, 1.1)) * u * 32, max_w=canvas.text_w)
        if rise(t, 2.1) > 0:
            draw_line(d, [("None of them says which part.", MUTED)], int(size * 0.55),
                      canvas.cx, y + size * 3.0 + (1 - rise(t, 2.1)) * u * 24,
                      max_w=canvas.text_w)
        canvas.merge(layer, window(t, 0.0, 3.4, 0.32))

    # The akshara diff, which is the thing worth showing.
    #
    # Laid out as a measured stack rather than fractions of the frame height:
    # the glyph boxes change size between a 9:16 Short and a 16:9 in-stream cut,
    # and hand-placed fractions that look right in one ratio collide in another.
    if 3.2 < t < 11.8:
        layer, d = canvas.overlay()
        big = int(u * (185 if canvas.tall else 150 if not canvas.wide else 108))
        cap_size = int(u * 48)
        lab_size = int(u * 46)
        len_size = int(u * 56)
        gap = u * 30
        row_h = big * 1.38
        lab_h = lab_size * 1.5
        arrow_h = u * 84

        total = (cap_size * 1.5 + row_h + lab_h + gap + arrow_h + gap
                 + len_size * 1.5 + row_h + lab_h)
        y = canvas.h / 2 - total / 2

        def row(items, top, label, label_colour, delay):
            widths = [d.textlength(g, font=font_for(g, big)) for g, _ in items]
            pad = u * 20
            total_w = sum(w + pad * 2 for w in widths) + gap * (len(items) - 1)
            x = canvas.cx - total_w / 2
            for i, ((glyph, ok), gw) in enumerate(zip(items, widths)):
                shown = rise(t, delay + i * 0.42)
                if shown > 0:
                    colour = EMERALD if ok else ROSE
                    d.rounded_rectangle((x, top, x + gw + pad * 2, top + row_h),
                                        radius=u * 22, fill=colour + (int(55 * shown),),
                                        outline=colour + (int(255 * shown),),
                                        width=max(2, int(u * 3)))
                    d.text((x + pad, top + big * 0.06), glyph, font=font_for(glyph, big),
                           fill=WHITE + (int(255 * shown),),
                           stroke_width=int(round(indic_stroke(glyph, big))),
                           stroke_fill=WHITE)
                x += gw + pad * 2 + gap
            draw_line(d, [(label, label_colour)], lab_size, canvas.cx,
                      top + row_h + lab_h * 0.16, max_w=canvas.text_w)

        draw_line(d, [("You said", MUTED)], cap_size, canvas.cx, y, max_w=canvas.text_w)
        y += cap_size * 1.5
        row([("क", False), ("ल", True)], y, "the vowel came out short", ROSE, 3.5)
        y += row_h + lab_h + gap

        if t > 6.0:
            aa = window(t, 6.0, 11.6, 0.28)
            d.line((canvas.cx, y, canvas.cx, y + arrow_h * 0.68),
                   fill=WHITE + (int(190 * aa),), width=max(2, int(u * 4)))
            d.polygon([(canvas.cx - u * 17, y + arrow_h * 0.62),
                       (canvas.cx + u * 17, y + arrow_h * 0.62),
                       (canvas.cx, y + arrow_h)], fill=WHITE + (int(190 * aa),))
        y += arrow_h + gap

        if t > 6.6:
            draw_line(d, [("Lengthen it: ", WHITE), ("क", MUTED), (" \u2192 ", MUTED),
                          ("का", AMBER)], len_size, canvas.cx, y, max_w=canvas.text_w)
            row([("का", True), ("ल", True)], y + len_size * 1.5,
                "काल  —  correct", EMERALD, 6.9)

        canvas.merge(layer, window(t, 3.3, 11.6, 0.38))

    if 11.4 < t < 15.2:
        layer, d = canvas.overlay()
        size = int(u * (68 if canvas.tall else 58))
        y = canvas.h / 2 - size * 1.8
        p = rise(t, 11.7)
        draw_line(d, [("Syllable by syllable.", WHITE)], size, canvas.cx,
                  y + (1 - p) * u * 30, max_w=canvas.text_w)
        draw_line(d, [("Hindi, Marathi, Bengali.", PINK)], size, canvas.cx,
                  y + size * 1.28, max_w=canvas.text_w)
        if t > 12.5:
            draw_line(d, [("On the device. Nothing uploaded.", MUTED)], int(size * 0.60),
                      canvas.cx, y + size * 3.0, max_w=canvas.text_w)
        canvas.merge(layer, window(t, 11.5, 15.0, 0.32))

    if t > 15.0:
        end_card(canvas, t - 15.1, 3.6,
                 "Hear every letter, practise it, and find out what to change.")


# ══════════════════════════════════════════════════════════════════════════
# Ad 3 — "Point the camera"  (OCR scan)
# ══════════════════════════════════════════════════════════════════════════

def ad_scan(canvas: Canvas, t: float) -> None:
    """Traveller intent, and the shortest of the three.

    The script list at the end is not padding — it is the honest limit. MLKit
    ships on-device recognisers for those five script families and no others,
    and an ad implying a Tamil sign will scan would earn the install and then
    lose it.
    """
    u = canvas.u

    if t < 3.2:
        layer, d = canvas.overlay()
        size = int(u * (104 if canvas.tall else 90))
        y = canvas.h / 2 - size * 1.2
        if rise(t, 0.15) > 0:
            draw_line(d, [("Can't read", WHITE)], size, canvas.cx,
                      y + (1 - rise(t, 0.15)) * u * 32, max_w=canvas.text_w)
        if rise(t, 0.9) > 0:
            draw_line(d, [("the sign?", AMBER)], size, canvas.cx,
                      y + size * 1.24 + (1 - rise(t, 0.9)) * u * 32, max_w=canvas.text_w)
        canvas.merge(layer, window(t, 0.0, 3.0, 0.32))

    if 2.8 < t < 11.6:
        layer, d = canvas.overlay()

        # Laid out against a vertical budget rather than fractions of the frame.
        # The viewfinder, the translation card and the closing caption have to
        # stack inside the safe area in every ratio; sized off the frame width
        # alone, the 4:5 image asset put the caption straight through the card.
        cs = int(u * (50 if canvas.tall else 42))
        rs = int(u * (70 if canvas.tall else 58))
        gap1 = u * (72 if canvas.tall else 50)
        gap2 = u * 40
        card_h = rs * 2.3
        cap_h = cs * 1.35 + cs * 0.62 * 1.3

        fw = canvas.w * (0.80 if canvas.tall else 0.44 if canvas.wide else 0.56)
        avail = canvas.safe_bottom - canvas.safe_top
        fh = min(fw * (0.50 if canvas.wide else 0.60),
                 avail - gap1 - card_h - gap2 - cap_h)
        block = fh + gap1 + card_h + gap2 + cap_h
        fx = canvas.cx - fw / 2
        fy = canvas.safe_top + (avail - block) / 2

        d.rounded_rectangle((fx, fy, fx + fw, fy + fh), radius=u * 26, fill=(16, 22, 36, 245))
        c, lw = fw * 0.11, max(3, int(u * 6))
        for ox, oy, dx, dy in ((fx, fy, 1, 1), (fx + fw, fy, -1, 1),
                               (fx, fy + fh, 1, -1), (fx + fw, fy + fh, -1, -1)):
            d.line((ox, oy, ox + c * dx, oy), fill=EMERALD + (240,), width=lw)
            d.line((ox, oy, ox, oy + c * dy), fill=EMERALD + (240,), width=lw)

        draw_line(d, [("रेलवे स्टेशन", (226, 232, 240))], int(fh * 0.28),
                  fx + fw / 2, fy + fh * 0.30, max_w=fw * 0.74, shadow=False)

        if 3.5 < t < 5.4:
            sp = clamp((t - 3.5) / 1.7)
            sy = fy + fh * sp
            for k in range(10):
                d.line((fx + u * 10, sy - k * u * 4, fx + fw - u * 10, sy - k * u * 4),
                       fill=EMERALD + (int(150 * (1 - k / 10)),), width=max(1, int(u * 3)))

        if t > 5.2:
            bp = ease_out((t - 5.2) / 0.45)
            bx0, bx1 = fx + fw * 0.15, fx + fw * 0.85
            d.rounded_rectangle((bx0, fy + fh * 0.24, bx0 + (bx1 - bx0) * bp, fy + fh * 0.70),
                                radius=u * 12, outline=EMERALD + (255,), width=max(2, int(u * 4)))

        ty = fy + fh + gap1
        if t > 6.1:
            tp = window(t, 6.1, 11.4, 0.32)
            d.polygon([(canvas.cx - u * 18, ty - u * 46), (canvas.cx + u * 18, ty - u * 46),
                       (canvas.cx, ty - u * 10)], fill=WHITE + (int(210 * tp),))
            box_w = canvas.w * (0.82 if canvas.tall else 0.52 if canvas.wide else 0.44)
            d.rounded_rectangle((canvas.cx - box_w / 2, ty, canvas.cx + box_w / 2, ty + card_h),
                                radius=u * 24, fill=(255, 255, 255, int(26 * tp)),
                                outline=(255, 255, 255, int(75 * tp)), width=max(1, int(u * 2)))
            draw_line(d, [("Railway station", WHITE)], rs, canvas.cx,
                      ty + (card_h - rs * 1.25) / 2, max_w=box_w * 0.86)

        if t > 8.4:
            base = ty + card_h + gap2
            draw_line(d, [("Point. Photo. Read.", EMERALD)], cs, canvas.cx, base,
                      max_w=canvas.text_w)
            draw_line(d, [("Latin, Devanagari, Chinese, Japanese, Korean.", MUTED)],
                      int(cs * 0.62), canvas.cx, base + cs * 1.35, max_w=canvas.text_w)

        canvas.merge(layer, window(t, 2.9, 11.4, 0.38))

    if t > 11.3:
        end_card(canvas, t - 11.4, 3.6, "Speak it, type it or photograph it. 44 languages.")


# ══════════════════════════════════════════════════════════════════════════

@dataclass
class Ad:
    key: str
    title: str
    duration: float
    draw: Callable[[Canvas, float], None]
    energy: float = 1.0
    notes: str = ""


ADS: list[Ad] = [
    Ad("table", "Put the phone on the table", 20.0, ad_table, 1.0,
       "Hero creative. Leads on Face to Face, the feature no obvious competitor has."),
    Ad("syllable", "Which syllable came out wrong", 18.7, ad_syllable, 0.92,
       "Learner-intent creative. Leads on akshara-level pronunciation feedback."),
    Ad("scan", "Point the camera", 15.0, ad_scan, 0.96,
       "Traveller-intent creative. Leads on camera OCR."),
]

RATIOS = {
    "9x16": (1080, 1920),
    "1x1": (1080, 1080),
    "16x9": (1920, 1080),
}

# The three image ratios an App campaign accepts, at Google's recommended
# sizes. The scene code keys off Canvas.tall / Canvas.wide rather than off a
# named ratio, so these compose correctly without a second set of layouts:
# 4:5 reads as tall, 1.91:1 as wide, 1:1 as neither.
IMAGE_RATIOS = {
    "1x1": (1200, 1200),
    "1.91x1": (1200, 628),
    "4x5": (1200, 1500),
}

# The moment in each ad that stands on its own as a still.
STILLS: list[tuple[str, str, float]] = [
    ("table", "table_flat", 12.4),      # the two-sided screen, mid-conversation
    ("table", "brand", 19.2),           # the end card, as a plain brand image
    ("syllable", "syllable", 10.4),     # the akshara correction
    ("scan", "scan", 9.6),              # sign to translation
]


# ── Audio ──────────────────────────────────────────────────────────────────

SR = 48000


def synth(duration: float, seed: int) -> np.ndarray:
    """A quiet, neutral bed: low pad, pentatonic plucks, a resolving chord.

    Deliberately understated. This exists so the ad is not silent — silence
    reads as a broken upload — and so the creative can be judged before anyone
    licenses a real track. Swap it for something from the YouTube Audio Library
    before you spend money on the campaign; see ads/CREATIVE_BRIEF.md.
    """
    rng = np.random.default_rng(seed)
    n = int(duration * SR)
    t = np.arange(n) / SR
    out = np.zeros(n, dtype=np.float64)

    # Pad: two low sines with slow movement.
    for f, amp in ((110.0, 0.055), (164.81, 0.040), (220.0, 0.022)):
        drift = 1 + 0.0016 * np.sin(2 * np.pi * 0.07 * t + f)
        out += amp * np.sin(2 * np.pi * f * t * drift) * (0.6 + 0.4 * np.sin(2 * np.pi * 0.09 * t))

    # Plucks on a minor pentatonic, one every half bar.
    scale = [440.00, 523.25, 587.33, 659.25, 783.99, 880.00]
    step = 0.5
    k = 0
    while k * step < duration - 0.5:
        start = k * step
        if k % 8 not in (0, 2, 3, 5, 6):
            k += 1
            continue
        f = scale[int(rng.integers(0, len(scale)))]
        idx = int(start * SR)
        length = min(int(1.1 * SR), n - idx)
        if length <= 0:
            break
        lt = np.arange(length) / SR
        env = np.exp(-lt * 4.2)
        note = (np.sin(2 * np.pi * f * lt) * 0.65
                + np.sin(2 * np.pi * f * 2 * lt) * 0.22
                + np.sin(2 * np.pi * f * 3 * lt) * 0.07)
        out[idx:idx + length] += note * env * 0.085
        k += 1

    # Soft air on transitions, so cuts do not land in a vacuum.
    for start in (0.0, duration * 0.32, duration * 0.62, duration - 3.7):
        idx = max(0, int(start * SR))
        length = min(int(0.9 * SR), n - idx)
        if length <= 0:
            continue
        noise = rng.normal(0, 1, length)
        # One-pole lowpass: a whoosh, not a hiss.
        b = np.zeros(length)
        acc = 0.0
        for i in range(0, length, 8):
            acc += 0.06 * (noise[i] - acc)
            b[i:i + 8] = acc
        lt = np.arange(length) / SR
        out[idx:idx + length] += b * np.exp(-lt * 3.4) * 0.55

    # Resolve on the end card.
    idx = max(0, int((duration - 3.6) * SR))
    length = n - idx
    if length > 0:
        lt = np.arange(length) / SR
        env = (1 - np.exp(-lt * 6)) * np.exp(-lt * 0.55)
        for f, amp in ((261.63, 0.06), (392.00, 0.045), (523.25, 0.035)):
            out[idx:] += amp * np.sin(2 * np.pi * f * lt) * env

    # Fades.
    fi = int(0.35 * SR)
    out[:fi] *= np.linspace(0, 1, fi)
    fo = int(0.8 * SR)
    out[-fo:] *= np.linspace(1, 0, fo)

    peak = np.max(np.abs(out)) or 1.0
    out = out / peak * 0.62

    # A few milliseconds of delay on the right channel for width.
    d = int(0.011 * SR)
    right = np.concatenate([np.zeros(d), out[:-d]]) * 0.92
    return np.stack([out, right], axis=1)


def write_wav(path: Path, audio: np.ndarray) -> None:
    pcm = np.clip(audio, -1, 1)
    pcm = (pcm * 32767).astype("<i2")
    with wave.open(str(path), "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


# ── Render ─────────────────────────────────────────────────────────────────

def ffmpeg_bin() -> str:
    found = shutil.which("ffmpeg")
    if found:
        return found
    try:
        import imageio_ffmpeg
        return imageio_ffmpeg.get_ffmpeg_exe()
    except Exception:
        sys.exit("ffmpeg not found. Install it, or: pip install imageio-ffmpeg")


def frame(ad: Ad, w: int, h: int, t: float) -> Image.Image:
    canvas = Canvas(w, h, background(w, h, t, ad.energy))
    ad.draw(canvas, t)
    return canvas.img.convert("RGB")


def render(ad: Ad, ratio: str, outdir: Path) -> Path:
    w, h = RATIOS[ratio]
    outdir.mkdir(parents=True, exist_ok=True)
    dest = outdir / f"speechnova_{ad.key}_{ratio}.mp4"
    wav = outdir / f".{ad.key}.wav"
    write_wav(wav, synth(ad.duration, seed=abs(hash(ad.key)) % 9999))

    total = int(ad.duration * FPS)
    cmd = [
        ffmpeg_bin(), "-y", "-loglevel", "error",
        "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{w}x{h}", "-r", str(FPS), "-i", "-",
        "-i", str(wav),
        "-c:v", "libx264", "-preset", "medium", "-crf", "20", "-pix_fmt", "yuv420p",
        "-profile:v", "high", "-level", "4.1",
        "-c:a", "aac", "-b:a", "192k", "-ar", "48000",
        "-movflags", "+faststart", "-shortest", str(dest),
    ]
    proc = subprocess.Popen(cmd, stdin=subprocess.PIPE)
    assert proc.stdin
    for i in range(total):
        proc.stdin.write(frame(ad, w, h, i / FPS).tobytes())
        if i % 60 == 0:
            print(f"    {ad.key} {ratio}  {i}/{total}", end="\r", flush=True)
    proc.stdin.close()
    if proc.wait() != 0:
        sys.exit(f"ffmpeg failed for {dest.name}")
    wav.unlink(missing_ok=True)
    size = dest.stat().st_size / 1e6
    print(f"    {ad.key} {ratio}  done  {size:.1f} MB{' ' * 20}")
    return dest


def main() -> None:
    ap = argparse.ArgumentParser(description="Render SpeechNova's YouTube ad creatives.")
    ap.add_argument("--ad", choices=[a.key for a in ADS], help="render one ad")
    ap.add_argument("--ratio", choices=list(RATIOS), help="render one aspect ratio")
    ap.add_argument("--preview", type=float, metavar="SECONDS",
                    help="write a single PNG frame instead of video")
    ap.add_argument("--images", action="store_true",
                    help="write the campaign's image assets instead of video")
    ap.add_argument("--geometry", action="store_true",
                    help="print the device screen rectangle, and the ffmpeg "
                         "command that drops a real screen recording into it")
    ap.add_argument("--out", default=str(OUT))
    args = ap.parse_args()

    if not ICON.exists():
        sys.exit(f"launcher icon missing: {ICON}")

    ads = [a for a in ADS if not args.ad or a.key == args.ad]
    ratios = [r for r in RATIOS if not args.ratio or r == args.ratio]
    outdir = Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)

    if args.geometry:
        # Printed rather than written into the brief by hand: the layout moves,
        # and a hard-coded overlay offset in a document goes stale silently.
        for r in ([args.ratio] if args.ratio else list(RATIOS)):
            w, h = RATIOS[r]
            c = Canvas(w, h, Image.new("RGBA", (1, 1)))
            ph_h = h * (0.58 if c.tall else 0.74 if c.wide else 0.62)
            ph_w = ph_h / 2.0
            ph = Phone(c.cx - ph_w / 2, h * (0.10 if c.tall else 0.04), ph_w, ph_h)
            x0, y0, x1, y1 = (int(round(v)) for v in ph.screen)
            print(f"  {r}: screen {x1 - x0}x{y1 - y0} at +{x0}+{y0}")
            print(f"    ffmpeg -i ads/out/speechnova_table_{r}.mp4 -i CAPTURE.mp4 \\")
            print(f"      -filter_complex \"[1:v]scale={x1 - x0}:{y1 - y0},"
                  f"format=yuv420p[cap];[0:v][cap]overlay={x0}:{y0}:"
                  f"enable='between(t,5.2,13.8)'\" \\")
            print("      -c:a copy -c:v libx264 -crf 20 -pix_fmt yuv420p out_real.mp4")
        print("\n  The mock drifts a few pixels vertically as it floats; set the")
        print("  float amplitude in ad_table to 0 if you need an exact match.")
        return

    if args.images:
        by_key = {a.key: a for a in ADS}
        for ad_key, name, t in STILLS:
            if args.ad and ad_key != args.ad:
                continue
            for label, (w, h) in IMAGE_RATIOS.items():
                dest = outdir / f"speechnova_{name}_{label}.jpg"
                # JPEG at 92: an App campaign image caps at 5 MB and these land
                # near 300 KB, so quality is the only thing worth spending on.
                frame(by_key[ad_key], w, h, t).save(dest, quality=92, optimize=True)
                print(f"    {dest.name}  {dest.stat().st_size // 1024} KB")
        return

    if args.preview is not None:
        for ad in ads:
            for r in ratios:
                w, h = RATIOS[r]
                p = outdir / f"preview_{ad.key}_{r}_{args.preview:g}s.png"
                frame(ad, w, h, args.preview).save(p)
                print(f"    {p}")
        return

    for ad in ads:
        print(f"  {ad.title}  ({ad.duration:g}s)")
        for r in ratios:
            render(ad, r, outdir)


if __name__ == "__main__":
    main()
