# 抖音资源下载器 (DouyinDownloader)

一键下载抖音视频、图文、合集的 Android 工具。支持从抖音 App 分享链接自动解析下载。

## 功能

- 下载抖音视频（无水印）
- 下载抖音图文/图集
- 抖音分享面板直接选择本 App 自动解析
- 支持短链 `v.douyin.com` 和完整链接
- 登录后可访问更多内容
- **合集批量下载**：自动识别抖音合集/短剧，一键解析全集并批量下载
- **选集分页浏览**：每页 20 集，支持上一页/下一页/指定页跳转
- **解析进度显示**：实时显示合集解析进度条和已获取集数
- **设置页面**：登录管理、合集解析开关

## 安装

1. 下载 [Release APK](https://github.com/LightChaser32/douyin-downloader-app/releases/latest)
2. 在 Android 手机上安装（需允许安装未知来源应用）
3. 首次使用需登录抖音账号

## 构建

### 一键搭建环境（Windows）

```bat
setup.bat
```

自动下载 JDK 17 和 Android SDK，生成构建所需配置文件。

### 手动构建

```bash
# 需要 JDK 17 和 Android SDK (platforms;android-34)
gradlew.bat assembleDebug      # Debug 版本
gradlew.bat assembleRelease    # Release 版本（需配置 keystore.properties）
```

## 使用说明

### 下载单个视频/图文

1. 打开抖音，选择要下载的视频/图文
2. 点击分享 → 选择"抖音资源下载器"
3. App 自动解析链接并显示结果
4. 点击下载按钮保存到相册

### 手动粘贴链接

1. 在抖音复制分享链接
2. 打开本 App，点击"粘贴链接"
3. 点击"解析"

### 合集批量下载

1. 粘贴或分享一个属于合集/短剧的视频链接
2. App 自动识别合集并显示合集信息卡片
3. 进度条实时显示解析进度
4. 展开选集列表，翻页浏览所有集数
5. 使用"全选/取消全选"或逐个勾选
6. 点击"下载选中"批量下载

> 点击页码可弹出跳转对话框，输入指定页码快速跳转。

## 登录

首次使用需要登录抖音账号：

1. 点击右上角设置图标
2. 选择"打开登录页"
3. 在 WebView 中完成登录
4. 登录成功后按返回键

## 工程目录索引

```
DouyinDownloaderApp/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml          # 应用清单（权限、Activity、分享 Intent）
│   │   ├── assets/
│   │   │   ├── abogus/abogus.js         # a_bogus 签名算法（JS，WebView 内执行）
│   │   │   └── extract.js               # WebView 拦截数据提取脚本
│   │   ├── java/com/douyin/crawler/
│   │   │   ├── MainActivity.kt          # 主界面（解析、合集、下载、分页、设置）
│   │   │   ├── ApiClient.kt             # 抖音 Web API 请求（detail、合集列表、a_bogus）
│   │   │   ├── Aweme.kt                 # 数据模型（Aweme、MixInfo、MixEpisode）
│   │   │   ├── AwemeParser.kt           # JSON 解析（视频/图文/合集分页数据）
│   │   │   ├── WebViewLoader.kt         # WebView 拦截器（加载页面、捕获 API 响应）
│   │   │   ├── Downloader.kt            # 视频/图文下载（OkHttp + MediaStore）
│   │   │   ├── GallerySaver.kt          # MediaStore 保存到相册
│   │   │   ├── CookieStore.kt           # Cookie 持久化（SharedPreferences）
│   │   │   └── LocalStorage.kt          # 设置项存储
│   │   └── res/
│   │       ├── layout/
│   │       │   ├── activity_main.xml    # 主界面布局（解析区、结果卡片、合集卡片、分页）
│   │       │   └── item_mix_episode.xml # 合集选集列表项（封面+标题+勾选）
│   │       ├── drawable/                # 图标、背景 drawable
│   │       └── values/
│   │           ├── strings.xml          # 字符串资源
│   │           ├── colors.xml           # 颜色定义
│   │           └── themes.xml           # 主题样式
│   └── build.gradle                     # 应用级构建配置（版本号、签名、依赖）
├── build.gradle                         # 根项目构建配置
├── settings.gradle                      # 项目设置（仓库镜像、模块声明）
├── gradle.properties                    # Gradle 属性（JVM 内存、AndroidX）
├── setup.bat                            # 一键搭建构建环境（JDK + SDK）
├── .gitignore                           # Git 忽略规则
├── DISCLAIMER.md                        # 免责声明
├── REQUIREMENTS.md                      # 通用化改造需求文档
└── README.md                            # 项目说明
```

## 技术参考

- [douyin-downloader](https://github.com/jiji262/douyin-downloader.git)

## 技术栈

- 原生 Android (Kotlin)
- WebView 拦截 + a_bogus 签名
- MediaStore API 保存到相册
- OkHttp 网络请求
- JSBridge（WebView ↔ Kotlin 通信）

## 系统要求

- Android 7.0+ (API 24)
- 网络连接

## 更新日志

### v1.0.1 (2026-08-19)

- 新增合集批量下载功能（自动识别合集/短剧）
- 新增选集分页系统（每页 20 集，支持指定页跳转）
- 新增解析进度条（实时显示已获取集数）
- 新增设置页面（登录管理、合集解析开关）
- 修复 detail 接口合集信息提取（适配 series_info / series_basic_info 字段）

### v1.0.0 (2026-08-18)

- 首版发布
- 支持下载抖音视频和图文
- 支持抖音分享面板直接解析
- 支持短链和完整链接
- WebView 登录

## 免责声明

本工具**仅用于学习和研究目的**。使用前请阅读完整的 [DISCLAIMER.md](DISCLAIMER.md)。

- 用户应自行承担使用本软件的一切法律责任
- 仅可下载自己拥有合法权利的内容
- 不得用于商业用途或侵犯他人权益
- 请遵守当地法律法规及抖音平台服务条款

**不同意上述条款者请立即停止使用并删除本软件。**

## License

MIT