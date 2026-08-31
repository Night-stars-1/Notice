"""Featurisation shared (bit-for-bit) with app/.../domain/SpamFeatures.kt.

Pipeline: lowercase -> drop all whitespace, decimal digits and the letter 'x',
then char n-grams (1..3) over UTF-16 code units hashed with FNV-1a 32-bit.

Whitespace, digits and 'x' are dropped because the Chinese SMS corpus strips
spaces from ham rows and masks digits/names in spam rows as runs of 'x'; keeping
them would let the model learn "has a space => spam" / "has a digit => ham" /
"has xxx => spam" artifacts instead of the surrounding words.
"""
from __future__ import annotations

import unicodedata

BUCKETS = 1 << 18
NGRAM_MIN = 1
NGRAM_MAX = 3

# Keep in sync with SpamFeatures.SPACE_CHARS.
SPACE_CHARS = frozenset(
    "\t\n\x0b\x0c\r\x1c\x1d\x1e\x1f \x85\xa0\u1680"
    "\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200a"
    "\u2028\u2029\u202f\u205f\u3000"
)

FNV_OFFSET = 0x811C9DC5
FNV_PRIME = 0x01000193


def normalize(text: str) -> str:
    out = []
    for ch in text.lower():
        if ch in SPACE_CHARS or ch == "x" or unicodedata.category(ch) == "Nd":
            continue
        out.append(ch)
    return "".join(out)


def code_units(text: str) -> bytes:
    """UTF-16-LE bytes: two bytes (low, high) per code unit, surrogates kept."""
    return text.encode("utf-16-le", "surrogatepass")


def fnv1a32(data: bytes) -> int:
    h = FNV_OFFSET
    for b in data:
        h ^= b
        h = (h * FNV_PRIME) & 0xFFFFFFFF
    return h


def feature_buckets(
    text: str,
    ngram_min: int = NGRAM_MIN,
    ngram_max: int = NGRAM_MAX,
    buckets: int = BUCKETS,
) -> dict[int, int]:
    units = code_units(normalize(text))
    n_units = len(units) // 2
    mask = buckets - 1
    counts: dict[int, int] = {}
    for n in range(ngram_min, ngram_max + 1):
        for start in range(0, n_units - n + 1):
            chunk = units[start * 2 : (start + n) * 2]
            k = fnv1a32(chunk) & mask
            counts[k] = counts.get(k, 0) + 1
    return counts
