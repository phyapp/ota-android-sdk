# OTA Android SDK

基于 BLE 的 OTA 固件升级 SDK，支持 SLB 和 Single Bank（SBK）两种 OTA 协议。

## 集成方式

在项目的 `build.gradle`（Module 级别）中添加依赖：

```gradle
dependencies {
    implementation 'io.github.phyapp:otalib:2.3.9'
}
```

无需额外配置 Maven 仓库地址（`mavenCentral()` 默认包含）。

### 最低要求

- minSdk: 23 (Android 6.0)
- compileSdk: 35

## 快速开始

### 1. 声明权限

SDK 的 `AndroidManifest.xml` 已声明 `BLUETOOTH_CONNECT` 和 `BLUETOOTH_SCAN` 权限，但 Android 12+ 需要在运行时动态申请：

```kotlin
val bluetoothScanPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    Manifest.permission.BLUETOOTH_SCAN
} else {
    Manifest.permission.ACCESS_FINE_LOCATION
}
```

### 2. 初始化 OTACore

```java
// 单例模式，全局只需一个实例
OTACore otaCore = new OTACore(context);
// 或
OTACore otaCore = OTACore.getInstance(context);
```

### 3. 设置扫描回调

```java
otaCore.setPhyScanCallback(new OTAScanCallback() {
    @Override
    public void onScanResult(ScanResult result) {
        // 每扫描到一个设备会回调一次
        OTADevice device = new OTADevice(result);
        String name = device.getRealName();
        String mac = device.getMacAddress();
        int rssi = device.getRssi();
    }
});
```

### 4. 开始/停止扫描

```java
otaCore.startScan();
// ...
otaCore.stopScan();
```

### 5. 设置 OTA 状态回调

```java
otaCore.setOtaCallback(new OTACallback() {
    @Override
    public void bleAdapterNotify(int code, String message) {
        // 蓝牙适配器/定位状态变化
        // code: 10001-10006
    }

    @Override
    public void deviceNotify(int code, String message, OTADevice device) {
        // 每个设备的 OTA 状态变化
        // code 对应 OtaState 中的 1100x 值
    }

    @Override
    public void onLogOutput(OTALog log) {
        // SDK 内部日志输出（可选实现）
    }
});
```

`bleAdapterNotify` 的 code 说明：

| Code | 说明 |
|------|------|
| 10001 | 手机蓝牙已关闭 |
| 10002 | 手机蓝牙已打开 |
| 10003 | 手机定位已关闭 |
| 10004 | 手机定位已打开 |
| 10005 | 固件文件检测结果（ProductID + BooterVersion） |
| 10006 | 固件文件为空 |

### 6. 选择升级设备

```java
// 从扫描结果中选取需要升级的设备
List<OTADevice> selectedDevices = new ArrayList<>();
selectedDevices.add(new OTADevice(scanResult));
// ...
otaCore.setDevices(selectedDevices);
```

### 7. 选择固件文件

```java
// .bin 文件 → SLB 协议
// .hex / .hex4 / .hex16 / .res / .hexe16 文件 → SBK 协议
otaCore.selectFile("/path/to/firmware.bin");
```

### 8. 设置 AES 加密密钥（可选）

如果固件升级需要 AES 加密认证：

```java
otaCore.setKey("你的32位十六进制密钥");
```

### 9. 开始升级

```java
otaCore.startUpgrade();
```

### 10. 取消升级

```java
otaCore.cancelUpgrade();
```

## 完整示例

```java
public class OtalibDemo {

    private OTACore otaCore;
    private final List<OTADevice> deviceList = new ArrayList<>();

    public void start(Context context) {
        // 1. 初始化
        otaCore = new OTACore(context);

        // 2. 扫描回调
        otaCore.setPhyScanCallback(new OTAScanCallback() {
            @Override
            public void onScanResult(ScanResult result) {
                OTADevice device = new OTADevice(result);
                if (!deviceList.contains(device)) {
                    deviceList.add(device);
                    // 更新 UI
                }
            }
        });

        // 3. OTA 状态回调
        otaCore.setOtaCallback(new OTACallback() {
            @Override
            public void bleAdapterNotify(int code, String message) {
                Log.d("OTALib", "Adapter: " + message);
            }

            @Override
            public void deviceNotify(int code, String message, OTADevice device) {
                Log.d("OTALib", "Device " + device.getMacAddress()
                        + " -> [" + code + "] " + message);

                if (code == 11027) {
                    // 进度更新：device.getProcess()
                    int progress = (int) device.getProcess();
                } else if (code == 11026) {
                    // 升级完成
                } else if (device.getOtaType().isError()) {
                    // 升级失败
                }
            }
        });

        // 4. 开始扫描
        otaCore.startScan();
    }

    public void startUpgrade(String filePath) {
        // 勾选设备
        otaCore.setDevices(deviceList);

        // 选择固件
        otaCore.selectFile(filePath);

        // 开始升级
        otaCore.startUpgrade();
    }

    public void stop() {
        otaCore.stopScan();
        otaCore.cancelUpgrade();
    }
}
```

## OTA 状态码

所有状态码定义在 `OtaState` 枚举中，通过 `deviceNotify` 的 `code` 参数回调。

### 连接阶段

| Code | OtaState | 说明 |
|------|----------|------|
| 11001 | CONNECTING | 连接中 |
| 11002 | CONNECTED_DISCOVERING | 连接成功，发现服务中 |
| 11003 | DISCONNECTED | 已断开连接 |

### 服务发现阶段

| Code | OtaState | 说明 |
|------|----------|------|
| 11004 | SBK_APP_SERVICE | SBK App 服务发现成功 |
| 11005 | SBK_OTA_SERVICE | SBK OTA 服务发现成功 |
| 11006 | SLB_OTA_SERVICE | SLB 服务发现成功 |
| 11007 | ERROR_NO_SERVICE | 未找到 OTA 蓝牙服务 |
| 11008 | ERROR_ABNORMAL_CHARACTERISTIC | 特征异常 |
| 11010~11012 | CONFIRMED_xxx | 确认协议模式 |

### 就绪阶段

| Code | OtaState | 说明 |
|------|----------|------|
| 11020 | ENABLED_SBK_APP_READY | SBK App 模式已就绪 |
| 11021 | ENABLED_SBK_OTA_READY | SBK OTA 模式已就绪 |
| 11022 | ENABLED_SLB_READY | SLB 模式已就绪 |

### 升级阶段

| Code | OtaState | 说明 |
|------|----------|------|
| 11025 | FEEDBACK_BOOT_VERSION | 获取到设备 Boot 版本 |
| 11027 | FEEDBACK_PROGRESS | 传输进度（`device.getProcess()`） |
| 11023 | APP_MODE_END_WAITING_SCAN | App 模式结束，等待二次扫描 |
| 11026 | UPGRADE_COMPLETE_WAITING_DISCONNECT | 升级完成 |

### 错误码

| Code | OtaState | 说明 |
|------|----------|------|
| 11014 | ERROR_FILE_MISMATCH | 文件与芯片类型不匹配 |
| 11015 | ERROR_MTU_CHANGE | MTU 协商失败 |
| 11016 | ERROR_MTU_MISMATCH | MTU 不一致（多设备并发） |
| 11017 | ENABLE_FAILED_RECONNECT | Enable 失败，自动重连 |
| 11028 | ERROR_CANNOT_CONNECT | 无法连接设备（重连超过 3 次） |
| 11030 | ERROR_FIRMWARE_FEEDBACK | 固件端返回错误 |

### 判断方法

```java
// 是否为不可恢复的错误
device.getOtaType().isError();

// 是否为终态（错误或升级完成）
device.getOtaType().isTerminal();

// 从 code 转 OtaState
OtaState state = OtaState.fromCode(code);
```

## 支持的固件文件格式

| 文件后缀 | 协议 | 说明 |
|----------|------|------|
| `.bin` | SLB | Service-Level Binding OTA |
| `.hex` | SBK | Single Bank OTA (App 模式) |
| `.hex4` | SBK | Single Bank OTA |
| `.hex16` | SBK | Single Bank OTA |
| `.hexe16` | SBK | Single Bank OTA（含 AES 加密认证） |
| `.res` | SBK | 资源文件升级 |

## 多设备并发

SDK 支持同时升级最多 6 台设备：

```java
// 添加多台设备
otaCore.setDevices(deviceList);  // deviceList 可包含多台设备

// SDK 会按顺序自动连接并开始升级
// 每台设备的升级进度通过 deviceNotify() 独立回调
```

## 内部类说明

以下类为 SDK 内部使用，通常情况下不需要直接操作：

- `SHBContext` / `SLBContext` — 各协议的数据传输上下文
- `SHBFile` / `SLBFile` — 固件文件解析
- `Partition` — 固件分区数据结构
- `ShbOtaHandler` / `SlbOtaHandler` — 协议处理
- `GattCallbackHandler` / `BleConnectionManager` — BLE GATT 连接管理
