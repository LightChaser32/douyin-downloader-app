@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul

set "ROOT=%~dp0"
set "PREBUILT=%ROOT%prebuilt"
set "JDK_DIR=%PREBUILT%\jdk"
set "SDK_DIR=%PREBUILT%\android-sdk"

echo ============================================
echo   抖音资源下载器 - 一键搭建构建环境
echo ============================================
echo.

REM ---------- 1. JDK 17 ----------
if exist "%JDK_DIR%\bin\java.exe" (
    echo [1/4] JDK 17 已存在，跳过下载
) else (
    echo [1/4] 下载 JDK 17（Windows x64）...
    if not exist "%PREBUILT%" mkdir "%PREBUILT%"
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse' -OutFile '%PREBUILT%\jdk17.zip'"
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Path '%PREBUILT%\jdk17.zip' -DestinationPath '%PREBUILT%\' -Force"
    for /d %%D in ("%PREBUILT%\jdk-17*") do (
        if not exist "%JDK_DIR%" move "%%D" "%JDK_DIR%" >nul
    )
    del "%PREBUILT%\jdk17.zip" 2>nul
    if exist "%JDK_DIR%\bin\java.exe" (
        echo      JDK 17 安装完成
    ) else (
        echo      JDK 17 安装失败，请手动安装后重试
    )
)

REM ---------- 2. Android SDK ----------
if exist "%SDK_DIR%\cmdline-tools\latest\bin\sdkmanager.bat" (
    echo [2/4] Android SDK 已存在，跳过下载
) else (
    echo [2/4] 下载 Android SDK 组件...
    if not exist "%SDK_DIR%" mkdir "%SDK_DIR%"
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri 'https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip' -OutFile '%PREBUILT%\cmdline-tools.zip'"
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Path '%PREBUILT%\cmdline-tools.zip' -DestinationPath '%SDK_DIR%\' -Force"
    if not exist "%SDK_DIR%\cmdline-tools\latest" mkdir "%SDK_DIR%\cmdline-tools\latest"
    move "%SDK_DIR%\cmdline-tools\cmdline-tools\*" "%SDK_DIR%\cmdline-tools\latest\" >nul
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri 'https://dl.google.com/android/repository/platform-tools-latest-windows.zip' -OutFile '%PREBUILT%\platform-tools.zip'"
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Path '%PREBUILT%\platform-tools.zip' -DestinationPath '%SDK_DIR%\' -Force"
    del "%PREBUILT%\cmdline-tools.zip" "%PREBUILT%\platform-tools.zip" 2>nul

    echo      安装 platforms;android-34 与 build-tools;34.0.0（需联网，请稍候）...
    call "%SDK_DIR%\cmdline-tools\latest\bin\sdkmanager.bat" "platforms;android-34" "build-tools;34.0.0" >nul
    call "%SDK_DIR%\cmdline-tools\latest\bin\sdkmanager.bat" --licenses < nul
    echo      Android SDK 安装完成
)

REM ---------- 3. local.properties ----------
echo [3/4] 生成 local.properties ...
(
    echo sdk.dir=%SDK_DIR:\=\\%
) > "%ROOT%local.properties"

REM ---------- 4. keystore.properties ----------
if exist "%ROOT%keystore.properties" (
    echo [4/4] keystore.properties 已存在，跳过
) else (
    echo [4/4] 配置 release 签名（仅构建 release 需要，可留空直接回车跳过）
    set /p KEYSTORE_FILE=请填写 douyin_release.jks 的完整路径：
    if not "!KEYSTORE_FILE!"=="" (
        set /p KEYSTORE_PASSWORD=storePassword 密码：
        set /p KEY_ALIAS=keyAlias：
        set /p KEY_PASSWORD=keyPassword：
        (
            echo storeFile=!KEYSTORE_FILE:\=\\!
            echo storePassword=!KEYSTORE_PASSWORD!
            echo keyAlias=!KEY_ALIAS!
            echo keyPassword=!KEY_PASSWORD!
        ) > "%ROOT%keystore.properties"
        echo      已生成 keystore.properties（已被 .gitignore 排除，不会上传）
    ) else (
        echo      已跳过签名配置，release 将走无签名构建
    )
)

echo.
echo ============================================
echo   环境搭建完成！
echo   现在可运行:  gradlew.bat assembleDebug
echo   或生成签名包: gradlew.bat assembleRelease
echo ============================================
pause
