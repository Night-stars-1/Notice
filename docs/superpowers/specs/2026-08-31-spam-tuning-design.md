# 骚扰识别：端侧微调 + 排除应用 设计

日期：2026-08-31。基于 `2026-08-31-spam-classifier-design.md`。

## 目标

1. 用户在记录页把通知标为「骚扰 / 正常」，模型在手机上据此学习，无需重新训练内置模型。
2. 「智能识别骚扰」可以排除指定应用：这些应用的通知不打分、不被模型拦截。

## 非目标

- 不做云端同步、不做跨设备。
- 不调整内置模型文件；微调只产生一个叠加在内置权重上的稀疏修正量。

## ① 微调

### 标注存储 `data/SpamLabelRepository.kt`
- 文件 `filesDir/spam_labels.json`：`[{recordId, packageName, text, spam, timestamp}]`。
- `text = title + "\n" + body`（与 system_server 打分用的合并文本足够接近）。
- API：`labels: StateFlow<Map<String, SpamLabel>>`（key = recordId）、`set(record, spam)`、`remove(recordId)`、`clear()`。

### 训练 `domain/SpamTuner.kt`（纯 Kotlin）
- 输入：内置 `SpamModel`、样本 `List<Sample(text, spam)>`。
- 输出：`SpamDelta(indices: IntArray, values: FloatArray)`——稀疏 Δw，不动 bias。
- 目标：Σ logloss(σ(z_base + Δw·x)) + λ/2‖Δw‖²，从 Δw = 0 起 SGD（固定顺序、固定轮数，结果确定）。默认 `epochs = 60, lr = 1.0, l2 = 0.005`（l2 决定单条样本最多能把自己推到 ≈0.95；再大就推不过阈值）。
- 归一化后长度 < `SpamJudge.MIN_LENGTH` 的样本忽略（system_server 也不会判定它们）。

### 修正量文件 `domain/SpamDelta.kt`
- 大端：`NSPD`、int32 version=1、int32 buckets、int32 count、count × (int32 index, float32 value)。
- `SpamModel.withDelta(delta)` 返回叠加后的新模型；buckets 不匹配则忽略 delta。

### 下发
- App 侧：`ModuleStatus.openRemoteFile("spam_delta.bin")`（libxposed `XposedService.openRemoteFile`）写入，截断后重写；然后 `FilterConfig.spamDeltaVersion = System.currentTimeMillis()` 写入 remote prefs。
- 模块侧：`KeywordFilter` 收到 prefs 变化时比较 `spamDeltaVersion`，不同则通过 `XposedInterface.openRemoteFile` 重新读取 delta 并重建模型；读取失败退回内置模型。version = 0 表示无 delta。
- 触发：`NoticeViewModel` 监听标注变化（跳过首次），在 `Dispatchers.Default` 上训练并下发；标注为空时写空 delta（count = 0）。

### UI
- 记录详情页顶部一行两个 `FilterChip`：「骚扰」「正常」；再次点击取消标注。
- 设置页「智能识别骚扰」打开时追加「微调样本 N 条」一行，右侧「清除」（确认对话框）。

## ② 排除应用

- `FilterConfig.spamExcludedPackages: List<String>`，JSON 键 `spamExcludedPackages`。
- 模块侧：`resolved in spamExcludedPackages` 时不打分（记录里也没有分数）。
- 设置页「智能识别骚扰」打开时追加「排除应用」一行（supporting 显示数量），点击进入 `AppPickerScreen`（`showModeSelector = false`，语义固定为排除）。

## 测试
- `SpamDeltaTest`：编解码 round-trip；`withDelta` 叠加正确；buckets 不匹配忽略。
- `SpamTunerTest`：标为骚扰的低分样本微调后 ≥ 0.9，标为正常的高分样本微调后 ≤ 0.1，无关文本变化 < 0.05；空样本得到空 delta。
- `FilterConfigCodecTest`：`spamExcludedPackages`、`spamDeltaVersion` round-trip 与旧 JSON 默认值。
- `./gradlew :app:testDebugUnitTest :app:assembleDebug`，安装到手机。
