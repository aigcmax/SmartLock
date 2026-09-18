# 智能门锁 App (SmartLock)

> 一款基于蓝牙 BLE 的智能门锁 Android 应用，让忘带钥匙的上班族也能轻松开门。

[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-blue.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Compose-BOM%202024.12.01-brightgreen.svg)](https://developer.android.com/jetpack/compose)
[![minSdk](https://img.shields.io/badge/minSdk-26-orange.svg)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

---

## 简介

**SmartLock** 是一款通过蓝牙 BLE 与智能门锁通信的 Android 应用。用户无需实体钥匙，打开 App 即可完成解锁/上锁。App 采用极简设计，首页只保留一个主按钮和连接状态，操作路径最短。

配合 [ESP32-C3 固件](../firmware/) 使用，基于 **Nordic UART Service (NUS)** 协议通信。

---

## 功能特性

- 🔓 **一键解锁/上锁** — 首页单按钮操作，立即响应
- 🔍 **自动扫描匹配** — 首次使用自动发现附近门锁
- 🔐 **密码加密存储** — AES-256/GCM + Android Keystore，本地不落明文
- 🎨 **极简首页** — 只有主按钮和连接状态，无多余入口
- 🔄 **自动重连** — 打开 App 或断线后自动重连已匹配设备
- 🛡️ **权限引导完善** — 蓝牙权限被拒后引导跳转系统设置
- 📡 **NUS 协议通信** — 兼容 ESP32-C3 / HM-10 / nRF52 等主流 BLE 模块
- 🧪 **模拟模式** — 无硬件也能跑通完整业务流程

---

## 界面预览

|                                          启动页                                           |                                        扫描设备                                        |                 上锁                  |                                           解锁                                           |
|:--------------------------------------------------------------------------------------:|:----------------------------------------------------------------------------------:|:-----------------------------------:|:--------------------------------------------------------------------------------------:|
| ![Splash](https://github.com/aigcmax/SmartLock/blob/master/docs/images/app-splash.png) | ![Scan](https://github.com/aigcmax/SmartLock/blob/master/docs/images/app-scan.png) | ![Lock](https://github.com/aigcmax/SmartLock/blob/master/docs/images/app-lock.png) | ![Unlock](https://github.com/aigcmax/SmartLock/blob/master/docs/images/app-unlock.png) |

---

## 技术栈

| 分类   | 技术                          | 版本             |
|------|-----------------------------|----------------|
| 语言   | Kotlin                      | 2.2.10         |
| UI   | Jetpack Compose + Material3 | BOM 2026.02.01 |
| 架构   | MVVM + Repository           | —              |
| 导航   | Navigation Compose          | 2.8.5          |
| 本地存储 | Room                        | 2.7.1          |
| 加密   | Android Keystore (AES-GCM)  | —              |
| 异步   | Coroutines + Flow           | 1.9.0          |
| 蓝牙   | Android BluetoothGatt API   | —              |
| 构建   | AGP                         | 9.3.0          |
| JDK  | 17                          | —              |


---

## 项目结构

    app/src/main/java/com/example/smartlock/
    ├── SmartLockApp.kt                Application 入口
    ├── MainActivity.kt                主 Activity
    ├── di/                            依赖注入
    │   ├── AppContainer.kt
    │   └── AppViewModelFactory.kt
    ├── domain/                        领域层
    │   ├── model/Models.kt
    │   └── repository/LockRepository.kt
    ├── data/                          数据层
    │   ├── local/                     Room 数据库
    │   │   ├── LockDeviceEntity.kt
    │   │   ├── LockDeviceDao.kt
    │   │   └── AppDatabase.kt
    │   ├── security/                  AES/GCM 加密
    │   │   └── PasswordCipher.kt
    │   └── repository/
    │       └── LockRepositoryImpl.kt
    ├── ble/                           蓝牙通信层
    │   ├── BleManager.kt              抽象接口
    │   ├── FakeBleManager.kt          模拟实现
    │   └── uart                       NUS协议
    │       ├── RealBleManager.kt      NUS 真机实现
    │       ├── BleUartConfig.kt       UUID / 超时配置
    │       └── BleUartProtocol.kt     帧编解码 + CRC16
    └── ui/                            UI 层
        ├── theme/                     主题
        ├── common/                    通用组件
        ├── navigation/                导航
        ├── splash/                    启动页
        ├── scan/                      设备扫描
        ├── password/                  密码设置/修改
        ├── home/                      首页
        └── manage/                    设备管理

---

## 快速开始

### 环境要求

| 项 | 版本                     |
|---|------------------------|
| Android Studio | Ladybug 2024.2.1 或更高   |
| JDK | 17                     |
| Gradle | 9.5.0（wrapper 自带）      |
| Android SDK | API 35                 |
| 设备 | Android 8.0（API 26）及以上 |

---

## 快速开始

### 克隆与编译

```bash
# 克隆仓库
git clone https://github.com/yourname/smart-lock.git
cd smart-lock

# 编译 Debug 版本
./gradlew :app:assembleDebug

# 安装到已连接设备
./gradlew :app:installDebug
```

### Android Studio 运行

1. 打开 `smart-lock/` 目录。
2. 等待 Gradle Sync 完成。
3. 连接 Android 设备或启动模拟器。
4. 点击 **Run ▶**。

---

## 使用说明

### 首次使用

1. 打开 App → 进入扫描页。
2. 授予蓝牙权限（Android 12+ 需要“附近的设备”权限）。
3. 等待扫描 → 列表出现门锁设备，例如 `ESP32-C3-Lock`。
4. 点击设备 → 建立连接。
5. 设置密码 → 输入 6 位数字密码，同步至门锁。
6. 进入首页 → 开始使用。

### 日常使用

| 操作        | 效果        |
| --------- | --------- |
| 短按主按钮     | 解锁或上锁     |
| 长按主按钮 3 秒 | 进入设备管理    |
| 打开 App    | 自动重连已匹配门锁 |

### 修改密码

首页长按主按钮 → **设备管理** → **修改密码** → 验证旧密码 → 设置新密码。

---

## 蓝牙协议

App 与门锁基于 **Nordic UART Service (NUS)** 通信。

### NUS UUID

| 项       | UUID                                   |
| ------- | -------------------------------------- |
| Service | `6E400001-B5A3-F393-E0A9-E50E24DCCA9E` |
| RX（写）   | `6E400002-B5A3-F393-E0A9-E50E24DCCA9E` |
| TX（通知）  | `6E400003-B5A3-F393-E0A9-E50E24DCCA9E` |

### 帧格式

```text
[0xAA][0x55][CMD][LEN][PAYLOAD...][CRC_H][CRC_L]
```

* **CRC**：CRC16-MODBUS（多项式 `0x1021`，初值 `0xFFFF`）。

### 命令码

| 命令码    | 含义   | Payload     |
| ------ | ---- | ----------- |
| `0x01` | 解锁   | 6 位密码 ASCII |
| `0x02` | 上锁   | 6 位密码 ASCII |
| `0x03` | 设置密码 | 6 位密码 ASCII |
| `0x10` | ACK  | 空           |
| `0x11` | NACK | 1 字节错误码     |

详细协议说明见 [通信协议文档](docs/通信协议.md)。

---

## 硬件对接

App 需配合支持 **NUS 协议** 的 BLE 门锁使用。

已测试硬件：

* ✅ ESP32-C3（参考固件见 `firmware/`）
* ✅ HM-10 / AT-09 系列
* ✅ nRF52 系列

### 适配其他硬件

如果硬件使用非 NUS 的 UUID，修改：

`ble/BleUartConfig.kt`

```kotlin
// 示例：HM-10 的 UUID
val SERVICE_UUID =
    UUID.fromString("0000FFE0-0000-1000-8000-00805F9B34FB")

val RX_CHARACTERISTIC_UUID =
    UUID.fromString("0000FFE1-0000-1000-8000-00805F9B34FB")

val TX_CHARACTERISTIC_UUID =
    UUID.fromString("0000FFE1-0000-1000-8000-00805F9B34FB")
```

若硬件帧格式不同，修改：

`ble/BleUartProtocol.kt`

中的 `buildFrame()` 和 `tryParse()`。

---

## 模拟器调试

模拟器通常无可用的真实蓝牙硬件，可以切换到模拟模式来跑通 UI 流程。

在 `di/AppContainer.kt` 中：

```kotlin
val bleManager: BleManager =
    if (Build.FINGERPRINT.contains("generic") ||
        Build.FINGERPRINT.contains("emulator")
    ) {
        FakeBleManager(appContext) // 模拟数据
    } else {
        RealBleManager(appContext) // 真实蓝牙
    }
```

`FakeBleManager` 会返回 3 台模拟设备，可以完整走通：

**扫描 → 匹配 → 设置密码 → 解锁 / 上锁**

---

## 调试技巧

### 查看 BLE 收发字节

在 `RealBleManager` 的 `writeChunk` 和 `onIncoming` 中添加日志：

```kotlin
Log.d(
    tag,
    "TX → ${data.joinToString(" ") { "%02X".format(it) }}"
)

Log.d(
    tag,
    "RX ← ${data.joinToString(" ") { "%02X".format(it) }}"
)
```

查看日志：

```bash
adb logcat | grep RealBleManager
```

### 常用 adb 命令

```bash
# 授予蓝牙权限
adb shell pm grant com.example.smartlock android.permission.BLUETOOTH_SCAN
adb shell pm grant com.example.smartlock android.permission.BLUETOOTH_CONNECT

# 重置权限
adb shell pm reset-permissions com.example.smartlock

# 卸载重装（清除图标/权限缓存）
adb uninstall com.example.smartlock
```

### 用 nRF Connect 独立验证硬件

1. 安装 [nRF Connect](https://www.nordicsemi.com/Products/Development-tools/nRF-Connect-for-mobile)。
2. 扫描 → 确认能看到门锁。
3. 连接 → 展开 GATT。
4. 确认 NUS 服务和特征存在。

---

## 常见问题

<details>
<summary><b>扫不到设备怎么办？</b></summary>

* 确认门锁已上电并正在广播。
* 检查 `RealBleManager.deviceNamePrefixes` 是否匹配硬件名称。
* 临时将 `SHOW_ALL_DEVICES` 设置为 `true`，查看全部设备。
* 使用 nRF Connect 确认硬件本身广播正常。

</details>

<details>
<summary><b>权限弹窗不出现？</b></summary>

系统可能已经记住“拒绝”状态。

执行：

```bash
adb shell pm reset-permissions com.example.smartlock
```

然后重新打开 App。

</details>

<details>
<summary><b>密码同步失败？</b></summary>

多半是固件未正确返回 ACK。

检查固件端：

* 是否正确解析 `SET_PASSWORD` 命令。
* 是否调用 `sendResponse()` 返回 ACK。
* 串口是否打印类似：

```text
[TX] AA 55 10 00 ...
```

</details>

<details>
<summary><b>编译报 AppDatabase_Impl does not exist？</b></summary>

可能是 KSP 未生效。

检查：

1. 根目录 `build.gradle.kts` 中是否有：

   ```kotlin
   id("com.google.devtools.ksp")
   ```

2. `app/build.gradle.kts` 顶部是否正确应用 KSP 插件。

3. KSP 版本前半段是否与 Kotlin 版本一致。

   例如：

   ```text
   Kotlin 2.0.21
   KSP 2.0.21-1.0.28
   ```

4. 执行：

   ```bash
   ./gradlew clean
   ```

   然后重新 Rebuild。

</details>

<details>
<summary><b>编译报 unexpected jvm signature V？</b></summary>

可能是 KSP 与 Room 的兼容性问题。

升级 Room 到 **2.7.1**：

```kotlin
implementation("androidx.room:room-runtime:2.7.1")
implementation("androidx.room:room-ktx:2.7.1")
ksp("androidx.room:room-compiler:2.7.1")
```

</details>

<details>
<summary><b>启动时白屏一闪？</b></summary>

可能缺少系统 Splash 配置。

添加依赖：

```kotlin
implementation("androidx.core:core-splashscreen:1.0.1")
```

并在 `themes.xml` 中配置 `Theme.SplashScreen`。

</details>

---

## 项目状态

| 功能        | 状态     |
| --------- | ------ |
| 蓝牙权限引导    | ✅ 完成   |
| 设备扫描与匹配   | ✅ 完成   |
| 设置密码      | ✅ 完成   |
| 解锁 / 上锁   | ✅ 完成   |
| 修改密码      | ✅ 完成   |
| 自动重连      | ✅ 完成   |
| NUS 通信    | ✅ 完成   |
| 多门锁管理     | 🚧 规划中 |
| 开门记录      | 🚧 规划中 |
| 指纹 / 面容验证 | 🚧 规划中 |

---

## 贡献指南

欢迎提交 Issue 和 Pull Request。

### 分支策略

* `main` — 稳定发布版本
* `develop` — 开发主分支
* `feature/*` — 功能分支（从 `develop` 切出）
* `hotfix/*` — 紧急修复

### 提交规范

遵循 [Conventional Commits](https://www.conventionalcommits.org/)：

```text
feat(ble): 新增 NUS 分包写入支持
fix(ui): 修正首页按钮点击无响应
docs(readme): 补充硬件接线说明
```

### 提交 PR 前

* [ ] 代码通过 `./gradlew :app:assembleDebug`
* [ ] 无编译警告
* [ ] 已在真机上验证 BLE 功能
* [ ] 更新了相关文档

---

## 相关文档

* [完整说明文档](docs/项目说明文档.md)
* [通信协议](docs/通信协议.md)
* [ESP32-C3 固件](firmware/README.md)

---

## 开源协议

本项目采用 [MIT License](LICENSE) 开源。

---

## 联系

* 提交 Issue：https://github.com/yourname/smart-lock/issues
* 邮箱：[dev@example.com](mailto:dev@example.com)

---

<p align="center">
  Built with ❤️ using Kotlin &amp; Jetpack Compose
</p>
