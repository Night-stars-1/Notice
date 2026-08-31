# 骚扰通知智能识别 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an offline spam/harassment classifier to Notice: a Python-trained char-n-gram logistic-regression model bundled in the APK, scored inside `system_server` behind a global "智能识别骚扰" switch with a threshold slider.

**Architecture:** `ml/` holds the training pipeline (uv + scikit-learn) and exports a tiny binary weight file plus parity fixtures. `app` gets a pure-Kotlin `SpamFeatures`/`SpamModel` pair (bit-identical featurisation to Python), a `SpamJudge` decision helper, two new `FilterConfig` fields, a hook in `KeywordFilter.shouldBlock`, and a switch + slider in `SettingsScreen`.

**Tech Stack:** Python 3 via `uv` (numpy, scikit-learn); Kotlin/Android (AGP 9.3.2 built-in Kotlin, Compose M3); JUnit 4 + `org.json` for JVM unit tests.

**Spec:** `docs/superpowers/specs/2026-08-31-spam-classifier-design.md`

## Global Constraints

- Build env: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"; export ANDROID_HOME="$HOME/Library/Android/sdk"`; invoke Gradle as `sh ./gradlew …` (the wrapper is not executable).
- No new runtime dependencies in `app` (pure Kotlin inference). Test-only deps: `junit:junit:4.13.2`, `org.json:json:20250517`.
- Featurisation must be bit-identical between `ml/features.py` and `SpamFeatures.kt`: lowercase → drop whitespace (explicit set), decimal digits (Nd) and the letter `x`; char n-grams n∈{1,2,3} over UTF-16 code units; FNV-1a 32-bit over (low byte, high byte) of each code unit; bucket = hash & (2^18 − 1); counts L2-normalised.
- **Execution note (2026-08-31):** the corpus strips spaces from ham and masks digits/names in spam as `x`; the original "collapse whitespace / digit→0" featurisation learned those artifacts (English text and `你好` scored as spam, a loan ad with a phone number scored as ham). The plan's code blocks show the original featurisation; the committed code is the corrected one described above, plus an English SMS corpus, `C=1`, and a hard never-block guard for verification-code messages in `SpamJudge`.
- Model file `app/src/main/resources/model/spam_v1.bin`, big-endian: `NSPM`, int32 version=1, int32 buckets, int32 ngramMin, int32 ngramMax, float32 bias, float32 scale, then `buckets` int8 weights (w = q × scale).
- UI: only the Settings "通用" section changes. No changes to Logs or Rules screens.
- Do not commit unless the user asks; `git add` is fine.

---

### Task 1: Python feature extraction with fixed test vectors

**Files:**
- Create: `ml/pyproject.toml`
- Create: `ml/features.py`
- Create: `ml/test_features.py`

**Interfaces:**
- Produces: `normalize(text: str) -> str`, `code_units(text: str) -> bytes` (UTF-16-LE), `fnv1a32(data: bytes) -> int`, `feature_buckets(text: str, ngram_min=1, ngram_max=3, buckets=1<<18) -> dict[int,int]`, `SPACE_CHARS: frozenset[str]`, `BUCKETS = 1 << 18`.

- [ ] **Step 1: Create the uv project**

```toml
# ml/pyproject.toml
[project]
name = "notice-ml"
version = "0.1.0"
requires-python = ">=3.10"
dependencies = [
    "numpy>=1.26",
    "scikit-learn>=1.4",
    "scipy>=1.11",
]

[dependency-groups]
dev = ["pytest>=8"]
```

- [ ] **Step 2: Write the failing tests**

```python
# ml/test_features.py
from features import normalize, fnv1a32, feature_buckets, BUCKETS


def test_normalize_lower_digits_spaces():
    assert normalize("  Hello　World  123 ") == "hello world 000"
    assert normalize("ＡＢＣ１２３") == "ａｂｃ000"
    assert normalize("a\t\n b") == "a b"
    assert normalize("") == ""


def test_fnv1a32_known_vectors():
    assert fnv1a32(b"") == 0x811C9DC5
    assert fnv1a32(b"a") == 0xE40C292C
    assert fnv1a32(b"foobar") == 0xBF9CF968


def test_feature_buckets_unigram_counts():
    # "aa" -> unigrams a,a ; bigram aa  => 2 distinct buckets, counts 2 and 1
    b = feature_buckets("aa")
    assert sorted(b.values()) == [1, 2]
    assert all(0 <= k < BUCKETS for k in b)


def test_feature_buckets_uses_utf16_units():
    # U+1F600 is a surrogate pair -> 2 code units -> unigrams: 2, bigram: 1
    b = feature_buckets("\U0001F600")
    assert sum(b.values()) == 3
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `cd ml && uv run --group dev pytest -q test_features.py`
Expected: FAIL with `ModuleNotFoundError: No module named 'features'`

- [ ] **Step 4: Implement features.py**

```python
# ml/features.py
"""Featurisation shared (bit-for-bit) with app/.../domain/SpamFeatures.kt.

Pipeline: lowercase -> decimal digits to '0' -> collapse whitespace -> trim,
then char n-grams (1..3) over UTF-16 code units hashed with FNV-1a 32-bit.
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
    pending_space = False
    for ch in text.lower():
        if ch in SPACE_CHARS:
            pending_space = True
            continue
        if unicodedata.category(ch) == "Nd":
            ch = "0"
        if pending_space and out:
            out.append(" ")
        pending_space = False
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
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd ml && uv run --group dev pytest -q test_features.py`
Expected: `4 passed`

- [ ] **Step 6: Stage**

```bash
git add ml/pyproject.toml ml/features.py ml/test_features.py
```

---

### Task 2: Kotlin `SpamFeatures` with the same fixed vectors

**Files:**
- Modify: `app/build.gradle.kts` (add test deps)
- Create: `app/src/main/java/moe/notice/filter/domain/SpamFeatures.kt`
- Test: `app/src/test/java/moe/notice/filter/domain/SpamFeaturesTest.kt`

**Interfaces:**
- Produces: `object SpamFeatures { const val BUCKETS = 1 shl 18; const val NGRAM_MIN = 1; const val NGRAM_MAX = 3; fun normalize(text: String): String; fun fnv1a32(text: CharSequence, start: Int, end: Int): Int; fun buckets(text: String, ngramMin: Int = NGRAM_MIN, ngramMax: Int = NGRAM_MAX, buckets: Int = BUCKETS): Map<Int, Int> }`

- [ ] **Step 1: Add JUnit and org.json to the test classpath**

In `app/build.gradle.kts` `dependencies { … }` add after the libxposed lines:

```kotlin
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
```

- [ ] **Step 2: Write the failing test**

```kotlin
// app/src/test/java/moe/notice/filter/domain/SpamFeaturesTest.kt
package moe.notice.filter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpamFeaturesTest {
    @Test
    fun normalizeLowerDigitsSpaces() {
        assertEquals("hello world 000", SpamFeatures.normalize("  Hello　World  123 "))
        assertEquals("ａｂｃ000", SpamFeatures.normalize("ＡＢＣ１２３"))
        assertEquals("a b", SpamFeatures.normalize("a\t\n b"))
        assertEquals("", SpamFeatures.normalize(""))
    }

    @Test
    fun fnv1a32KnownVectors() {
        assertEquals(0x811C9DC5.toInt(), SpamFeatures.fnv1a32("", 0, 0))
        // "a" as one UTF-16 unit is bytes 0x61 0x00, differs from the ASCII vector;
        // compare against the Python implementation's value for the same bytes.
        assertEquals(fnvOfBytes(byteArrayOf(0x61, 0x00)), SpamFeatures.fnv1a32("a", 0, 1))
        assertEquals(fnvOfBytes("foobar".toByteArray(Charsets.UTF_16LE)), SpamFeatures.fnv1a32("foobar", 0, 6))
    }

    @Test
    fun bucketsUnigramCounts() {
        val b = SpamFeatures.buckets("aa")
        assertEquals(listOf(1, 2), b.values.sorted())
        assertTrue(b.keys.all { it in 0 until SpamFeatures.BUCKETS })
    }

    @Test
    fun bucketsUseUtf16Units() {
        val b = SpamFeatures.buckets("😀")
        assertEquals(3, b.values.sum())
    }

    private fun fnvOfBytes(bytes: ByteArray): Int {
        var h = 0x811C9DC5.toInt()
        for (byte in bytes) {
            h = h xor (byte.toInt() and 0xFF)
            h *= 0x01000193
        }
        return h
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `sh ./gradlew :app:testDebugUnitTest --tests 'moe.notice.filter.domain.SpamFeaturesTest' -q`
Expected: compilation FAIL, `Unresolved reference: SpamFeatures`

- [ ] **Step 4: Implement SpamFeatures.kt**

```kotlin
// app/src/main/java/moe/notice/filter/domain/SpamFeatures.kt
package moe.notice.filter.domain

/**
 * Featurisation shared bit-for-bit with ml/features.py.
 * Pipeline: lowercase -> decimal digits to '0' -> collapse whitespace -> trim,
 * then char n-grams (1..3) over UTF-16 code units hashed with FNV-1a 32-bit.
 */
object SpamFeatures {
    const val BUCKETS = 1 shl 18
    const val NGRAM_MIN = 1
    const val NGRAM_MAX = 3

    private const val FNV_OFFSET = 0x811C9DC5.toInt()
    private const val FNV_PRIME = 0x01000193

    // Keep in sync with features.SPACE_CHARS.
    private val SPACE_CHARS: Set<Char> = (
        "\t\n\u000B\u000C\r\u001C\u001D\u001E\u001F \u0085\u00A0\u1680" +
            "\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200A" +
            "\u2028\u2029\u202F\u205F\u3000"
        ).toSet()

    fun normalize(text: String): String {
        val out = StringBuilder(text.length)
        var pendingSpace = false
        for (raw in text.lowercase()) {
            if (raw in SPACE_CHARS) {
                pendingSpace = true
                continue
            }
            val ch = if (Character.getType(raw) == Character.DECIMAL_DIGIT_NUMBER.toInt()) '0' else raw
            if (pendingSpace && out.isNotEmpty()) out.append(' ')
            pendingSpace = false
            out.append(ch)
        }
        return out.toString()
    }

    /** FNV-1a over the UTF-16 code units in [start, end), each fed as (low byte, high byte). */
    fun fnv1a32(text: CharSequence, start: Int, end: Int): Int {
        var h = FNV_OFFSET
        for (i in start until end) {
            val c = text[i].code
            h = h xor (c and 0xFF)
            h *= FNV_PRIME
            h = h xor ((c ushr 8) and 0xFF)
            h *= FNV_PRIME
        }
        return h
    }

    fun buckets(
        text: String,
        ngramMin: Int = NGRAM_MIN,
        ngramMax: Int = NGRAM_MAX,
        buckets: Int = BUCKETS,
    ): Map<Int, Int> {
        val s = normalize(text)
        val mask = buckets - 1
        val counts = HashMap<Int, Int>()
        for (n in ngramMin..ngramMax) {
            var start = 0
            while (start + n <= s.length) {
                val k = fnv1a32(s, start, start + n) and mask
                counts[k] = (counts[k] ?: 0) + 1
                start++
            }
        }
        return counts
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `sh ./gradlew :app:testDebugUnitTest --tests 'moe.notice.filter.domain.SpamFeaturesTest' -q`
Expected: BUILD SUCCESSFUL, no failures.

- [ ] **Step 6: Stage**

```bash
git add app/build.gradle.kts app/src/main/java/moe/notice/filter/domain/SpamFeatures.kt app/src/test/java/moe/notice/filter/domain/SpamFeaturesTest.kt
```

---

### Task 3: Training script that exports the model and parity fixtures

**Files:**
- Create: `ml/train.py`
- Create: `ml/export.py`
- Create: `ml/test_export.py`
- Create: `ml/.gitignore` (ignore `data/`, `.venv/`, `__pycache__/`)
- Create: `ml/README.md`
- Produces (generated, committed): `app/src/main/resources/model/spam_v1.bin`, `app/src/test/resources/model/parity.json`

**Interfaces:**
- Produces: `export.write_model(path, weights: np.ndarray, bias: float, buckets, ngram_min, ngram_max) -> tuple[np.ndarray, float]` (returns dequantised weights & scale), `export.score(text, weights_dequant, bias) -> float`, `export.write_parity(path, texts, weights_dequant, bias)`.

- [ ] **Step 1: Write the failing export test**

```python
# ml/test_export.py
import json
import struct

import numpy as np

from export import write_model, write_parity, score, sigmoid
from features import BUCKETS


def test_write_model_header_and_quantisation(tmp_path):
    w = np.zeros(BUCKETS, dtype=np.float32)
    w[5] = 1.27
    w[9] = -0.635
    path = tmp_path / "m.bin"
    deq, scale = write_model(path, w, bias=-0.5, buckets=BUCKETS, ngram_min=1, ngram_max=3)
    raw = path.read_bytes()
    magic, version, buckets, nmin, nmax, bias, s = struct.unpack(">4siiiiff", raw[:28])
    assert magic == b"NSPM" and version == 1 and buckets == BUCKETS
    assert (nmin, nmax) == (1, 3)
    assert abs(bias - (-0.5)) < 1e-6 and abs(s - scale) < 1e-9
    q = np.frombuffer(raw[28:], dtype=np.int8)
    assert len(q) == BUCKETS
    assert q[5] == 127 and q[9] == -64
    assert abs(deq[5] - 1.27) < 1e-6


def test_score_matches_manual_dot(tmp_path):
    w = np.zeros(BUCKETS, dtype=np.float32)
    # bias only -> sigmoid(bias)
    assert abs(score("hello", w, 2.0) - sigmoid(2.0)) < 1e-6
    assert score("", w, 2.0) == sigmoid(2.0)


def test_write_parity(tmp_path):
    w = np.zeros(BUCKETS, dtype=np.float32)
    path = tmp_path / "parity.json"
    write_parity(path, ["a", "b"], w, 0.0)
    data = json.loads(path.read_text())
    assert data == [{"text": "a", "score": 0.5}, {"text": "b", "score": 0.5}]
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ml && uv run --group dev pytest -q test_export.py`
Expected: FAIL with `ModuleNotFoundError: No module named 'export'`

- [ ] **Step 3: Implement export.py**

```python
# ml/export.py
"""Binary model export + reference scorer (must match SpamModel.kt)."""
from __future__ import annotations

import json
import math
import struct
from pathlib import Path

import numpy as np

from features import feature_buckets

MAGIC = b"NSPM"
VERSION = 1


def sigmoid(z: float) -> float:
    if z >= 0:
        return 1.0 / (1.0 + math.exp(-z))
    e = math.exp(z)
    return e / (1.0 + e)


def quantise(weights: np.ndarray) -> tuple[np.ndarray, float]:
    max_abs = float(np.max(np.abs(weights))) if weights.size else 0.0
    scale = max_abs / 127.0 if max_abs > 0 else 1.0
    q = np.clip(np.rint(weights / scale), -127, 127).astype(np.int8)
    return q, scale


def write_model(
    path: str | Path,
    weights: np.ndarray,
    bias: float,
    buckets: int,
    ngram_min: int,
    ngram_max: int,
) -> tuple[np.ndarray, float]:
    weights = np.asarray(weights, dtype=np.float32).reshape(-1)
    assert weights.shape[0] == buckets
    q, scale = quantise(weights)
    scale32 = np.float32(scale)
    header = struct.pack(">4siiiiff", MAGIC, VERSION, buckets, ngram_min, ngram_max, float(bias), float(scale32))
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    with open(path, "wb") as f:
        f.write(header)
        f.write(q.tobytes())
    deq = (q.astype(np.float32) * scale32).astype(np.float32)
    return deq, float(scale32)


def score(text: str, weights_dequant: np.ndarray, bias: float) -> float:
    """Reference scorer using dequantised float32 weights (what the app computes)."""
    counts = feature_buckets(text)
    if not counts:
        return sigmoid(bias)
    norm = math.sqrt(sum(c * c for c in counts.values()))
    z = float(np.float32(bias))
    for k, c in counts.items():
        z += float(weights_dequant[k]) * (c / norm)
    return sigmoid(z)


def write_parity(path: str | Path, texts: list[str], weights_dequant: np.ndarray, bias: float) -> None:
    rows = [{"text": t, "score": score(t, weights_dequant, bias)} for t in texts]
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    Path(path).write_text(json.dumps(rows, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd ml && uv run --group dev pytest -q test_export.py test_features.py`
Expected: `7 passed`

- [ ] **Step 5: Write train.py**

```python
# ml/train.py
"""Train the spam classifier and export it into the app.

Usage:  cd ml && uv run train.py            # downloads data on first run
"""
from __future__ import annotations

import argparse
import sys
import time
import urllib.request
from pathlib import Path

import numpy as np
from scipy.sparse import csr_matrix
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import classification_report, confusion_matrix
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import normalize as l2_normalize

from export import write_model, write_parity
from features import BUCKETS, NGRAM_MAX, NGRAM_MIN, feature_buckets

ROOT = Path(__file__).resolve().parent
DATA_URL = (
    "https://raw.githubusercontent.com/hrwhisper/SpamMessage/master/data/"
    "%E5%B8%A6%E6%A0%87%E7%AD%BE%E7%9F%AD%E4%BF%A1.txt"
)
DATA_PATH = ROOT / "data" / "labeled_sms.txt"
MODEL_OUT = ROOT.parent / "app/src/main/resources/model/spam_v1.bin"
PARITY_OUT = ROOT.parent / "app/src/test/resources/model/parity.json"

PARITY_TEXTS = [
    "",
    "你好",
    "您的验证码是 483920，5分钟内有效，请勿泄露。",
    "【XX商城】双11狂欢！全场1折起，点击 http://t.cn/abc 领取888元红包，回复TD退订",
    "妈妈说周末回家吃饭",
    "恭喜您被抽中为幸运用户，加微信 abc123 领取iPhone 15 Pro Max",
    "Your package has been delivered to the front desk.",
    "低息贷款  无抵押　当天放款 详询 138-0000-0000",
    "会议改到下午三点，地点不变",
    "😀🎉 限时特惠！！！",
    "A",
]


def download() -> None:
    if DATA_PATH.exists():
        return
    DATA_PATH.parent.mkdir(parents=True, exist_ok=True)
    print(f"downloading {DATA_URL} -> {DATA_PATH}", file=sys.stderr)
    urllib.request.urlretrieve(DATA_URL, DATA_PATH)


def load() -> tuple[list[str], np.ndarray]:
    texts, labels = [], []
    with open(DATA_PATH, encoding="utf-8", errors="replace") as f:
        for line in f:
            line = line.rstrip("\n")
            if not line or "\t" not in line:
                continue
            label, text = line.split("\t", 1)
            if label not in ("0", "1"):
                continue
            texts.append(text)
            labels.append(int(label))
    return texts, np.asarray(labels, dtype=np.int8)


def vectorise(texts: list[str]) -> csr_matrix:
    indptr = [0]
    indices: list[int] = []
    data: list[float] = []
    t0 = time.time()
    for i, t in enumerate(texts):
        b = feature_buckets(t, NGRAM_MIN, NGRAM_MAX, BUCKETS)
        indices.extend(b.keys())
        data.extend(b.values())
        indptr.append(len(indices))
        if i and i % 100000 == 0:
            print(f"  vectorised {i} ({time.time() - t0:.0f}s)", file=sys.stderr)
    x = csr_matrix((np.asarray(data, dtype=np.float32), np.asarray(indices), np.asarray(indptr)), shape=(len(texts), BUCKETS))
    return l2_normalize(x, norm="l2", copy=False)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--C", type=float, default=4.0)
    ap.add_argument("--limit", type=int, default=0, help="use only the first N rows (smoke test)")
    args = ap.parse_args()

    download()
    texts, y = load()
    if args.limit:
        texts, y = texts[: args.limit], y[: args.limit]
    print(f"rows={len(texts)} spam={int(y.sum())} ham={int((y == 0).sum())}", file=sys.stderr)

    x = vectorise(texts)
    x_tr, x_te, y_tr, y_te = train_test_split(x, y, test_size=0.2, random_state=42, stratify=y)

    clf = LogisticRegression(C=args.C, solver="liblinear", max_iter=1000)
    clf.fit(x_tr, y_tr)

    p = clf.predict_proba(x_te)[:, 1]
    for thr in (0.5, 0.8, 0.9, 0.95):
        pred = (p >= thr).astype(int)
        print(f"\n== threshold {thr} ==", file=sys.stderr)
        print(confusion_matrix(y_te, pred), file=sys.stderr)
        print(classification_report(y_te, pred, digits=4), file=sys.stderr)

    w = clf.coef_.reshape(-1).astype(np.float32)
    b = float(clf.intercept_[0])
    deq, scale = write_model(MODEL_OUT, w, b, BUCKETS, NGRAM_MIN, NGRAM_MAX)
    write_parity(PARITY_OUT, PARITY_TEXTS, deq, b)

    # Report quantisation loss on the test split.
    zq = x_te @ deq + b
    pq = 1.0 / (1.0 + np.exp(-zq))
    drift = float(np.max(np.abs(pq - p)))
    print(f"\nwrote {MODEL_OUT} ({MODEL_OUT.stat().st_size} bytes) scale={scale:.6g} max|Δp| after int8={drift:.4f}", file=sys.stderr)
    print(f"wrote {PARITY_OUT}", file=sys.stderr)


if __name__ == "__main__":
    main()
```

- [ ] **Step 6: Add .gitignore and README**

```gitignore
# ml/.gitignore
data/
.venv/
__pycache__/
.pytest_cache/
```

```markdown
# ml — 骚扰通知分类模型训练

`cd ml && uv run train.py` 会：

1. 下载中文垃圾短信数据集（约 80 万条，`label\tcontent`，1 = 骚扰）到 `ml/data/`（已存在则跳过）。
2. 用与 `SpamFeatures.kt` 完全一致的字符 1–3 gram + FNV-1a 哈希（2^18 桶）特征训练 Logistic Regression。
3. 打印多个阈值下的混淆矩阵与 precision / recall。
4. 导出 `app/src/main/resources/model/spam_v1.bin`（int8 量化，约 256 KB）和
   `app/src/test/resources/model/parity.json`（Kotlin 单测用，校验两端打分一致）。

测试：`uv run --group dev pytest -q`

快速冒烟：`uv run train.py --limit 20000`（不要提交冒烟模型）。
```

- [ ] **Step 7: Smoke-train on a subset, then full train**

Run: `cd ml && uv run train.py --limit 20000`
Expected: prints confusion matrices, writes both output files, `max|Δp| after int8` < 0.05.

Run: `cd ml && uv run train.py`
Expected: full run (a few minutes). Precision and recall for class 1 at threshold 0.5 both ≥ 0.95. `app/src/main/resources/model/spam_v1.bin` ≈ 262,172 bytes.

- [ ] **Step 8: Stage**

```bash
git add ml/train.py ml/export.py ml/test_export.py ml/.gitignore ml/README.md app/src/main/resources/model/spam_v1.bin app/src/test/resources/model/parity.json
```

---

### Task 4: Kotlin `SpamModel` loader + parity test

**Files:**
- Create: `app/src/main/java/moe/notice/filter/domain/SpamModel.kt`
- Test: `app/src/test/java/moe/notice/filter/domain/SpamModelTest.kt`

**Interfaces:**
- Consumes: `SpamFeatures.buckets(text, ngramMin, ngramMax, buckets)`.
- Produces: `class SpamModel(val buckets: Int, val ngramMin: Int, val ngramMax: Int, val bias: Float, val weights: FloatArray) { fun score(text: String): Float; companion object { const val RESOURCE = "model/spam_v1.bin"; fun load(input: InputStream): SpamModel; fun bundled(): SpamModel? } }`

- [ ] **Step 1: Write the failing tests**

```kotlin
// app/src/test/java/moe/notice/filter/domain/SpamModelTest.kt
package moe.notice.filter.domain

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class SpamModelTest {
    @Test
    fun loadsHandBuiltBinary() {
        val bytes = ByteArrayOutputStream().also { bos ->
            DataOutputStream(bos).apply {
                write("NSPM".toByteArray(Charsets.US_ASCII))
                writeInt(1)
                writeInt(8)
                writeInt(1)
                writeInt(1)
                writeFloat(0.5f)
                writeFloat(0.01f)
                write(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0))
            }
        }.toByteArray()
        val m = SpamModel.load(bytes.inputStream())
        assertEquals(8, m.buckets)
        assertEquals(1, m.ngramMin)
        assertEquals(1, m.ngramMax)
        assertEquals(0.5f, m.bias, 0f)
        assertEquals(sigmoid(0.5f), m.score("anything"), 1e-6f)
        assertEquals(sigmoid(0.5f), m.score(""), 1e-6f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBadMagic() {
        SpamModel.load(ByteArray(28).inputStream())
    }

    @Test
    fun bundledModelMatchesPythonParity() {
        val model = SpamModel.bundled()
        assertNotNull("model/spam_v1.bin missing from resources", model)
        val json = javaClass.classLoader!!.getResourceAsStream("model/parity.json")!!
            .bufferedReader().readText()
        val rows = JSONArray(json)
        assertTrue(rows.length() > 5)
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            val text = row.getString("text")
            val expected = row.getDouble("score").toFloat()
            assertEquals("row $i: $text", expected, model!!.score(text), 1e-3f)
        }
    }

    private fun sigmoid(z: Float): Float = (1.0 / (1.0 + Math.exp(-z.toDouble()))).toFloat()
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `sh ./gradlew :app:testDebugUnitTest --tests 'moe.notice.filter.domain.SpamModelTest' -q`
Expected: compilation FAIL, `Unresolved reference: SpamModel`

- [ ] **Step 3: Implement SpamModel.kt**

```kotlin
// app/src/main/java/moe/notice/filter/domain/SpamModel.kt
package moe.notice.filter.domain

import java.io.DataInputStream
import java.io.InputStream
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Logistic-regression spam scorer over [SpamFeatures] hashed n-grams.
 * Binary format (big-endian): "NSPM", int32 version=1, int32 buckets, int32 ngramMin,
 * int32 ngramMax, float32 bias, float32 scale, then `buckets` int8 weights (w = q * scale).
 */
class SpamModel(
    val buckets: Int,
    val ngramMin: Int,
    val ngramMax: Int,
    val bias: Float,
    val weights: FloatArray,
) {
    init {
        require(weights.size == buckets) { "weights ${weights.size} != buckets $buckets" }
        require(buckets > 0 && buckets and (buckets - 1) == 0) { "buckets must be a power of two" }
    }

    /** Probability in [0, 1] that [text] is spam. */
    fun score(text: String): Float {
        val counts = SpamFeatures.buckets(text, ngramMin, ngramMax, buckets)
        var z = bias
        if (counts.isNotEmpty()) {
            var sq = 0.0
            for (c in counts.values) sq += (c.toDouble() * c)
            val norm = sqrt(sq)
            for ((k, c) in counts) z += weights[k] * (c / norm).toFloat()
        }
        return sigmoid(z)
    }

    companion object {
        const val RESOURCE = "model/spam_v1.bin"
        private const val VERSION = 1
        private val MAGIC = byteArrayOf('N'.code.toByte(), 'S'.code.toByte(), 'P'.code.toByte(), 'M'.code.toByte())

        @Volatile private var bundledLoaded = false
        @Volatile private var bundledModel: SpamModel? = null

        fun load(input: InputStream): SpamModel {
            val din = DataInputStream(input.buffered())
            val magic = ByteArray(4)
            din.readFully(magic)
            require(magic.contentEquals(MAGIC)) { "bad magic" }
            val version = din.readInt()
            require(version == VERSION) { "unsupported version $version" }
            val buckets = din.readInt()
            val ngramMin = din.readInt()
            val ngramMax = din.readInt()
            val bias = din.readFloat()
            val scale = din.readFloat()
            require(buckets in 1..(1 shl 24)) { "bad bucket count $buckets" }
            val q = ByteArray(buckets)
            din.readFully(q)
            val weights = FloatArray(buckets) { q[it] * scale }
            return SpamModel(buckets, ngramMin, ngramMax, bias, weights)
        }

        /** The model packaged in the APK, loaded once; null when missing or corrupt. */
        fun bundled(): SpamModel? {
            if (bundledLoaded) return bundledModel
            synchronized(this) {
                if (bundledLoaded) return bundledModel
                bundledModel = runCatching {
                    SpamModel::class.java.classLoader?.getResourceAsStream(RESOURCE)?.use { load(it) }
                }.getOrNull()
                bundledLoaded = true
                return bundledModel
            }
        }

        private fun sigmoid(z: Float): Float {
            val zd = z.toDouble()
            return if (zd >= 0) {
                (1.0 / (1.0 + exp(-zd))).toFloat()
            } else {
                val e = exp(zd)
                (e / (1.0 + e)).toFloat()
            }
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `sh ./gradlew :app:testDebugUnitTest --tests 'moe.notice.filter.domain.SpamModelTest' -q`
Expected: BUILD SUCCESSFUL, no failures. If `bundledModelMatchesPythonParity` fails on a specific row, the Python and Kotlin featurisation diverge for that text — fix `normalize`/hashing (never loosen the tolerance).

- [ ] **Step 5: Stage**

```bash
git add app/src/main/java/moe/notice/filter/domain/SpamModel.kt app/src/test/java/moe/notice/filter/domain/SpamModelTest.kt
```

---

### Task 5: `FilterConfig` fields + codec round-trip

**Files:**
- Modify: `app/src/main/java/moe/notice/filter/domain/FilterConfig.kt`
- Modify: `app/src/main/java/moe/notice/filter/data/FilterConfigCodec.kt:13-37`
- Test: `app/src/test/java/moe/notice/filter/data/FilterConfigCodecTest.kt`

**Interfaces:**
- Produces: `FilterConfig(spamEnabled: Boolean = false, spamThreshold: Float = 0.9f)`; JSON keys `"spamEnabled"`, `"spamThreshold"`.

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/java/moe/notice/filter/data/FilterConfigCodecTest.kt
package moe.notice.filter.data

import moe.notice.filter.domain.FilterConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterConfigCodecTest {
    @Test
    fun roundTripsSpamFields() {
        val config = FilterConfig(enabled = true, spamEnabled = true, spamThreshold = 0.73f)
        val decoded = FilterConfigCodec.decode(FilterConfigCodec.encode(config))
        assertTrue(decoded.spamEnabled)
        assertEquals(0.73f, decoded.spamThreshold, 1e-6f)
    }

    @Test
    fun legacyJsonWithoutSpamFieldsUsesDefaults() {
        val decoded = FilterConfigCodec.decode("""{"enabled":true,"logEnabled":false,"rules":[]}""")
        assertFalse(decoded.spamEnabled)
        assertEquals(0.9f, decoded.spamThreshold, 1e-6f)
    }

    @Test
    fun clampsThresholdIntoRange() {
        val decoded = FilterConfigCodec.decode("""{"spamThreshold":7}""")
        assertEquals(0.99f, decoded.spamThreshold, 1e-6f)
        val low = FilterConfigCodec.decode("""{"spamThreshold":-1}""")
        assertEquals(0.5f, low.spamThreshold, 1e-6f)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `sh ./gradlew :app:testDebugUnitTest --tests 'moe.notice.filter.data.FilterConfigCodecTest' -q`
Expected: compilation FAIL, `No parameter with name 'spamEnabled'`

- [ ] **Step 3: Add the fields**

```kotlin
// app/src/main/java/moe/notice/filter/domain/FilterConfig.kt
package moe.notice.filter.domain

data class FilterConfig(
    val enabled: Boolean = false,
    val logEnabled: Boolean = true,
    val rules: List<BlockRule> = emptyList(),
    val spamEnabled: Boolean = false,
    val spamThreshold: Float = DEFAULT_SPAM_THRESHOLD,
) {
    companion object {
        const val DEFAULT_SPAM_THRESHOLD = 0.9f
        const val MIN_SPAM_THRESHOLD = 0.5f
        const val MAX_SPAM_THRESHOLD = 0.99f
    }
}
```

In `FilterConfigCodec.encode`, after `root.put("logEnabled", config.logEnabled)` add:

```kotlin
        root.put("spamEnabled", config.spamEnabled)
        root.put("spamThreshold", config.spamThreshold.toDouble())
```

In `FilterConfigCodec.decode`, replace the `return FilterConfig(...)` with:

```kotlin
        return FilterConfig(
            enabled = root.optBoolean("enabled", false),
            logEnabled = root.optBoolean("logEnabled", true),
            rules = rules,
            spamEnabled = root.optBoolean("spamEnabled", false),
            spamThreshold = root.optDouble("spamThreshold", FilterConfig.DEFAULT_SPAM_THRESHOLD.toDouble())
                .toFloat()
                .coerceIn(FilterConfig.MIN_SPAM_THRESHOLD, FilterConfig.MAX_SPAM_THRESHOLD),
        )
```

- [ ] **Step 4: Run test to verify it passes**

Run: `sh ./gradlew :app:testDebugUnitTest --tests 'moe.notice.filter.data.FilterConfigCodecTest' -q`
Expected: BUILD SUCCESSFUL, no failures.

- [ ] **Step 5: Stage**

```bash
git add app/src/main/java/moe/notice/filter/domain/FilterConfig.kt app/src/main/java/moe/notice/filter/data/FilterConfigCodec.kt app/src/test/java/moe/notice/filter/data/FilterConfigCodecTest.kt
```

---

### Task 6: `SpamJudge` decision helper + hook into `KeywordFilter`

**Files:**
- Create: `app/src/main/java/moe/notice/filter/domain/SpamJudge.kt`
- Test: `app/src/test/java/moe/notice/filter/domain/SpamJudgeTest.kt`
- Modify: `app/src/main/java/moe/notice/filter/xposed/KeywordFilter.kt:43-97`

**Interfaces:**
- Consumes: `SpamModel.score`, `SpamFeatures.normalize`, `FilterConfig.spamEnabled/spamThreshold`.
- Produces: `object SpamJudge { const val MIN_LENGTH = 4; const val RULE_ID = "spam_model"; const val RULE_NAME = "智能识别骚扰"; val rule: BlockRule; data class Verdict(val score: Float, val block: Boolean); fun judge(model: SpamModel, threshold: Float, text: String): Verdict? }` — returns `null` when the text is too short to judge.

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/java/moe/notice/filter/domain/SpamJudgeTest.kt
package moe.notice.filter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpamJudgeTest {
    private fun constantModel(bias: Float) =
        SpamModel(buckets = 8, ngramMin = 1, ngramMax = 1, bias = bias, weights = FloatArray(8))

    @Test
    fun skipsShortText() {
        assertNull(SpamJudge.judge(constantModel(10f), 0.5f, "abc"))
        assertNull(SpamJudge.judge(constantModel(10f), 0.5f, "  a  b  "))
        assertNotNull(SpamJudge.judge(constantModel(10f), 0.5f, "abcd"))
    }

    @Test
    fun blocksAtOrAboveThreshold() {
        val high = SpamJudge.judge(constantModel(10f), 0.9f, "hello world")!!
        assertTrue(high.block)
        assertTrue(high.score > 0.99f)

        val low = SpamJudge.judge(constantModel(-10f), 0.9f, "hello world")!!
        assertFalse(low.block)
        assertTrue(low.score < 0.01f)
    }

    @Test
    fun syntheticRuleIsStable() {
        assertEquals("spam_model", SpamJudge.rule.id)
        assertEquals("智能识别骚扰", SpamJudge.rule.name)
        assertTrue(SpamJudge.rule.enabled)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `sh ./gradlew :app:testDebugUnitTest --tests 'moe.notice.filter.domain.SpamJudgeTest' -q`
Expected: compilation FAIL, `Unresolved reference: SpamJudge`

- [ ] **Step 3: Implement SpamJudge.kt**

```kotlin
// app/src/main/java/moe/notice/filter/domain/SpamJudge.kt
package moe.notice.filter.domain

/** Applies the spam model with a threshold; the synthetic [rule] is what shows up in logs. */
object SpamJudge {
    /** Normalised texts shorter than this are not judged (too little signal). */
    const val MIN_LENGTH = 4
    const val RULE_ID = "spam_model"
    const val RULE_NAME = "智能识别骚扰"

    val rule: BlockRule = BlockRule(
        id = RULE_ID,
        name = RULE_NAME,
        enabled = true,
        mode = MatchMode.ALL_CONTENT,
    )

    data class Verdict(val score: Float, val block: Boolean)

    fun judge(model: SpamModel, threshold: Float, text: String): Verdict? {
        if (SpamFeatures.normalize(text).length < MIN_LENGTH) return null
        val score = model.score(text)
        return Verdict(score = score, block = score >= threshold)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `sh ./gradlew :app:testDebugUnitTest --tests 'moe.notice.filter.domain.SpamJudgeTest' -q`
Expected: BUILD SUCCESSFUL, no failures.

- [ ] **Step 5: Hook into KeywordFilter.shouldBlock**

Add imports at the top of `KeywordFilter.kt`:

```kotlin
import moe.notice.filter.domain.SpamJudge
import moe.notice.filter.domain.SpamModel
```

Replace the block from `val hit = if (!config.enabled) {` through `Xp.log(formatJudgeLog(resolved, extracted.combined, hit))` with:

```kotlin
        val cfg = config
        val ruleHit = if (!cfg.enabled) {
            null
        } else {
            RuleMatcher.firstMatch(cfg.rules, resolved, extracted.combined)
        }
        var verdict: SpamJudge.Verdict? = null
        var hit = ruleHit
        if (hit == null && cfg.enabled && cfg.spamEnabled) {
            verdict = try {
                SpamModel.bundled()?.let { SpamJudge.judge(it, cfg.spamThreshold, extracted.combined) }
            } catch (t: Throwable) {
                Xp.log("spam model failed", t)
                null
            }
            if (verdict?.block == true) hit = SpamJudge.rule
        }
        Xp.log(formatJudgeLog(resolved, extracted.combined, hit, verdict))
```

Change `formatJudgeLog` signature and result line:

```kotlin
    private fun formatJudgeLog(
        pkg: String,
        text: String,
        hit: BlockRule?,
        verdict: SpamJudge.Verdict?,
    ): String {
```

and replace `return "judge enabled=…"` with:

```kotlin
        val spam = if (verdict == null) "" else " spam=%.3f".format(java.util.Locale.ROOT, verdict.score)
        return "judge enabled=${config.enabled} spamEnabled=${config.spamEnabled} rules={$rules} pkg=$pkg result=$result$spam text=$snippet"
```

Also update `logConfig` summary so config reloads are visible:

```kotlin
        val summary = "enabled=${config.enabled} log=${config.logEnabled} spam=${config.spamEnabled}@${config.spamThreshold} rules=${config.rules.size} $source"
```

- [ ] **Step 6: Compile the module**

Run: `sh ./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Stage**

```bash
git add app/src/main/java/moe/notice/filter/domain/SpamJudge.kt app/src/test/java/moe/notice/filter/domain/SpamJudgeTest.kt app/src/main/java/moe/notice/filter/xposed/KeywordFilter.kt
```

---

### Task 7: Repository/ViewModel setters and Settings UI

**Files:**
- Modify: `app/src/main/java/moe/notice/filter/data/RuleRepository.kt:49-51`
- Modify: `app/src/main/java/moe/notice/filter/ui/NoticeViewModel.kt:56-62`
- Modify: `app/src/main/java/moe/notice/filter/ui/NoticeApp.kt:211-212, 239-240, 510-516`
- Modify: `app/src/main/java/moe/notice/filter/ui/components/SettingsItems.kt` (add `SettingSliderRow`)
- Modify: `app/src/main/java/moe/notice/filter/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `FilterConfig.spamEnabled/spamThreshold`, `FilterConfig.MIN_SPAM_THRESHOLD/MAX_SPAM_THRESHOLD`, `RuleRepository.save(config)`.
- Produces: `RuleRepository.setSpamEnabled(Boolean): Boolean`, `RuleRepository.setSpamThreshold(Float): Boolean`, `NoticeViewModel.setSpamEnabled(Boolean)`, `NoticeViewModel.setSpamThreshold(Float)`, `SettingsScreen(onSpamEnabledChange: (Boolean) -> Unit, onSpamThresholdChange: (Float) -> Unit)`.

- [ ] **Step 1: Repository setters**

In `RuleRepository.kt`, after `fun setLogEnabled(...)` add:

```kotlin
    fun setSpamEnabled(spamEnabled: Boolean): Boolean = save(_config.value.copy(spamEnabled = spamEnabled))

    fun setSpamThreshold(threshold: Float): Boolean = save(
        _config.value.copy(
            spamThreshold = threshold.coerceIn(FilterConfig.MIN_SPAM_THRESHOLD, FilterConfig.MAX_SPAM_THRESHOLD),
        ),
    )
```

(`FilterConfig` is already imported in `RuleRepository.kt`; verify with `grep -n "import moe.notice.filter.domain.FilterConfig" app/src/main/java/moe/notice/filter/data/RuleRepository.kt` and add the import if missing.)

- [ ] **Step 2: ViewModel setters**

In `NoticeViewModel.kt`, after `fun setLogEnabled(...)` add:

```kotlin
    fun setSpamEnabled(enabled: Boolean) {
        report(rules.setSpamEnabled(enabled))
    }

    fun setSpamThreshold(threshold: Float) {
        report(rules.setSpamThreshold(threshold))
    }
```

- [ ] **Step 3: Strings**

In `app/src/main/res/values/strings.xml`, right after the `enable_log` string add:

```xml
    <string name="enable_spam_model">智能识别骚扰</string>
    <string name="enable_spam_model_hint">规则未命中时，用离线模型识别推广、诈骗等骚扰通知</string>
    <string name="spam_threshold">拦截阈值</string>
    <string name="spam_threshold_hint">骚扰概率高于此值即拦截，数值越低越激进</string>
```

- [ ] **Step 4: Slider row component**

Append to `SettingsItems.kt` (add imports `androidx.compose.material3.Slider`, `androidx.compose.runtime.getValue`, `androidx.compose.runtime.mutableFloatStateOf`, `androidx.compose.runtime.remember`, `androidx.compose.runtime.setValue`, `androidx.compose.runtime.LaunchedEffect`, `java.util.Locale`):

```kotlin
/** Segmented list item with a title, live value label and a slider; commits on release. */
@Composable
fun SettingSliderRow(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    shape: Shape,
    onValueCommit: (Float) -> Unit,
    icon: ImageVector? = null,
    supporting: String? = null,
) {
    var local by remember { mutableFloatStateOf(value) }
    LaunchedEffect(value) { local = value }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(text = title, style = MaterialTheme.typography.titleMedium)
                    if (supporting != null) {
                        Text(
                            text = supporting,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = String.format(Locale.ROOT, "%.2f", local),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = local,
                onValueChange = { local = it },
                onValueChangeFinished = { onValueCommit(local) },
                valueRange = valueRange,
                steps = steps,
                modifier = Modifier.padding(start = if (icon != null) 40.dp else 0.dp),
            )
        }
    }
}
```

- [ ] **Step 5: SettingsScreen**

Change the signature and the "通用" section of `SettingsScreen.kt`. New signature:

```kotlin
@Composable
fun SettingsScreen(
    config: FilterConfig,
    onEnabledChange: (Boolean) -> Unit,
    onLogEnabledChange: (Boolean) -> Unit,
    onSpamEnabledChange: (Boolean) -> Unit,
    onSpamThresholdChange: (Float) -> Unit,
    onRequestTest: () -> Unit,
    contentPadding: PaddingValues,
)
```

Add imports `androidx.compose.material.icons.outlined.AutoAwesome`, `androidx.compose.material.icons.outlined.Tune`, `moe.notice.filter.ui.components.SettingSliderRow`.

Replace the two `item { SettingSwitchRow(...) }` blocks under `section_general` with:

```kotlin
        val generalCount = if (config.spamEnabled) 4 else 3
        item {
            SettingSwitchRow(
                icon = Icons.Outlined.FilterAlt,
                title = stringResource(R.string.enable_filter),
                supporting = stringResource(R.string.enable_filter_hint),
                checked = config.enabled,
                shape = groupedListShape(0, generalCount),
                onCheckedChange = onEnabledChange,
            )
        }
        item {
            SettingSwitchRow(
                icon = Icons.Outlined.History,
                title = stringResource(R.string.enable_log),
                supporting = stringResource(R.string.enable_log_hint),
                checked = config.logEnabled,
                shape = groupedListShape(1, generalCount),
                onCheckedChange = onLogEnabledChange,
            )
        }
        item {
            SettingSwitchRow(
                icon = Icons.Outlined.AutoAwesome,
                title = stringResource(R.string.enable_spam_model),
                supporting = stringResource(R.string.enable_spam_model_hint),
                checked = config.spamEnabled,
                shape = groupedListShape(2, generalCount),
                onCheckedChange = onSpamEnabledChange,
            )
        }
        if (config.spamEnabled) {
            item {
                SettingSliderRow(
                    icon = Icons.Outlined.Tune,
                    title = stringResource(R.string.spam_threshold),
                    supporting = stringResource(R.string.spam_threshold_hint),
                    value = config.spamThreshold,
                    valueRange = FilterConfig.MIN_SPAM_THRESHOLD..FilterConfig.MAX_SPAM_THRESHOLD,
                    steps = 48,
                    shape = groupedListShape(3, generalCount),
                    onValueCommit = onSpamThresholdChange,
                )
            }
        }
```

- [ ] **Step 6: Wire NoticeApp**

In `NoticeApp.kt`:
- Around line 211–212, after `onLogEnabledChange = viewModel::setLogEnabled,` add
  `onSpamEnabledChange = viewModel::setSpamEnabled,` and `onSpamThresholdChange = viewModel::setSpamThreshold,`.
- In `HomeScaffold` parameters (around line 239–240), after `onLogEnabledChange: (Boolean) -> Unit,` add
  `onSpamEnabledChange: (Boolean) -> Unit,` and `onSpamThresholdChange: (Float) -> Unit,`.
- In the `else -> SettingsScreen(` call (around line 510–516), after `onLogEnabledChange = onLogEnabledChange,` add
  `onSpamEnabledChange = onSpamEnabledChange,` and `onSpamThresholdChange = onSpamThresholdChange,`.

- [ ] **Step 7: Build and run the full unit test suite**

Run: `sh ./gradlew :app:testDebugUnitTest :app:assembleDebug -q`
Expected: BUILD SUCCESSFUL; all tests in `SpamFeaturesTest`, `SpamModelTest`, `FilterConfigCodecTest`, `SpamJudgeTest` pass; `app/build/outputs/apk/debug/app-debug.apk` exists.

Check the model was packaged: `unzip -l app/build/outputs/apk/debug/app-debug.apk | grep model/spam_v1.bin` → one line, size ≈ 262172.

- [ ] **Step 8: Stage**

```bash
git add app/src/main/java/moe/notice/filter/data/RuleRepository.kt app/src/main/java/moe/notice/filter/ui/NoticeViewModel.kt app/src/main/java/moe/notice/filter/ui/NoticeApp.kt app/src/main/java/moe/notice/filter/ui/components/SettingsItems.kt app/src/main/java/moe/notice/filter/ui/settings/SettingsScreen.kt app/src/main/res/values/strings.xml
```

---

## Self-review notes

- Spec coverage: §1 → Tasks 1, 3; §2 → Tasks 2, 4, 5, 6; §3 → Task 7; §4 → Tasks 2–7 (tests) and Task 7 step 7 (verification command).
- The spec's "model load failure → no block" is covered by `SpamModel.bundled()` returning null and the `try/catch` in `KeywordFilter`.
- Type consistency: `SpamJudge.judge(model, threshold, text)` and `SpamModel.score(text)` are used with the same signatures in Tasks 4, 6; `FilterConfig.MIN/MAX_SPAM_THRESHOLD` are defined in Task 5 and consumed in Tasks 5, 7.
