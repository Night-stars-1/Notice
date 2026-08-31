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
