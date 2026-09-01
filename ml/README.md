# ml — 骚扰通知分类模型训练

`cd ml && uv run train.py` 会：

1. 下载中文垃圾短信数据集（约 80 万条，`label\tcontent`，1 = 骚扰）和英文 SMS Spam Collection（约 5.5k 条）到 `ml/data/`（已存在则跳过）。
2. 用与 `SpamFeatures.kt` 完全一致的归一化（小写、去空白、去数字、去字母 x，避免学到语料的脱敏痕迹）+ 字符 1–3 gram + FNV-1a 哈希（2^18 桶）特征训练 Logistic Regression。
3. 打印多个阈值下的混淆矩阵与 precision / recall。
4. 导出 `app/src/main/resources/model/spam_v1.bin`（int8 量化，约 256 KB）和
   `app/src/test/resources/model/parity.json`（Kotlin 单测用，校验两端打分一致）。

## 附加真实通知语料（推荐）

短信语料和 App 通知的分布差异很大，内置模型在真实通知上效果有限。可以导入 CSV
（列：`判定, 理由, 出现次数, App 名称, 通知标题, 通知信息, …`）作为附加语料：

```bash
uv run train.py --extra data/xxx.csv                 # 短信 + 英文 + 真实通知（真实样本权重 ×3）
uv run train.py --extra data/xxx.csv --extra-weight 10
uv run train.py --extra data/xxx.csv --no-sms --limit 20000   # 只用真实通知训练（--limit 只是少加载点短信）
uv run train.py --extra data/xxx.csv --no-extra-train          # 只拿真实通知做评估的对照基线
```

- 标签映射：`垃圾广告`、`骚扰` → 1，`正常` → 0；文本 = 标题 + 换行 + 正文（与 App 端一致）。
- 脚本会单独切出 20% 真实通知做留出集，输出 `[real notifications holdout]` 指标——**以这个为准**，短信留出集的指标不能反映实际效果。
- CSV 里是个人通知内容，放在 `ml/data/`（已被 .gitignore 忽略），不要提交。
- `--sms-ham-only`：短信 / 英文语料只保留正常样本当负样本。只用真实通知训练时召回最高，但模型先验会偏向骚扰
  （空文本、家常话都在 0.5–0.8），加入短信正常样本后校准正常，召回略降。
- 当前内置模型：`--sms-ham-only --limit 100000 --extra-weight 30 --C 10`。真实通知留出集上，
  阈值 0.8 → precision 0.97 / recall 0.74；阈值 0.9 → 0.98 / 0.67。

测试：`uv run --group dev pytest -q`

快速冒烟：`uv run train.py --limit 20000`（不要提交冒烟模型）。

## 已知局限

- 中文语料的正常样本偏新闻/句子片段而非真实聊天或 App 通知，"你好"之类的短问候会偏高分；4 个字以下的文本不会被判定。
- 语料中没有真实验证码短信，模型对「验证码……请勿泄露」会给高分；App 端 `SpamJudge` 对含"验证码 / verification code / OTP"等字样的通知硬性放行。
- 默认阈值 0.9（留出集 precision ≈ 1.0，recall ≈ 0.91）；实际通知分布不同，遇到误杀先调高阈值。
