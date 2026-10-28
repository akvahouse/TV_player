"""Adds the Ukrainian letters Univia Pro lacks (і ї є ґ and capitals) and writes Android font resources.

Glyph names in these files do not match their shapes, so every source glyph is looked up through
the cmap by code point, and ї / Ї are built from the i stem and its dot.
"""
import math
import re
import sys
from pathlib import Path

from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.recordingPen import RecordingPen
from fontTools.pens.reverseContourPen import ReverseContourPen
from fontTools.pens.t2CharStringPen import T2CharStringPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont

src_dir, out_dir = Path(sys.argv[1]), Path(sys.argv[2])


def contours(font, name):
    """The glyph's outline split into separate contours (lists of pen operations)."""
    pen = RecordingPen()
    font.getGlyphSet()[name].draw(pen)
    result, current = [], []
    for op in pen.value:
        current.append(op)
        if op[0] in ("closePath", "endPath"):
            result.append(current)
            current = []
    return result


def bounds_of(ops):
    pen = BoundsPen(None)
    for method, args in ops:
        getattr(pen, method)(*args)
    return pen.bounds


def replay(ops, pen, dx=0, dy=0):
    shifted = TransformPen(pen, (1, 0, 0, 1, dx, dy))
    for method, args in ops:
        getattr(shifted, method)(*args)


def add_glyph(font, name, draw, advance):
    cff = font["CFF "].cff
    top = cff.topDictIndex[0]
    pen = T2CharStringPen(advance, font.getGlyphSet())
    draw(pen)
    charstring = pen.getCharString(private=top.Private, globalSubrs=cff.GlobalSubrs)
    strings = top.CharStrings
    if strings.charStringsAreIndexed:
        strings.charStringsIndex.append(charstring)
        strings.charStrings[name] = len(strings.charStringsIndex) - 1
    else:
        strings.charStrings[name] = charstring
    # For CFF fonts the glyph order and the charset are the same list: append once.
    order = font.getGlyphOrder()
    order.append(name)
    if top.charset is not order:
        top.charset.append(name)
    bounds = BoundsPen(None)
    draw(bounds)
    font["hmtx"][name] = (advance, int(bounds.bounds[0]) if bounds.bounds else 0)


def mirrored(font, source):
    """є is a mirrored э, Є a mirrored Э."""
    glyphs = font.getGlyphSet()
    advance = font["hmtx"][source][0]

    def draw(pen):
        glyphs[source].draw(ReverseContourPen(TransformPen(pen, (-1, 0, 0, 1, advance, 0))))

    return draw, advance


def with_upturn(font, source):
    """ґ / Ґ: г / Г with a short stroke rising from the right end of the top bar."""
    glyphs = font.getGlyphSet()
    advance = font["hmtx"][source][0]
    bounds = BoundsPen(glyphs)
    glyphs[source].draw(bounds)
    x_min, _, x_max, y_max = bounds.bounds
    stem = max(20, round((x_max - x_min) * 0.2))
    rise = round(y_max * 0.2)

    def draw(pen):
        glyphs[source].draw(pen)
        pen.moveTo((x_max - stem, y_max - stem))
        pen.lineTo((x_max, y_max - stem))
        pen.lineTo((x_max, y_max + rise))
        pen.lineTo((x_max - stem, y_max + rise))
        pen.closePath()

    return draw, advance


def with_dieresis(font, i_name, base_name):
    """ї from the i stem, Ї from I; both get two copies of the i dot."""
    parts = contours(font, i_name)
    dot = max(parts, key=lambda ops: bounds_of(ops)[1])
    i_stem = [ops for ops in parts if ops is not dot]
    dot_x0, dot_y0, dot_x1, _ = bounds_of(dot)
    dot_w = dot_x1 - dot_x0
    stem_top = max(bounds_of(ops)[3] for ops in i_stem)
    gap = dot_y0 - stem_top

    base = i_stem if base_name == i_name else contours(font, base_name)
    bx0 = min(bounds_of(ops)[0] for ops in base)
    bx1 = max(bounds_of(ops)[2] for ops in base)
    by1 = max(bounds_of(ops)[3] for ops in base)
    by0 = min(bounds_of(ops)[1] for ops in base)
    dot_y_mid = by1 + gap + (bounds_of(dot)[3] - dot_y0) / 2
    if base_name == i_name:
        center = (dot_x0 + dot_x1) / 2  # the i dot already sits where italic puts it
    else:
        # Follow the italic slant from the middle of the stem up to where the dots go. The files
        # report an italic angle of 0, so measure it from the stem's left edge instead.
        points = [pt for _, args in (op for ops in base for op in ops) for pt in args]
        low = min((p for p in points if p[1] <= by0 + 1), key=lambda p: p[0])
        high = min((p for p in points if p[1] >= by1 - 1), key=lambda p: p[0])
        slant = (high[0] - low[0]) / max(1, high[1] - low[1])
        center = (bx0 + bx1) / 2 + slant * (dot_y_mid - (by0 + by1) / 2)
    spread = dot_w * 0.85  # distance from the stem center to each dot center
    left = center - spread - dot_w / 2
    right = center + spread + dot_w / 2
    advance = font["hmtx"][base_name][0]
    # Dots may overhang the advance a little, as accents do; widen only beyond that.
    allowance = dot_w * 0.3
    extra = max(0, -left - allowance, right - advance - allowance)
    shift = extra
    advance += round(2 * extra)
    dot_dy = (by1 + gap) - dot_y0

    def draw(pen):
        for ops in base:
            replay(ops, pen, dx=shift)
        dot_center = (dot_x0 + dot_x1) / 2
        for side in (-1, 1):
            replay(dot, pen, dx=center + side * spread - dot_center + shift, dy=dot_dy)

    return draw, advance


for path in sorted(src_dir.glob("UniviaPro-*.otf")):
    font = TTFont(path)
    cmap = font.getBestCmap()
    glyph = lambda code: cmap[code]
    mapping = {0x456: glyph(0x69), 0x406: glyph(0x49)}  # і, І look exactly like Latin i, I
    for code, name, (draw, advance) in [
        (0x454, "uk.ie", mirrored(font, glyph(0x44D))),
        (0x404, "uk.Ie", mirrored(font, glyph(0x42D))),
        (0x491, "uk.ghe", with_upturn(font, glyph(0x433))),
        (0x490, "uk.Ghe", with_upturn(font, glyph(0x413))),
        (0x457, "uk.yi", with_dieresis(font, glyph(0x69), glyph(0x69))),
        (0x407, "uk.Yi", with_dieresis(font, glyph(0x69), glyph(0x49))),
    ]:
        add_glyph(font, name, draw, advance)
        mapping[code] = name
    for table in font["cmap"].tables:
        if table.isUnicode():
            table.cmap.update(mapping)
    style = path.stem.removeprefix("UniviaPro-")
    snake = re.sub(r"(?<!^)(?=[A-Z])", "_", style).lower()
    font.save(out_dir / f"univia_pro_{snake}.otf")
print("patched", len(list(src_dir.glob("UniviaPro-*.otf"))))
