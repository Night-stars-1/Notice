from features import normalize, fnv1a32, feature_buckets, BUCKETS


def test_normalize_lower_digits_spaces():
    assert normalize("  Hello　World  123 ") == "helloworld"
    assert normalize("ＡＢＣ１２３") == "ａｂｃ"
    assert normalize("a\t\n b") == "ab"
    assert normalize("\u00a0\u3000") == ""
    assert normalize("") == ""
    assert normalize("Xbox 360") == "bo"


def test_fnv1a32_known_vectors():
    assert fnv1a32(b"") == 0x811C9DC5
    assert fnv1a32(b"a") == 0xE40C292C
    assert fnv1a32(b"foobar") == 0xBF9CF968


def test_feature_buckets_unigram_counts():
    # "aa" -> unigram 为 a,a；bigram 为 aa  => 2 个不同的桶，计数分别为 2 和 1
    b = feature_buckets("aa")
    assert sorted(b.values()) == [1, 2]
    assert all(0 <= k < BUCKETS for k in b)


def test_feature_buckets_uses_utf16_units():
    # U+1F600 是一个代理对 -> 2 个代码单元 -> unigram：2 个，bigram：1 个
    b = feature_buckets("\U0001F600")
    assert sum(b.values()) == 3
