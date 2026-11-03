"""Prépare les polices embarquées de CarDeck (pip install fonttools).

  python3 tools/fonts/subset.py <dossier des sources>

Sources (Google Fonts / material-design-icons, licences OFL et Apache 2.0) :
  rf.ttf  = RobotoFlex[GRAD,XOPQ,XTRA,YOPQ,YTAS,YTDE,YTFI,YTLC,YTUC,opsz,slnt,wdth,wght].ttf
  gsf.ttf = GoogleSansFlex[ROND,opsz,slnt,wdth,wght].ttf
  msr.ttf = MaterialSymbolsRounded[FILL,GRAD,opsz,wght].ttf (+ msr.codepoints)

Les axes inutiles sont figés et seuls les caractères latins (et les icônes de symbols.txt) sont gardés.
"""
import os
import sys
from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

src = sys.argv[1]
here = os.path.dirname(os.path.abspath(__file__))
out = os.path.join(here, "..", "..", "app", "src", "main", "res", "font")
LATIN = "U+0020-007E,U+00A0-024F,U+02BC,U+02C6,U+02DA,U+02DC,U+0300-0304,U+0308,U+0327,U+1E00-1EFF,U+2000-206F,U+20AC,U+2116,U+2122,U+2190-2199,U+2212,U+2215,U+221E,U+2248,U+2260,U+2264,U+2265"


def build(name, axes, unicodes, dest):
    font = TTFont(os.path.join(src, name), lazy=False)
    opts = subset.Options()
    opts.layout_features = ["*"]
    opts.name_IDs = ["*"]
    opts.notdef_outline = True
    s = subset.Subsetter(opts)
    s.populate(unicodes=unicodes)
    s.subset(font)
    font = instancer.instantiateVariableFont(font, axes)
    font.save(os.path.join(out, dest))
    print(dest, os.path.getsize(os.path.join(out, dest)) // 1024, "Ko")


latin = subset.parse_unicodes(LATIN)
build("rf.ttf", {"opsz": 14, "GRAD": 0, "wdth": 100, "slnt": 0, "XOPQ": 96, "YOPQ": 79, "XTRA": 468,
                 "YTUC": 712, "YTLC": 514, "YTAS": 750, "YTDE": -203, "YTFI": 738}, latin, "roboto_flex.ttf")
build("gsf.ttf", {"wdth": 100, "ROND": 0, "slnt": 0}, latin, "google_sans_flex.ttf")

codes = {}
for line in open(os.path.join(src, "msr.codepoints")):
    n, c = line.split()
    codes.setdefault(n, int(c, 16))
names = [l.strip() for l in open(os.path.join(here, "symbols.txt")) if l.strip() and not l.startswith("#")]
missing = [n for n in names if n not in codes]
if missing:
    sys.exit("Icônes inconnues : " + ", ".join(missing))
build("msr.ttf", {"GRAD": 0, "opsz": 24, "wght": 400}, [codes[n] for n in names], "material_symbols_rounded.ttf")
