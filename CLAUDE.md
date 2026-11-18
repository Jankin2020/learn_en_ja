# VoxCoach (learn_en_ja) – 接手说明

这是一个英语和日语口语陪练 Android App，参照 Voca AI 设计。仓库地址：github.com/Jankin2020/learn_en_ja。
用户用中文交流，手机是小米 K70（国行 ROM）。用户要求**不开代理**也能用；需要时可以开代理访问 Google。

## 当前进度（2026-11-18）
- 最新版本 **1.5**（`versionCode 7`），见 AndroidManifest.xml。
- 1.5 的改动：「语音环境」页（VoiceActivity）改成可折叠分组，顶部显示「当前生效」，选中项标 ✓；
  硅基流动的日语默认音色改为 claire。
- **待办**
  1. 确认 `VoiceActivity.java`、`Store.java`、`AndroidManifest.xml`、`VoxCoach.apk` 已经是 1.5 版本。
     上个会话写入时电脑连接断开，这几个文件由用户手动替换，所以要先检查。
  2. `git add -A && git commit`，然后用户运行 `push_to_github.ps1` 推送，打 tag `v1.5`，触发 Actions 发布 APK。
  3. 1.4 / 1.4.1 / 1.5 都还**没有真机测试**。有两处容易出问题，需要用户反馈：
     - 识别错误码 13 的自动切换。
     - 内置离线音的播放和变速（PlaybackParams）。

## 构建（不用 Gradle）
- 运行 `bash build.sh`，依次执行 aapt → javac → dx → zipalign → apksigner，产物是 `VoxCoach.apk`。
- 依赖：`apt install android-sdk-platform-23 aapt dalvik-exchange zipalign apksigner` 和 JDK。
  Windows 下用 WSL，或者推送后用 GitHub Actions 构建（配置在 `ci/build.yml`）。
  `push_to_github.ps1` 会把 `ci/build.yml` 复制到 `.github/workflows/`。
- 签名密钥是 `voxcoach.keystore`，密码 `voxcoach`。密钥只在本地，已被 .gitignore 排除。
  CI 里用 secret `KEYSTORE_BASE64` 签名，**丢了这个密钥就不能覆盖安装**。
- 每次发版：把 Manifest 的 `versionCode` 加 1，并修改 `versionName`。

## 代码约定（重要）
- 编译时用的是 android-23 的 android.jar，所以：
  - **不能用 API 23 以上的接口**，确实要用就走反射，参考 `Rec.tryModelDownload`。
  - **不能用 lambda 和方法引用**，因为 dx 不支持。
  - 不用 AndroidX、不用 Kotlin，界面全部用 Java 代码写（工具类是 `UI.java`）。
- minSdk 23，targetSdk 34。
- 源码在 `src/com/xyb/voxcoach/`：

| 文件 | 作用 |
|---|---|
| `Scorer` | 发音评分：语音识别结果与原句逐词或逐假名对齐。纯 Java，可以在 JVM 上单测 |
| `Content` | 内置题库、对立词、场景。日语注音格式为 `{漢字\|かな}` |
| `Store` | SharedPreferences 存储，以及自适应逻辑 |
| `Speaker` | 播放示范音。来源有四种：bundled（内置离线录音，默认）、system、cloud、azure；没有内置录音时用 `ttsFallback` 兜底 |
| `AudioBank` | 内置录音，key 为 `md5(lang\|text)` |
| `Rec` | 语音识别。系统识别会自动选服务；遇到错误码 12、13 或网络错误会换服务，都失败再转在线识别 |
| `CloudAudio` | OpenAI 兼容的 /audio 接口，以及 Azure TTS |
| `AiClient` | 调用 OpenAI 兼容的对话接口 |
| `PackSync`、`PacksActivity` | 内容库：订阅 `content_packs/index.json`，默认地址是 jsDelivr 镜像 |
| 各个 `*Activity` | 界面：主页、跟读、听写、听辨、对话、五十音、复习、设置、语音环境、内容库 |

## 内容与语音资源
- `content_packs/` 下有 6 个场景包和 `index.json`。
  修改某个包时，要把包文件和 index 里的 `version` 都加 1，App 才会检测到更新。
  推送后可以通过 purge.jsdelivr.net 刷新 CDN 缓存。
- `assets/voice/` 下有 903 条 mp3，用 Kokoro-82M 生成（英语音色 af_heart，日语音色 jf_alpha）。
  新增内容时：先用 `tools/voicegen/manifest.py` 生成清单，再用 `gen_voice.py` 合成，脚本支持断点续跑。
  新场景包可以加上 `"audio": "audio/"` 字段，再把录音放到 `content_packs/audio/<key>.mp3`，App 安装场景包时会自动下载。
- 新增日语句子时，要用 Scorer 自测：注音里不能残留汉字，自己读自己的评分应该 ≥95。
