"""Cuts static weights out of the Exo 2 and Montserrat variable fonts.

Android 11 does not apply font variation settings to resource fonts, so each weight the app uses
becomes its own file: res/font/<family>[_italic]_w<weight>.ttf

usage: python instance_fonts.py <dir with Exo2[wght].ttf etc.> <res/font dir>
"""
import sys
from pathlib import Path

from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

# Latin, Cyrillic (Ukrainian included), punctuation, currency, №, arrows.
UNICODES = "U+0000-024F,U+0300-036F,U+0400-04FF,U+2000-206F,U+20A0-20CF,U+2100-214F,U+2190-21FF,U+2212"

# Must match UniviaWeight.value in Settings.kt.
WEIGHTS = [100, 200, 300, 350, 400, 500, 700, 850, 900]
SOURCES = {
    "exo2": "Exo2[wght].ttf",
    "exo2_italic": "Exo2-Italic[wght].ttf",
    "montserrat": "Montserrat[wght].ttf",
    "montserrat_italic": "Montserrat-Italic[wght].ttf",
}

src, out = Path(sys.argv[1]), Path(sys.argv[2])
for name, file in SOURCES.items():
    for weight in WEIGHTS:
        font = instantiateVariableFont(TTFont(src / file), {"wght": weight}, updateFontNames=False)
        # Drop other scripts and TrueType hinting, which a TV screen at this size does not need.
        options = subset.Options()
        options.hinting = False
        options.layout_features = ["*"]
        subsetter = subset.Subsetter(options)
        subsetter.populate(unicodes=subset.parse_unicodes(UNICODES))
        subsetter.subset(font)
        font.save(out / f"{name}_w{weight}.ttf")
print("done")
