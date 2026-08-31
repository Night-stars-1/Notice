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
