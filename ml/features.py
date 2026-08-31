"""与 app/.../domain/SpamFeatures.kt 逐位一致的特征化实现。

流程：转小写 -> 去掉所有空白字符、十进制数字和字母 'x'，
然后对 UTF-16 代码单元取字符 n-gram（1..3），并用 FNV-1a 32 位哈希。

之所以去掉空白、数字和 'x'，是因为中文短信语料在正常样本中去掉了空格，
而在垃圾样本中把数字/人名掩码为连续的 'x'；若保留它们，模型会学到
"有空格 => 垃圾" / "有数字 => 正常" / "有 xxx => 垃圾" 这类伪特征，而不是周围的词语。
"""
from __future__ import annotations

import unicodedata

BUCKETS = 1 << 18
NGRAM_MIN = 1
NGRAM_MAX = 3

# 与 SpamFeatures.SPACE_CHARS 保持同步。
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
    """UTF-16-LE 字节序列：每个代码单元两个字节（低位、高位），保留代理项。"""
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
