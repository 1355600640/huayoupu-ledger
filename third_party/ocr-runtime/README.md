# 固定 OCR 原生运行库

这些 AAR 是第三方构建产物，不适用根目录的项目 GPL 授权替代声明，也不是慧铭自研引擎。保留 Java 绑定及原始通知，原生部分针对 V6 用途精简。常规应用构建直接校验、使用它们，无需本机重新编译 C++。

| 组件 | 固定版本 / 源码提交 | 用途与许可 |
| --- | --- | --- |
| ONNX Runtime | 1.21.1 / `8f7cce3a49fdbdac96e0868b75b7d0159db7ac7f` | CPU 精简算子；MIT 及 ThirdPartyNotices |
| OpenCV | 4.12.0 / `49486f61fb25722cbcf586b7f4320921d46fb38e` | core / imgproc / imgcodecs / java；Apache-2.0 |
| Eigen | `1d8b82b0740839c0de7f1242a3585e3390ff5f33` | MPL-2.0 及文件级许可；未修改原始源码归档位于 `sources/eigen-1d8b82b.tar.gz` |
| Android NDK libc++ | NDK 28.2.13676358 | Apache-2.0 with LLVM Exceptions 及完整 NDK 通知 |
| OpenCV 内部图片与硬件库 | 同一固定 OpenCV 源码 | libpng、libjpeg-turbo / IJG、Android cpufeatures、carotene / tegra HAL，见 `licenses/` |

ARM64 构建记录显示链接 libpng、libjpeg-turbo、cpufeatures 和 tegra HAL；ZLib 为 Android 系统 `libz`，没有单独打包 zlib 共享库。不同 ABI 的优化模块可能不同，保留完整通知。

This software is based in part on the work of the Independent JPEG Group.

## 文件校验与对应源码

所有 AAR 大小、SHA-256、源码哈希、JNI 调整和 ABI 记录于 [运行库锁文件](config/ledger-v6-runtime-lock.json)。Paddle SDK / 模型见 [上游锁文件](config/upstream-lock.json)。额外通知来源与哈希见 [additional-notices.json](licenses/additional-notices.json)。

精确对应的原始源码归档：

- [ONNX Runtime 固定源码](https://codeload.github.com/microsoft/onnxruntime/tar.gz/8f7cce3a49fdbdac96e0868b75b7d0159db7ac7f)，SHA-256 `f867db83cb997f93b45a572ca4a4d409c7dad6f614179d59dd4b08eea5f9df02`。
- [OpenCV 固定源码](https://codeload.github.com/opencv/opencv/tar.gz/49486f61fb25722cbcf586b7f4320921d46fb38e)，SHA-256 `8f00b42869ab2836be36090f6631ae4c38ba59171e22a69a8e0f92f8ef1771d4`。
- [Eigen 固定源码](https://codeload.github.com/eigen-mirror/eigen/tar.gz/1d8b82b0740839c0de7f1242a3585e3390ff5f33)，同时随本仓库提供，SHA-256 `c8adb6532fbfacd2ec1418c66404159b00c34d855bdc19cefa1f0db0e5e0aa5f`。其中 `COPYING.README` 解释文件级许可，不能仅凭 MPL 总许可删除其他版权说明。
- [PaddleOCR 固定源码](https://github.com/PaddlePaddle/PaddleOCR/tree/dab3fe35379033fdcb2d0e9572fac0b36c9a9ebf)。构建准备脚本下载并验证 SDK 文件与模型，独立适配器位于 `config/paddle-sdk.gradle.kts`。

上游完整 ONNX 通知覆盖多个可选提供器；通知中出现的库不一定全部编入精简 AAR。原始通知保留，不能据此声明所有内容均为 MIT。

## 重建原生库

应用 CI 在 Linux 上消费已校验的 AAR。现有原生重建脚本使用 Windows NDK 工具名；以下是 Windows PowerShell 流程，不声称可在所有平台逐字节复现。路径需为 ASCII，设置实际 JDK 21 的 `JAVA_HOME` 与实际 SDK 的 `ANDROID_HOME`。

```powershell
sdkmanager 'ndk;28.2.13676358' 'cmake;3.22.1'
python -m pip install --target .tools/ocr-native/python-build cmake==3.31.6
python -m pip install onnx==1.19.0
python scripts/prepare-ledger-build.py
python scripts/prepare-ocr-ort-config.py
```

从 Maven Central 下载同版本原始绑定 AAR；脚本校验固定 SHA-256，不能用其他版本替换：

```powershell
New-Item -ItemType Directory -Force .tools/ocr-native/official | Out-Null
Invoke-WebRequest 'https://repo.maven.apache.org/maven2/com/microsoft/onnxruntime/onnxruntime-android/1.21.1/onnxruntime-android-1.21.1.aar' -OutFile .tools/ocr-native/official/onnxruntime.aar
Invoke-WebRequest 'https://repo.maven.apache.org/maven2/org/opencv/opencv/4.12.0/opencv-4.12.0.aar' -OutFile .tools/ocr-native/official/opencv.aar
python scripts/build-ocr-ort.py --abi arm64-v8a --sdk $env:ANDROID_HOME --base-aar .tools/ocr-native/official/onnxruntime.aar
python scripts/build-ocr-opencv.py --abi arm64-v8a --sdk $env:ANDROID_HOME --base-aar .tools/ocr-native/official/opencv.aar
```

将 ABI 改为 `x86_64` 可构建另一版本。算子配置由固定模型生成；不改变权重精度。输出在 `.tools/ocr-native/`，不会自动覆盖本目录已发布 AAR。更新运行库必须同时更新锁文件、通知及设备回归，不能为通过哈希校验而只改摘要。

## 分发

随 APK 提供对应版本的项目源码及构建说明，保留此目录、原文通知、固定来源和修改声明。Eigen 的 MPL 覆盖源码与通知保持可获得。字体、商标和其他素材的范围另见 [NOTICE](../../NOTICE.md)。
