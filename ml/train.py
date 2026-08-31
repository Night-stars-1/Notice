"""Train the spam classifier and export it into the app.

Usage:  cd ml && uv run train.py            # downloads data on first run
"""
from __future__ import annotations

import argparse
import csv
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
# Small English SMS Spam Collection (UCI), adds Latin-script coverage.
EN_DATA_URL = (
    "https://raw.githubusercontent.com/mohitgupta-omg/"
    "Kaggle-SMS-Spam-Collection-Dataset-/master/spam.csv"
)
EN_DATA_PATH = ROOT / "data" / "spam_en.csv"
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
    for url, path in ((DATA_URL, DATA_PATH), (EN_DATA_URL, EN_DATA_PATH)):
        if path.exists():
            continue
        path.parent.mkdir(parents=True, exist_ok=True)
        print(f"downloading {url} -> {path}", file=sys.stderr)
        urllib.request.urlretrieve(url, path)


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
    zh = len(texts)
    with open(EN_DATA_PATH, encoding="latin-1", newline="") as f:
        for row in csv.reader(f):
            if len(row) < 2 or row[0] not in ("ham", "spam"):
                continue
            texts.append(row[1])
            labels.append(1 if row[0] == "spam" else 0)
    print(f"loaded zh={zh} en={len(texts) - zh}", file=sys.stderr)
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
    x = csr_matrix(
        (np.asarray(data, dtype=np.float32), np.asarray(indices), np.asarray(indptr)),
        shape=(len(texts), BUCKETS),
    )
    return l2_normalize(x, norm="l2", copy=False)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--C", type=float, default=1.0)
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
    print(
        f"\nwrote {MODEL_OUT} ({MODEL_OUT.stat().st_size} bytes) scale={scale:.6g} max|Δp| after int8={drift:.4f}",
        file=sys.stderr,
    )
    print(f"wrote {PARITY_OUT}", file=sys.stderr)


if __name__ == "__main__":
    main()
