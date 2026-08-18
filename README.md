# 抖音资源下载器 (DouyinDownloader)

一键下载抖音视频、图文的 Android 工具。支持从抖音 App 分享链接自动解析下载。

## 功能

- 下载抖音视频（无水印）
- 下载抖音图文/图集
- 抖音分享面板直接选择本 App 自动解析
- 支持短链 `v.douyin.com` 和完整链接
- 登录后可访问更多内容

## 安装

1. 下载 [Release APK](https://github.com/user/douyin-downloader-app/releases/latest)
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

1. 打开抖音，选择要下载的视频/图文
2. 点击分享 → 选择"抖音资源下载器"
3. App 自动解析链接并显示结果
4. 点击下载按钮保存到相册

### 手动粘贴链接

1. 在抖音复制分享链接
2. 打开本 App，点击"粘贴链接"
3. 点击"解析"

## 登录

首次使用需要登录抖音账号：

1. 点击右上角设置图标
2. 选择"打开登录页"
3. 在 WebView 中完成登录
4. 登录成功后按返回键

## 技术栈

- 原生 Android (Kotlin)
- WebView 拦截 + a_bogus 签名
- MediaStore API 保存到相册

## 系统要求

- Android 7.0+ (API 24)
- 网络连接

## 免责声明

本工具**仅用于学习和研究目的**。使用前请阅读完整的 [DISCLAIMER.md](DISCLAIMER.md)。

- 用户应自行承担使用本软件的一切法律责任
- 仅可下载自己拥有合法权利的内容
- 不得用于商业用途或侵犯他人权益
- 请遵守当地法律法规及抖音平台服务条款

**不同意上述条款者请立即停止使用并删除本软件。**

## License

MIT
