# VoxCoach：英语 / 日语 AI 口语陪练（Android）

直接安装 `VoxCoach.apk` 即可使用（Android 6.0 及以上）。当前版本：1.3。

## 功能

| 模块 | 做什么 | 怎么根据你的发音调整 |
|---|---|---|
| 🎯 发音跟读 | 标准示范 → 跟读 → 逐词/逐假名着色打分（准确度 / 完整度 / 流利度） | 连续两次低于 70 分会自动切换到慢速示范，并逐个重放读错的部分；最近 5 句平均分 ≥86 自动升级，<55 自动降级；读错的词进入弱项本 |
| 🎧 听写训练 | 听句子并写下来，比对后用颜色标出差异 | 播放语速随正确率变化（0.6x–1.3x）：全对就加速，错得多就放慢 |
| 👂 听辨训练 | 最小对立词（light/right、ship/sheep、おばさん/おばあさん、きて/きって…），先听辨再开口 | 错误率越高、跟读中越常出错的音类，出现得越频繁 |
| 💬 AI 情景对话 | 12 个场景角色扮演（咖啡店、面试、便利店、职场敬语…），可以语音或打字 | AI 按你的等级回复，每句给出评分、纠错和更自然的说法；可关字幕练听力；结束点评里的推荐表达可一键加入跟读题库 |
| あ 五十音（日语） | 平/片假名表（清音 · 浊音 · 拗音），以及认读测验、听音选字、例词跟读 | 错得多的假名出现得更频繁；认读会用形近字（ぬ/め、シ/ツ、ソ/ン）当干扰项，听音会用音近字（か/が、き/きゃ）当干扰项；表格按掌握度着色 |
| 📚 弱项复习 | 待复习句子、易错单词、发音问题统计（L/R、TH、长音、促音、清浊音…） | 点单词进入单词纠音；AI 出题会针对这些弱项生成新句子 |

内置题库：英语 A1–B2、日语 N5–N2 各 48 句（日语带注音），另有 45 组最小对立词。

## AI 接口（设置页）

可以填任意 OpenAI 兼容接口，设置页有预设：

- DeepSeek `https://api.deepseek.com/v1` · `deepseek-chat`
- 通义千问 `https://dashscope.aliyuncs.com/compatible-mode/v1` · `qwen-plus`
- Kimi `https://api.moonshot.cn/v1` · `moonshot-v1-8k`
- 智谱 `https://open.bigmodel.cn/api/paas/v4` · `glm-4-flash`
- OpenAI `https://api.openai.com/v1` · `gpt-4o-mini`

API Key 只保存在手机本地。不配置 AI 时，跟读、听写和听辨照常可用。

## 手机要求与语音环境（国行手机必看）

App 内入口：首页「语音环境」或 设置 → 语音环境。

- **方案 A：Google 离线语音（免费）**
  - 开代理，在 Google Play 安装「Google 语音服务（Speech Services by Google）」和「Google」App。
  - 回到语音环境页，点「下载 Google 日语语音包」，选 日本語 下载。
  - 引擎保持「自动」即可，会优先用 Google；页面会检测每个引擎是否支持英语和日语。
- **方案 B：在线语音（不需要代理）**
  - 在「在线语音」里选硅基流动或 OpenAI 预设，填 Key。
  - 把合成或识别切到「在线」。
  - 合成结果缓存在本地，同一句不会重复计费。
- 两种方案可以混用。比如合成走 Google 离线、识别走在线，或者反过来。

## 让示范发音更像真人

在「语音环境」页可以选三种合成方式，自然度从低到高：

1. **系统引擎（Google）**：点「🎚 选日语音色 / 选英语音色」挑一个音色，会逐个试听。标着「高音质 · 联网」的通常更自然，需要能访问 Google。
2. **在线（兼容）**：OpenAI 兼容接口。OpenAI 的 `gpt-4o-mini-tts` 很自然，但需要代理；硅基流动 CosyVoice2 国内直连，但内置音色是中文母语者，说日语略带口音。
3. **微软 Azure 神经语音（推荐）**：日语有七海 Nanami、圭太 Keita 等，英语有 Ava、Andrew 等，接近真人。免费档（F0）每月有 50 万字符额度，示范音频会缓存，日常练习基本用不完。
   - 开通：在 portal.azure.com 创建「语音服务（Speech）」资源，定价层选 F0，区域建议 Japan East。
   - 复制「密钥 1」和区域（如 `japaneast`），填进 App。
   - 把「语音合成」切到「微软 Azure」，点任意音色即可试听。

## 内容库（场景包）

场景包放在仓库的 `content_packs/` 目录里，`index.json` 是目录：

| 文件 | 内容 |
|---|---|
| `ja_daily.json` | 日语 · 日常办事：看病、租房、区役所、美容院、邮局、办手机卡 |
| `ja_business.json` | 日语 · 职场商务：早会汇报、客户来电、向上司报告、会议发言、拜访客户、聚餐 |
| `ja_travel.json` | 日语 · 旅行：新干线、温泉旅馆、居酒屋、游客咨询、打车、交番报失 |
| `en_daily.json` | 英语 · 日常：看医生、银行、租房、药店、退换货、健身房 |
| `en_business.json` | 英语 · 职场：站会、客户电话、汇报数据、谈截止日期、一对一、社交 |
| `en_travel.json` | 英语 · 旅行：入境、租车、餐厅投诉、行李丢失、问路、酒店报修 |

每个包有 6 个对话场景和 12～14 句跟读句。

**在 App 里安装和更新**

入口：对话页的「📦 场景库」，或 设置 → 内容库。

- 打开后会自动读取目录，每个包可以单独「安装 / 更新」，也可以点「全部安装/更新」。
- 默认开启「每天自动更新」：每天第一次打开 App 时，已安装的包如果有新版本，会自动升级。
- 默认地址是国内镜像：`https://cdn.jsdelivr.net/gh/Jankin2020/learn_en_ja@main/content_packs/index.json`

**发布新内容或修改内容**

1. 新增包：在 `content_packs/` 放一个新的 `xxx.json`，再在 `index.json` 的 `packs` 里加一条：`{"id","name","lang","version","desc","url":"xxx.json"}`。
2. 修改已有的包：改完内容后，把**包文件和 `index.json` 里的 `version` 都加 1**。App 是靠这个数字判断有没有更新的。
3. 运行 `push_to_github.ps1` 推送到 GitHub。
4. jsDelivr 镜像最多缓存 12 小时。想马上生效，就在浏览器打开下面的地址刷新缓存：
   `https://purge.jsdelivr.net/gh/Jankin2020/learn_en_ja@main/content_packs/index.json`
   改过哪个包，就把地址最后的文件名换成那个包再刷一次。

包文件格式（日语句子里含汉字的词写成 `{漢字|かな}`，评分才能精确到每个假名）：

```json
{
  "id": "ja_daily", "name": "日语 · 日常办事", "lang": "ja", "version": 2, "desc": "简介",
  "scenarios": [{"lang": "ja", "icon": "🏥", "title": "中文标题", "sub": "副标题", "role": "给 AI 的角色说明（英文或中文）", "opener": "AI 的第一句"}],
  "sentences": [{"lang": "ja", "level": 2, "text": "{熱|ねつ}があります。", "zh": "我发烧了。"}],
  "pairs":     [{"lang": "ja", "cat": "长音", "a": "主人 しゅじん", "a_say": "しゅじん", "a_zh": "丈夫", "b": "囚人 しゅうじん", "b_say": "しゅうじん", "b_zh": "囚犯"}]
}
```

想要新的场景包，直接告诉 Claude 主题即可，比如「日语 · 电商客服」，生成后放进这个目录再推送。

## 评分原理与局限

评分是把语音识别的 N-best 结果和目标句逐词（日语逐假名）对齐后计算的，日语会借助注音把汉字词换成假名再比对，因此能定位到漏掉的促音、长音等。它衡量的是「别人能不能听懂、准不准」，不是声学层面的音素打分：识别器有时会自动纠正轻微的口音。AI 诊断会根据识别出的差异推测具体是哪个音的问题。

## 从源码构建

纯 Java + Android 原生 API，不需要 Gradle：

```bash
sudo apt install openjdk-17-jdk android-sdk-platform-23 aapt dalvik-exchange zipalign apksigner
./build.sh        # 生成 VoxCoach.apk
```

`voxcoach.keystore` 是签名密钥，只放在你的本地文件夹里，已被 `.gitignore` 排除，不会上传到 GitHub。请备份它：只有用同一个密钥签名的新版本，才能覆盖安装并保留学习记录。

## 上传到 GitHub（Jankin2020）

1. 安装 Git for Windows：https://git-scm.com/download/win
2. 打开 https://github.com/new 创建一个空仓库，名称填 `learn_en_ja`，不要勾选 README。公开或私有都可以。
3. 在本文件夹打开 PowerShell，运行：
   `powershell -ExecutionPolicy Bypass -File .\push_to_github.ps1`
   第一次运行会弹出浏览器，登录 GitHub 授权即可。
4. （推荐）让 GitHub 自动构建的 APK 能覆盖安装手机上的版本：
   - 在 PowerShell 运行下面这行，把签名密钥转成文本并复制到剪贴板：
     `[Convert]::ToBase64String([IO.File]::ReadAllBytes("$PWD\voxcoach.keystore")) | Set-Clipboard`
   - 到仓库的 Settings → Secrets and variables → Actions → New repository secret，名字填 `KEYSTORE_BASE64`，内容粘贴。
5. 以后每次推送，Actions 都会自动构建 APK。推送 `v1.3` 这样的标签，会自动发布到 Releases 页，手机上直接下载安装。脚本最后一步可以帮你打标签。



源码结构（`src/com/xyb/voxcoach/`）：

- `Scorer` 对齐与评分
- `Content` 题库
- `Store` 进度与自适应逻辑
- `Speaker` TTS
- `Rec` 语音识别（系统 / 在线）
- `CloudAudio` 在线 TTS 与识别
- `Kana` 五十音数据
- `AiClient` 大模型接口
- 各 `*Activity` 界面
