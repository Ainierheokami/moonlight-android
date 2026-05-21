# 🎮 Moonlight Android - AinierHeokami Premium Edition

> [🇨🇳 简体中文](README-zh.md) | 🇬🇧 English

[![Android Platform](https://img.shields.io/badge/Platform-Android_5.0%2B-brightgreen.svg?style=flat-square)](https://developer.android.com)
[![Gradle Build](https://img.shields.io/badge/Gradle-9.4.1-blue.svg?style=flat-square)](https://gradle.org)
[![NDK Version](https://img.shields.io/badge/NDK-27.0-orange.svg?style=flat-square)](https://developer.android.com/ndk)
[![License](https://img.shields.io/badge/License-GPL_v3-red.svg?style=flat-square)](LICENSE.txt)

> **✨ AinierHeokami Premium Edition**: A deeply refactored and highly extended game streaming client built upon the official Moonlight Android codebase. Tailored for power users and enthusiasts, it integrates minimalist performance overlays, advanced OSK macros with custom keymapping, intuitive haptic hold-to-disconnect transitions, and robust system-level backup & recovery. Experience a butter-smooth, deeply customizable cloud gaming journey.

---

## 🚀 Key Features

### 📺 Performance Overlay & Control
*   **Minimalist Performance Overlay**: Built-in lightweight floating performance monitor to check streaming frame rates, bit rates, and network latency at a glance.
*   **Customizable Monitor Templates**: Highly customizable layout templates for performance metrics based on your preferences.
*   **Quick Return Menu**: Summon the dashboard mid-stream to seamlessly hot-swap between touch mode, virtual gamepad, and custom OSK within a second.

### 💾 Backup & Recovery
*   **One-Click Full Backup**: Export all configuration settings, paired computer hostlists, custom OSK keymaps, and custom hotkeys into a single backup file.
*   **Dual-Mode Smart Import**: Auto-adapt to either full recovery on the same device or sandbox-downgraded import on a cross-device configuration. Hot-restart the app upon successful importing to apply changes without manual process killing.

### ⌨️ Advanced OSK & Hotkeys Pro
*   **Multi-Format Keymapping**: Supports adaptive mapping of both decimal and hexadecimal keycodes, covering diverse keys needed for PC gaming.
*   **Key Grouping & Toggles**: Organize custom buttons into groups to batch show/hide them to keep your gameplay UI clean and immersive.
*   **Simple OSK Macros**: Define sequences of key presses and combo macros to execute complex game operations with a single tap.
*   **Immersive OSK Extensions**: Built-in ultra-responsive floating keyboard, virtual joystick, and physical/virtual hotkey triggers to replace hardware peripherals.

### 🎨 Premium UX/UI
*   **Hold-to-Disconnect with Damping Transition**: Prevent accidental stream disconnection via haptic vibration feedback and a smooth `ValueAnimator`-driven background color gradient transition (from active Red `#F44336` to warning Yellow `#FFD600`) while keeping white text sharp.
*   **Modernized Settings Hierarchy**: Rearranged settings categories, elevating "Backup & Recovery" to a top-level preference item.

### 📂 SAF File Picker Pro
*   **Custom ROM Compatibility**: Thoroughly fixes SAF (Storage Access Framework) filter bugs on heavily customized Android ROMs such as Xiaomi (MIUI/HyperOS).
*   **Smart Union MIME Filtering**: Employs multi-type SAF matching to instantly and cleanly show `.txt` and `.json` files without grey-out issues.

---

## 🏗 Modern Build Guide

The project engineering stack has been fully modernized, supporting rapid and stable local compilation.

### Prerequisites
1.  **JDK 17 / 21** development environment.
2.  **Android SDK** compilation version (compileSdk) `35`.
3.  **Android NDK** version `27.0.12077973`.

### Quick Build Steps
1.  **Pull Submodules**:
    ```bash
    git submodule update --init --recursive
    ```
2.  **Configure Environment**:
    Create a `local.properties` file in the root directory and specify your SDK and NDK paths:
    ```properties
    sdk.dir=/your/path/to/Android/Sdk
    ndk.dir=/your/path/to/Android/Sdk/ndk/27.0.12077973
    ```
3.  **Assemble Debug APK** (Non-Root Variant):
    *   **Windows (PowerShell)**:
        ```powershell
        .\gradlew.bat assembleNonRootDebug
        ```
    *   **Linux / macOS**:
        ```bash
        ./gradlew assembleNonRootDebug
        ```
    The compiled APK will be output to: `app/build/outputs/apk/nonRoot/debug/`

---

## 👥 Credits

*   **Project Tailoring & Refactoring**: [ainierheokami](https://github.com/ainierheokami)
*   **Official Upstream Client**: [Moonlight Stream Team](https://github.com/moonlight-stream)
*   **Classic GameMenu Reference**: [kmreisi](https://github.com/kmreisi/limelight-android)

---

> **⚠️ Disclaimer**: This premium edition client is developed strictly for personal learning, communication, and research on cloud game streaming technologies. Please use it reasonably under local laws and regulations.
