# ml — 骚扰通知分类模型训练

`cd ml && uv run train.py` 会：

1. 下载中文垃圾短信数据集（约 80 万条，`label\tcontent`，1 = 骚扰）和英文 SMS Spam Collection（约 5.5k 条）到 `ml/data/`（已存在则跳过）。
2. 用与 `SpamFeatures.kt` 完全一致的归一化（小写、去空白、去数字、去字母 x，避免学到语料的脱敏痕迹）+ 字符 1–3 gram + FNV-1a 哈希（2^18 桶）特征训练 Logistic Regression。
3. 打印多个阈值下的混淆矩阵与 precision / recall。
4. 导出 `app/src/main/resources/model/spam_v1.bin`（int8 量化，约 256 KB）和
   `app/src/test/resources/model/parity.json`（Kotlin 单测用，校验两端打分一致）。

测试：`uv run --group dev pytest -q`

快速冒烟：`uv run train.py --limit 20000`（不要提交冒烟模型）。

## 已知局限

- 中文语料的正常样本偏新闻/句子片段而非真实聊天或 App 通知，"你好"之类的短问候会偏高分；4 个字以下的文本不会被判定。
- 语料中没有真实验证码短信，模型对「验证码……请勿泄露」会给高分；App 端 `SpamJudge` 对含"验证码 / verification code / OTP"等字样的通知硬性放行。
- 默认阈值 0.9（留出集 precision ≈ 1.0，recall ≈ 0.91）；实际通知分布不同，遇到误杀先调高阈值。
