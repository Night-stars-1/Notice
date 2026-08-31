# 骚扰通知智能识别（端侧线性模型）设计

日期：2026-08-31

## 目标

在 Notice 中增加一个全局开关「智能识别骚扰」：开启后，未被任何规则命中的通知会经过一个端侧文本分类模型打分，概率 ≥ 阈值则拦截。模型完全离线运行在 `system_server` 内，无第三方依赖，无网络请求。

## 非目标

- 不做用户反馈标注、端侧增量学习。
- 不做云端/LLM 方案。
- 不改日志页、规则页；不新增 `MatchMode`。

## 组成

### 1. 训练管线 `ml/`

- `ml/pyproject.toml`：依赖 numpy、scikit-learn；用 `uv run` 执行。
- `ml/features.py`：与 Kotlin 端逐位一致的特征提取：
  - 归一化：`lowercase` → 删除所有空白字符、所有 Unicode 十进制数字（Nd）和字母 `x`。
    （语料的正常样本被去掉了空格、骚扰样本的数字/姓名被脱敏成 `x` 串，保留它们模型会学到"有空格=骚扰 / 有数字=正常 / 有 xxx=骚扰"的假特征。）
  - 特征：字符 n-gram，n ∈ {1,2,3}，在归一化后的字符串（按 UTF-16 code unit）上滑窗。
  - 哈希：FNV-1a 32 位，逐 UTF-16 code unit（低字节、高字节顺序）喂入，结果 `& (buckets-1)`，buckets = 2^18。
  - 特征值：每个桶计数，最后做 L2 归一化（与 sklearn `HashingVectorizer(norm='l2', alternate_sign=False)` 语义一致，但哈希函数自实现）。
- `ml/train.py`：
  - 下载中文垃圾短信数据集（约 80 万条，`label\tcontent`）和英文 SMS Spam Collection（约 5.5k 条，补充拉丁字母覆盖）到 `ml/data/`（已存在则跳过）。
  - 8:2 切分，`LogisticRegression`（liblinear，C=1），打印多个阈值下的 precision / recall / F1 / 混淆矩阵。
  - 导出 `app/src/main/resources/model/spam_v1.bin` 与 `app/src/test/resources/model/parity.json`。
- 模型文件格式（大端）：
  - magic `NSPM`（4 bytes）、version int32 = 1、buckets int32、ngramMin int32、ngramMax int32、bias float32、scale float32、随后 `buckets` 个 int8 权重（`w ≈ q * scale`）。
- `parity.json`：`[{"text": ..., "score": ...}, ...]`，含中英混合、纯数字、空串、短文本样例。

### 2. App 端推理

- `domain/SpamModel.kt`（纯 Kotlin）：
  - `SpamModel.load(input: InputStream): SpamModel`，`score(text: String): Float`（sigmoid 概率）。
  - `SpamFeatures`：归一化、n-gram、FNV-1a 哈希，与 Python 一致。
  - `SpamModel.bundled()`：懒加载 `resources/model/spam_v1.bin`，失败返回 null 并记录一次。
- `FilterConfig` 增加 `spamEnabled: Boolean = false`、`spamThreshold: Float = 0.9f`；`FilterConfigCodec` 编解码，旧 JSON 读取时使用默认值。
- `KeywordFilter.shouldBlock`：
  - 规则命中优先；未命中且 `config.enabled && config.spamEnabled` 时，对 `extracted.combined` 打分。
  - 归一化后长度 < 4 直接跳过。
  - 含验证码类字样（验证码 / 校验码 / 动态码 / verification code / OTP 等）的通知硬性放行，不论分数（语料中没有真实验证码短信，模型会误判）。
  - `score >= spamThreshold` 视为命中，`hit = BlockRule(id = "spam_model", name = "智能识别骚扰")`，走现有日志链路。
  - judge 日志附加 `spam=0.93`。
- 模型加载失败或打分抛异常时不拦截。

### 3. 设置页

- `SettingsScreen`：新增「智能识别骚扰」开关；开启时显示阈值 Slider（0.50–0.99，步进 0.01，显示当前值）。复用 `SettingsItems` 里现有组件风格。
- `NoticeViewModel`：`setSpamEnabled`、`setSpamThreshold`，持久化方式与 `logEnabled` 相同。
- `strings.xml` 新增对应文案。

### 4. 测试

- 引入 `testImplementation("junit:junit:4.13.2")`。
- `SpamFeaturesTest`：归一化和哈希的固定向量。
- `SpamModelTest`：加载 bundled 模型，对 `parity.json` 每条样例断言 `|score - expected| < 1e-3`。
- `FilterConfigCodecTest`：新字段 round-trip；旧 JSON 缺字段取默认。
- 验证命令：`./gradlew :app:testDebugUnitTest :app:assembleDebug`。
