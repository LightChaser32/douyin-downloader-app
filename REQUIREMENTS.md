# 需求文档（REQUIREMENTS）

> 本文件记录 DouyinDownloaderApp（抖音资源下载器）在"通用化"改造中的全部改动要求。
> 待测试成功后，再执行 GitHub 上传相关操作（.gitignore / README / 推送）。

---

## 目标

让 App 能在任意 Android 手机（minSdk 24 即 Android 7.0+）上通用安装使用，且任何电脑都能一键搭建环境构建。

---

## 需求一：UA 分层处理

**背景：**
当前存在多处 UA，其中 `Downloader.kt` 硬编码了开发者的手机机型（V2408A），属于隐私泄露且不通用。

**改动：**

### 1.1 下载请求 UA 动态化
- 文件：`app/src/main/java/com/douyin/crawler/Downloader.kt`（约第 46-50 行）
- 现状：硬编码 `"Mozilla/5.0 (Linux; Android 13; V2408A Build/TP1A.220624.014) ..."`
- 要求：改为运行时用 `android.os.Build.MODEL` + `android.os.Build.VERSION.RELEASE` 动态构造真实机型 UA，形如：
  ```
  "Mozilla/5.0 (Linux; Android $release; $model) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
  ```
  每台手机自动携带自己的真实机型，无需 bat 配置、无需重新构建。

### 1.2 detail 接口 UA 保持固定（不改）
- 文件：`app/src/main/java/com/douyin/crawler/ApiClient.kt`（约第 27-28 行）
- 现状：桌面 UA `"Mozilla/5.0 (Windows NT 10.0; Win64; x64) ... Chrome/139.0.0.0"`
- 原因：**不能改动**。a_bogus 签名（`buildSignJs` 中 `generateABogus(query, UA)`）与桌面 UA 绑定，且请求参数全为桌面标识（`os_name=Windows`、`browser_name=Chrome`、`pc_client_type=1`），改 UA 会导致签名不匹配、接口拒绝。
- 这是接口兼容性要求（参考项目 jiji262 同样如此），不属于隐私问题。

### 1.3 其他 UA（不改）
- `MainActivity.kt`（约第 470 行）封面加载 UA：已是通用 Android UA，无需改。
- `WebViewLoader.kt`（约第 40 行）桌面 UA：WebView 内部页面加载用，无需改。

---

## 需求二：分享→本App 自动解析（通用体验核心）

**背景：**
当前没有"分享→本App"入口，用户只能在抖音复制链接后手动打开 App 粘贴。通用化后应支持抖音分享面板直接选择本 App 并自动解析。

**改动：**

### 2.1 AndroidManifest.xml
- 文件：`app/src/main/AndroidManifest.xml`
- 要求：给 `MainActivity` 增加 `ACTION_SEND` + `text/plain` 的 intent-filter（接收抖音分享文本）。

### 2.2 MainActivity.kt
- 文件：`app/src/main/java/com/douyin/crawler/MainActivity.kt`
- 要求：
  - `onCreate` 中处理 `intent.getStringExtra(Intent.EXTRA_TEXT)`，复用现有 `extractUrl()` 逻辑从分享文本中提取抖音链接，自动填入输入框并触发解析。
  - 重写 `onNewIntent()` 处理 App 已运行时的二次分享。
  - 无有效链接时提示"未在分享内容中找到抖音链接"。

**效果：** 抖音任意作品 → 点"分享" → 面板选择本 App → 自动打开并解析下载。

---

## 需求三：构建环境通用化

**背景：**
`gradle.properties` 硬编码了本机 JDK 绝对路径，他人克隆后无法构建。

**改动：**

### 3.1 gradle.properties 清理
- 文件：`gradle.properties`
- 要求：删除 `org.gradle.java.home=D:\...prebuilt\jdk\...` 一行（本机路径，不应随仓库分发）。保留 `android.useAndroidX`、`org.gradle.jvmargs`、`org.gradle.daemon`。

### 3.2 新增 setup.bat（一键搭建环境）
- 位置：项目根目录 `setup.bat`
- 功能：
  1. 检测/下载 JDK 17（Windows x64），解压到 `prebuilt/jdk/`
  2. 下载 Android SDK 组件：cmdline-tools、platforms;android-34、build-tools;34.0.0、platform-tools，解压到 `prebuilt/android-sdk/`
  3. 生成 `local.properties`（写入 `sdk.dir` 指向本地 SDK）
  4. 引导填写 jks 密码，生成 `.gitignore` 排除的 `keystore.properties`（含 storeFile/storePassword/keyAlias/keyPassword）

### 3.3 app/build.gradle release 签名
- 文件：`app/build.gradle`
- 要求：读取 `keystore.properties`（若存在）配置 `signingConfigs.release`，`buildTypes.release` 关联该签名。若 `keystore.properties` 不存在，则 release 走无签名逻辑，不影响 debug 构建。
- 注意：`douyin_release.jks` 未复制到本目录（隐私），需用户自行放置并填密码。

---

## 需求四：隐私清理

- `app/src/main/res/values/strings.xml`：删除已废弃的 `recent_title`（"最近下载"）字符串。
- `Downloader.kt` 机型硬编码由需求一 1.1 解决。
- 其余源码已确认无硬编码 Cookie/token/密钥/本机路径。

---

## 需求五：GitHub 上传（测试成功后执行）

> ⚠️ 本项在 App 测试成功后再做。

1. 新建 `.gitignore`：
   ```
   .gradle/
   .gradle-home/
   prebuilt/
   app/build/
   local.properties
   *.jks
   keystore.properties
   .idea/
   *.iml
   ```
2. 新建 `README.md`：项目简介、功能、构建方法（`setup.bat` + `gradlew.bat assembleDebug`）、登录说明、分享使用说明。
3. `git init` → commit → `gh repo create douyin-downloader-app --public --push`（仓库名已定，公开）。

---

## 测试清单（改造完成后真机验证）

- [ ] 手机下载视频：抖音分享 → 选本 App → 自动解析 → 下载 → 相册可见
- [ ] 手机下载图文：分享 → 自动解析 → 缩略图带复选框 → 勾选下载 → 相册可见
- [ ] 分享短链 `v.douyin.com/xxxxx` 自动解析
- [ ] 完整链接、note 图文链接解析正常
- [ ] 设置 → 打开登录页 → 登录成功提示
- [ ] 新手机安装（其他安卓设备）运行正常
