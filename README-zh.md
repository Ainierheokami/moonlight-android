# 🎮 Moonlight Android - AinierHeokami 高级定制版 (AinierHeokami Edition)

> 🇨🇳 简体中文 | [🇬🇧 English](README.md)

[![Android Platform](https://img.shields.io/badge/Platform-Android_5.0%2B-brightgreen.svg?style=flat-square)](https://developer.android.com)
[![Gradle Build](https://img.shields.io/badge/Gradle-9.4.1-blue.svg?style=flat-square)](https://gradle.org)
[![NDK Version](https://img.shields.io/badge/NDK-27.0-orange.svg?style=flat-square)](https://developer.android.com/ndk)
[![License](https://img.shields.io/badge/License-GPL_v3-red.svg?style=flat-square)](LICENSE.txt)

> **✨ AinierHeokami 定制版**：基于官方 Moonlight Android 客户端深度重构与高级扩展的串流神器。专为极致玩家和高度定制需求打造，融入了精简性能监控、强力虚拟键盘宏与自定义映射、长按阻尼防误触微交互、以及系统级安全备份机制，给您带来丝滑、可深度定制的跨平台云端游戏体验。

---

## 🚀 核心亮点特性 (Key Features)

### 📺 实时性能监控与控制 (Performance Overlay & Control)
*   **精简性能面板**：内置轻量化、精简的实时性能展示悬浮窗，串流帧率、码率及网络延迟一手掌握。
*   **自订监控模板**：支持高度自定义性能展示模板，按需配置监控指标。
*   **快捷返回菜单**：串流过程中可随时唤出快捷菜单，在触摸模式、虚拟手柄与虚拟键盘之间实现秒级无缝切换。

### 💾 系统级备份与还原 (Backup & Recovery)
*   **一键全量备份**：支持将您的所有串流偏好参数、已配对电脑的主机列表、虚拟键盘配置以及自定义热键一键导出为本地文件。
*   **双模安全导入**：支持跨设备安全导入与本征快速还原；导入配置成功后，应用将自动秒级热重启，即时生效无需手动杀进程。

### ⌨️ 高级虚拟键盘与快捷键管理器 (OSK & Hotkeys Pro)
*   **多机制按键映射**：支持十进制和十六进制按键码自适应映射，满足各种复杂 PC 游戏的自定义按键需求。
*   **按键编组与切换**：支持对虚拟按键进行灵活编组管理，并提供一键批量隐藏或显示特定按键组的功能。
*   **简单键盘宏指令**：支持自定义组合按键序列与简单宏逻辑，化繁为简一键释放组合招式。
*   **功能扩展支持**：内置高灵敏度悬浮键盘、虚拟手柄以及自定义物理/虚拟快捷键扩展，完美平替实体外设。

### 🎨 现代化交互与视觉美学 (Premium UX/UI)
*   **长按防误触退出**：退出串流按钮支持物理触觉震动反馈与平滑无缝的背景色阻尼渐变过渡（红色 $\rightarrow$ 黄色），彻底杜绝游戏中途意外退出的尴尬。
*   **现代化分类菜单**：重构设置面板结构，将“系统备份与还原”独立为顶级入口，菜单层级更加清晰直观。

### 📂 智能联合过滤文件选择器 (SAF File Picker)
*   **深度适配定制 ROM**：完美解决小米 (MIUI/HyperOS) 等高度定制系统对 SAF (Storage Access Framework) 过滤器的解析 Bug。
*   **智能联合过滤**：基于多类型联合过滤，快速精准呈现 `.txt`、`.json` 等格式备份文件，查找导入更简单。

---

## 🏗 现代化构建指南 (Modern Build Guide)

本项目工程栈已彻底升级至现代化 Android 编译生态，支持极速、稳定的本地编译。

### 前置条件
1.  **JDK 17 / 21** 开发环境。
2.  **Android SDK** 编译版本 (compileSdk) `35`。
3.  **Android NDK** 版本 `27.0.12077973`。

### 快速构建步骤
1.  **拉取子模块**：
    ```bash
    git submodule update --init --recursive
    ```
2.  **配置环境**：
    在根目录下创建 `local.properties` 文件，指定您的 SDK 与 NDK 路径：
    ```properties
    sdk.dir=/your/path/to/Android/Sdk
    ndk.dir=/your/path/to/Android/Sdk/ndk/27.0.12077973
    ```
3.  **使用 Gradle 构建 APK**（非 Root 调试版）：
    *   **Windows (PowerShell)**:
        ```powershell
        .\gradlew.bat assembleNonRootDebug
        ```
    *   **Linux / macOS**:
        ```bash
        ./gradlew assembleNonRootDebug
        ```
    构建完成的 APK 将输出至：`app/build/outputs/apk/nonRoot/debug/`

---

## 👥 贡献者与致谢 (Credits)

*   **项目定制与重构**：[ainierheokami](https://github.com/ainierheokami)
*   **官方上游核心客户端**：[Moonlight Stream Team](https://github.com/moonlight-stream)
*   **经典 GameMenu 菜单参考**：[kmreisi](https://github.com/kmreisi/limelight-android)

---

> **⚠️ 免责声明**：本定制版软件仅供个人学习、交流及云游戏串流技术研究使用。请在遵守当地法律法规的前提下合理使用。
