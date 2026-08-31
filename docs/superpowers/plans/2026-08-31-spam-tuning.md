# 骚扰识别微调 + 排除应用 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users label logged notifications as spam/ham, fit a sparse weight delta on-device, deliver it to `system_server`, and let users exclude apps from the AI judge.

**Architecture:** Pure-Kotlin `SpamDelta` + `SpamTuner` in `domain`; `SpamLabelRepository` (JSON file) in `data`; the ViewModel retrains on label changes and ships the delta via libxposed remote file + a version stamp in `FilterConfig`; `KeywordFilter` reloads the delta on prefs change and skips excluded packages.

**Tech Stack:** Kotlin, Compose M3, libxposed 102 (`openRemoteFile` on both sides), JUnit 4.

**Spec:** `docs/superpowers/specs/2026-08-31-spam-tuning-design.md`

## Global Constraints

- Build env: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"; export ANDROID_HOME="$HOME/Library/Android/sdk"`; `sh ./gradlew …`.
- No new runtime dependencies.
- UI changes limited to: log detail sheet (label chips), Settings 通用 section (two rows under the AI switch), AppPicker gains a `showModeSelector` flag.
- Do not commit unless asked; `git add` is fine.

---

### Task 1: `SpamDelta` (format + overlay) — TDD
- Create `domain/SpamDelta.kt`: `class SpamDelta(val buckets: Int, val indices: IntArray, val values: FloatArray)`, `encode(): ByteArray`, `companion fun decode(InputStream): SpamDelta`, `val isEmpty`.
- Add `SpamModel.withDelta(delta: SpamDelta): SpamModel` (copy weights, add values; ignore when `delta.buckets != buckets`).
- Test `SpamDeltaTest`: round-trip, overlay changes only listed indices, mismatch ignored, bad magic throws.

### Task 2: `SpamTuner` — TDD
- Create `domain/SpamTuner.kt`: `data class Sample(text, spam)`, `fun fit(base: SpamModel, samples: List<Sample>, epochs = 40, lr = 1f, l2 = 0.02f): SpamDelta`.
- Test `SpamTunerTest` with a hand-built model (`buckets = 1 shl 12`, bias −4 → everything ham): label one text spam → tuned score ≥ 0.9; unrelated text stays < 0.1; bias +4 model + ham label → ≤ 0.1; empty samples → empty delta; short sample ignored.

### Task 3: Config fields — TDD
- `FilterConfig`: `spamExcludedPackages: List<String> = emptyList()`, `spamDeltaVersion: Long = 0`.
- Codec encode/decode; extend `FilterConfigCodecTest`.
- `RuleRepository.setSpamExcludedPackages(list)`, `setSpamDeltaVersion(v)`.

### Task 4: Module side
- `ModuleStatus.openRemoteFile(name): ParcelFileDescriptor?` (app side helper).
- `KeywordFilter`: keep `@Volatile model: SpamModel?` + `loadedDeltaVersion`; `refreshModel(api, cfg)` in `attach` and the prefs listener; skip scoring when `resolved in cfg.spamExcludedPackages`.
- Compile.

### Task 5: Labels + retraining pipeline
- `data/SpamLabelRepository.kt` (JSON file, StateFlow map).
- `data/SpamDeltaWriter.kt`: `write(delta)` → `ModuleStatus.openRemoteFile(SpamDelta.REMOTE_FILE)`, truncate, write bytes; returns Boolean.
- `NoticeViewModel`: `labels` flow, `setLabel(record, spam: Boolean?)`, `clearLabels()`, `setSpamExcludedPackages(list)`; `init` collects labels (drop first) → `SpamTuner.fit` on `Dispatchers.Default` → write delta → `rules.setSpamDeltaVersion(now)`.

### Task 6: UI
- Strings: `label_spam`, `label_ham`, `label_section`, `spam_excluded_apps`, `spam_excluded_apps_count`, `spam_tune_samples`, `spam_tune_samples_hint`, `spam_tune_clear`, `spam_tune_clear_confirm`.
- `LogsScreen(labels: Map<String, Boolean>, onLabel: (NotificationRecord, Boolean?) -> Unit)` → detail sheet chips.
- `AppPickerScreen(showModeSelector: Boolean = true)`: hide mode items and mode prefix in subtitle when false.
- `SettingsScreen`: `spamExcludedCount`, `tuneSampleCount`, `onPickSpamApps`, `onClearLabels`; rows under the threshold slider; `generalCount = if (spamEnabled) 6 else 3`.
- `NoticeApp`: `pickingSpamApps` state → `Screen.Apps` with `showModeSelector = false`; wire callbacks.

### Task 7: Verify
- `sh ./gradlew :app:testDebugUnitTest :app:assembleDebug`; install with `adb -s 7f56832 install -r`; `git add`.
