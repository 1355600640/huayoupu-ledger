# 花有谱（Huayoupu Ledger）Android APK 打包指南

本文档详细说明如何构建、打包与签名花有谱记账 App 的 Android APK 安装包，适用于日常开发测试（Debug）与正式发布（Release）。

---

## 1. 项目概况与架构

- **应用包名 (applicationId)**：`net.ankio.ledger`
- **应用名称**：花有谱（Huayoupu Ledger）
- **开发语言 & 框架**：Android 原生容器 (Kotlin) + 离线轻量 WebView (HTML/CSS/JS) + PaddleOCR 本地推理 SDK
- **Android SDK 版本**：
  - `minSdk`：30 (Android 11+)
  - `targetSdk`：36
  - `compileSdk`：36
- **支持架构 (ABI)**：
  - `arm64-v8a`（主流 Android 真机，默认推荐）
  - `x86_64`（桌面 Android 模拟器）

---

## 2. 环境依赖与准备

进行打包前，请确保宿主机已安装并配置以下工具链：

### 2.1 JDK 21（必须）

项目使用 Gradle 8.11.1、Android Gradle 插件 (AGP) 8.8.2 和 Kotlin 2.1.20，编译目标为 JVM 21。
推荐直接使用 Android Studio 内置的 JBR (JetBrains Runtime)：

- **macOS**：
  ```bash
  export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
  ```
- **Windows**：
  ```cmd
  set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
  ```
- **Linux**：
  ```bash
  export JAVA_HOME=/opt/android-studio/jbr
  ```

验证命令：

```bash
"$JAVA_HOME/bin/java" -version
```

### 2.2 Android SDK 与 NDK

- 需安装 Android SDK Platform 36、Build-Tools 36.x。
- 确保环境变量 `ANDROID_HOME`（或在项目根目录创建 `local.properties` 指定 `sdk.dir`）。

### 2.3 Python 3

用于运行资产打包处理脚本 `scripts/prepare-ledger-v6-assets.py` 及安全源检查 `scripts/check-public-source.py`：

```bash
python3 --version
```

### 2.4 Node.js & npm

用于在 Release 构建时执行前端静态资源的混淆与保护工具（`tools/web-protection`）：

```bash
node --version
npm --version
```

---

## 3. 打包命令与流程

### 3.1 Debug APK 构建（推荐日常测试使用）

Debug 包会自动使用 Android 官方调试密钥（`~/.android/debug.keystore`）完成签名，构建完成后可**直接在手机或模拟器上安装运行**。

#### macOS / Linux 打包命令：

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
./gradlew -PledgerAbi=arm64-v8a -PledgerPython=python3 :app:assembleDebug
```

#### Windows 打包命令：

```cmd
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
gradlew.bat -PledgerAbi=arm64-v8a -PledgerPython=python3 :app:assembleDebug
```

#### 产物位置：

- 构建输出路径：`app/build/outputs/apk/debug/app-debug.apk`
- 建议将构建产物复制到集中输出目录（例如 `output/`）：
  ```bash
  mkdir -p output
  cp app/build/outputs/apk/debug/app-debug.apk output/Huayoupu-Ledger-debug-arm64-v8a.apk
  ```

#### 安装方式：

通过 USB 连接手机并开启开发者模式（USB 调试）：

```bash
adb install -r output/Huayoupu-Ledger-debug-arm64-v8a.apk
```

或直接通过微信/文件传输发送到手机点击安装。

---

### 3.2 Release APK 构建（正式发布）

Release 构建会激活：

1. **R8 代码混淆与优化**（开启 `isMinifyEnabled = true`）；
2. **资源缩减**（`isShrinkResources = true`，自动剔除无用资源）；
3. **前端 Web 代码安全保护**（通过 `tools/web-protection` 对 JavaScript 进行混淆）。

#### 打包命令：

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
./gradlew -PledgerAbi=arm64-v8a -PledgerPython=python3 :app:assembleRelease
```

#### 产物位置：

- `app/build/outputs/apk/release/app-release-unsigned.apk`

---

## 4. Release APK 对齐与签名

Release 构建默认输出为未签名的 APK。正式安装或上架前，必须完成 `zipalign` 4 字节对齐与 `apksigner` 签名。

### 4.1 生成正式签名密钥库（若已有可跳过）

```bash
keytool -genkeypair -v -keystore my-release-key.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias my-alias
```

### 4.2 对齐 APK (zipalign)

找到 Android SDK build-tools 目录下的 `zipalign`：

```bash
$ANDROID_HOME/build-tools/36.0.0/zipalign -p -f -v 4 \
  app/build/outputs/apk/release/app-release-unsigned.apk \
  output/Huayoupu-Ledger-release-aligned.apk
```

### 4.3 签名 APK (apksigner)

```bash
$ANDROID_HOME/build-tools/36.0.0/apksigner sign \
  --ks my-release-key.jks \
  --ks-key-alias my-alias \
  --out output/Huayoupu-Ledger-release.apk \
  output/Huayoupu-Ledger-release-aligned.apk
```

### 4.4 校验签名

```bash
$ANDROID_HOME/build-tools/36.0.0/apksigner verify -v output/Huayoupu-Ledger-release.apk
```

若输出包含 `Verifies: true`，说明 APK 签名有效，可安全分发与安装。

---

## 5. 核心构建参数说明

| 参数名                 | 默认值                                      | 可选值                    | 说明                                                                 |
| ---------------------- | ------------------------------------------- | ------------------------- | -------------------------------------------------------------------- |
| `-PledgerAbi`        | `arm64-v8a`                               | `arm64-v8a`, `x86_64` | 目标 CPU 架构过滤。真机通常为 `arm64-v8a`，模拟器通常为 `x86_64` |
| `-PledgerPython`     | `python`                                  | `python3`, `python`   | 指定执行脚本时所调用的 Python 解释器命令                             |
| `-PledgerTestRunner` | `androidx.test.runner.AndroidJUnitRunner` | 自定义 Runner             | 单元/仪表盘测试 Runner                                               |

---

## 6. 验证与质量检查指令

在提交代码或打包发布前，建议运行以下完整的验证流水线：

```bash
# 1. 运行公开源码与授权检查
python3 scripts/check-public-source.py

# 2. 运行核心模块单元测试
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
./gradlew -PledgerAbi=arm64-v8a -PledgerPython=python3 :core:test

# 3. 运行 App 模块单元测试
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
./gradlew -PledgerAbi=arm64-v8a -PledgerPython=python3 :app:testDebugUnitTest

# 4. 执行 Debug APK 构建
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
./gradlew -PledgerAbi=arm64-v8a -PledgerPython=python3 :app:assembleDebug
```

---

## 7. 常见问题排查 (Troubleshooting)

### Q1: 提示 `Unsupported class file major version` 或 Java 编译错误

- **原因**：当前 Shell 环境中激活的 JDK 版本过低或过高（项目要求 JDK 21）。
- **解决**：显式设置 `JAVA_HOME` 指向 Android Studio 自带的 JBR 路径（见 2.1 节）。

### Q2: 提示 `Cannot run program "python": error=2, No such file or directory`

- **原因**：macOS 默认未设置 `python` 软链接，仅包含 `python3` 命令。
- **解决**：在 Gradle 命令中追加 `-PledgerPython=python3` 参数。

### Q3: 提示 `Unsupported ledger ABI`

- **原因**：传入了未经配置的 ABI 架构（如 `armeabi-v7a`）。
- **解决**：传入 `-PledgerAbi=arm64-v8a` 或 `-PledgerAbi=x86_64`。

### Q4: 提示 `npm ci` 失败或连接超时

- **原因**：Release 构建依赖 `tools/web-protection` 下的 Node.js 混淆工具，初次构建会执行 `npm ci`。
- **解决**：进入 `tools/web-protection` 目录，手动配置镜像或执行 `npm install`。
