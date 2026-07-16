package com.ota.otalib.ble;

import static com.ota.otalib.model.DataType.RX;
import static com.ota.otalib.model.DataType.TX;
import static com.ota.otalib.model.LogType.LOG_D;
import static com.ota.otalib.model.LogType.LOG_E;
import static com.ota.otalib.model.LogType.LOG_I;
import static com.ota.otalib.utils.OTAConstant.*;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.ota.otalib.R;
import com.ota.otalib.model.ConnectState;
import com.ota.otalib.model.OtaState;
import com.ota.otalib.model.OTADevice;
import com.ota.otalib.model.SHBContext;
import com.ota.otalib.model.SLBContext;
import com.ota.otalib.utils.BleUtils;
import com.ota.otalib.utils.HexString;

import java.util.List;
import java.util.Locale;

/**
 * GATT回调处理，将协议相关的逻辑分发到 ShbOtaHandler 和 SlbOtaHandler
 */
@SuppressLint("MissingPermission")
public class GattCallbackHandler extends BluetoothGattCallback {

    private static final String TAG = "GattCallbackHandler";
    private static final int TIMEOUT = 10;
    /** 重连超时（秒）：连接建立阶段的超时，防止BLE回调丢失导致卡死 */
    private static final int RECONNECT_TIMEOUT = 5;
    /** 主线程Handler，用于连接超时、重连安全网等延时任务 */
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final OTACore core;
    private final ShbOtaHandler shbHandler;
    private final SlbOtaHandler slbHandler;
    private final BleConnectionManager connManager;

    public GattCallbackHandler(OTACore core, ShbOtaHandler shbHandler,
                               SlbOtaHandler slbHandler, BleConnectionManager connManager) {
        this.core = core;
        this.shbHandler = shbHandler;
        this.slbHandler = slbHandler;
        this.connManager = connManager;
    }

    /**
     * 添加超时监听处理
     */
    public void addTimeoutListener(OTADevice phyDevice, BluetoothGatt gatt) {
        phyDevice.setGatt(gatt);
        phyDevice.setRunTimer(true);

        Runnable timeoutTask = () -> {
            if (phyDevice.isRunTimer()) {
                phyDevice.setRunTimer(false);
                connManager.disconnectGatt(gatt);
            }
            phyDevice.clearTimeoutTask();
        };
        phyDevice.setTimeoutTask(mainHandler, timeoutTask);
        mainHandler.postDelayed(timeoutTask, TIMEOUT * 1000L);
    }

    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        String address = gatt.getDevice().getAddress();
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            OTADevice phyDevice = stopTimer(gatt);
            if (phyDevice.getOtaType() != OtaState.CONNECTING) {
                Log.e(TAG, "===========: 此时收到连接成功的回调：" + phyDevice.getOtaType().code);
                return;
            }
            core.updateDeviceState(address, OtaState.CONNECTED_DISCOVERING, "连接成功，发现服务中...", LOG_I);
            addTimeoutListener(phyDevice, gatt);
            gatt.discoverServices();
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            gatt.close();
            connManager.onDisconnected(address);
            for (OTADevice device : core.getDeviceList()) {
                if (device.getMacAddress().equals(address)) {
                    device.setConnectState(ConnectState.DISCONNECTED);
                    if (device.isRunTimer()) {
                        device.stopTimer();
                    }
                    List<String> cmdTempArray = core.getCmdTempMap().get(address);
                    if (cmdTempArray != null && !cmdTempArray.isEmpty()) {
                        cmdTempArray.clear();
                        core.getCmdTempMap().put(address, cmdTempArray);
                    }

                    if (device.getOtaType() == OtaState.APP_MODE_END_WAITING_SCAN) {
                        core.printLog(LOG_D, "切换OTA断开，开始重新扫描");
                    } else if (device.getOtaType() == OtaState.UPGRADE_COMPLETE_WAITING_DISCONNECT) {
                        core.getOtaCallback().deviceNotify(11032, "升级成功已断开", device);
                    } else if (device.getOtaType().isError()
                            && device.getOtaType() != OtaState.ERROR_CANNOT_CONNECT) {
                        core.getOtaCallback().deviceNotify(11031,
                                String.format(Locale.getDefault(), "设备异常: %d", device.getOtaType().code), device);
                    } else {
                        if (handleReconnectOrFail(device, address)) {
                            return;
                        }
                    }
                    break;
                }
            }
            core.connectNextDevice();
        }
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        String address = gatt.getDevice().getAddress();
        if (status != BluetoothGatt.GATT_SUCCESS) {
            core.updateDeviceState(address, OtaState.SBK_OTA_SERVICE, "发现服务异常断开", LOG_E);
            connManager.disconnectGatt(gatt);
            return;
        }
        stopTimer(gatt);

        OtaState otaType = BleUtils.getOTATypeForService(gatt.getServices());
        String addressKey = gatt.getDevice().getAddress();
        core.updateDeviceState(addressKey, otaType,
                BleUtils.getOTATypeInfo(core.getContext(), otaType.code), LOG_D);

        if (otaType == OtaState.ERROR_NO_SERVICE || otaType == OtaState.ERROR_ABNORMAL_CHARACTERISTIC
                || otaType == OtaState.ERROR_SERVICE_STATE || otaType == OtaState.ERROR_CHARACTERISTIC) {
            connManager.disconnectGatt(gatt);
        } else if ((otaType == OtaState.CONFIRMED_SLB && !core.getFilePath().endsWith(".bin"))
                || (otaType == OtaState.CONFIRMED_SBK_OTA && core.getFilePath().endsWith(".bin"))) {
            core.updateDeviceState(addressKey, OtaState.ERROR_FILE_MISMATCH,
                    BleUtils.getOTATypeInfo(core.getContext(), OtaState.ERROR_FILE_MISMATCH.code), LOG_D);
            connManager.disconnectGatt(gatt);
        } else {
            if (otaType == OtaState.CONFIRMED_SLB || otaType == OtaState.CONFIRMED_SBK_OTA) {
                gatt.requestMtu(517);
            } else {
                OTADevice phyDevice = BleUtils.getPhyDevice(core.getDeviceList(), address);
                core.enableNotifySerial(gatt, phyDevice);
            }
        }
    }

    @Override
    public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
        String address = gatt.getDevice().getAddress();
        if (status != BluetoothGatt.GATT_SUCCESS) {
            core.updateDeviceState(gatt.getDevice().getAddress(), OtaState.ERROR_MTU_CHANGE,
                    BleUtils.getOTATypeInfo(core.getContext(), OtaState.ERROR_MTU_CHANGE.code), LOG_D);
            connManager.disconnectGatt(gatt);
            return;
        }
        OTADevice phyDevice = BleUtils.getPhyDevice(core.getDeviceList(), address);
        if (phyDevice.getOtaType() == OtaState.CONFIRMED_SBK_OTA || phyDevice.getOtaType() == OtaState.CONFIRMED_SLB) {
            handleMtu(mtu, phyDevice);
            core.enableNotifySerial(gatt, phyDevice);
        }
    }

    @Override
    public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        int errCode = BleUtils.checkDescriptorWrite(descriptor, status);
        if (errCode != 0) {
            core.updateDeviceState(gatt.getDevice().getAddress(), OtaState.fromCode(errCode),
                    BleUtils.getOTATypeInfo(core.getContext(), errCode), LOG_D);
            connManager.disconnectGatt(gatt);
            return;
        }
        String address = gatt.getDevice().getAddress();
        OTADevice phyDevice = BleUtils.getPhyDevice(core.getDeviceList(), address);
        if (phyDevice != null) {
            phyDevice.stopTimer();
        }
        if (phyDevice.getOtaType() == OtaState.CONFIRMED_SBK_APP) {
            core.updateDeviceState(address, OtaState.ENABLED_SBK_APP_READY,
                    BleUtils.getOTATypeInfo(core.getContext(), OtaState.ENABLED_SBK_APP_READY.code), LOG_D);
            shbHandler.sendCmd(gatt, "02");
        } else if (phyDevice.getOtaType() == OtaState.CONFIRMED_SBK_OTA) {
            core.updateDeviceState(address, OtaState.ENABLED_SBK_OTA_READY,
                    BleUtils.getOTATypeInfo(core.getContext(), OtaState.ENABLED_SBK_OTA_READY.code), LOG_D);
            shbHandler.startSHBOTA(phyDevice);
        } else if (phyDevice.getOtaType() == OtaState.CONFIRMED_SLB) {
            core.updateDeviceState(address, OtaState.ENABLED_SLB_READY,
                    BleUtils.getOTATypeInfo(core.getContext(), OtaState.ENABLED_SLB_READY.code), LOG_D);
            if (core.getFilePath().endsWith("hexe16.bin")) {
                slbHandler.startSecuritySLB(phyDevice);
            } else {
                slbHandler.startSLBOTA(phyDevice);
            }
        }
    }

    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        String writeSuccessData = HexString.parseStringHex(characteristic.getValue());
        core.printLog(LOG_E, TX, String.format(Locale.getDefault(), "数据写入成功: %s", writeSuccessData));

        String uuid = characteristic.getUuid().toString().toUpperCase();
        switch (uuid) {
            case SHB_OTA_WRITE_CHARACTERISTIC:
            case SHB_OTA_WRITE_CHARACTERISTIC_NO_RSP:
                shbHandler.handleWrite(gatt, characteristic);
                break;
            case SLB_WRITE_CHARACTERISTIC:
            case SLB_WRITE_CHARACTERISTIC_NO_RSP:
                slbHandler.handleWrite(gatt, characteristic);
                break;
        }
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        String receiveData = HexString.parseStringHex(characteristic.getValue());
        core.printLog(LOG_E, RX, String.format(Locale.getDefault(), "收到数据: %s", receiveData));

        String uuid = characteristic.getUuid().toString().toUpperCase();
        if (SHB_OTA_NOTIFY_CHARACTERISTIC.equals(uuid)) {
            shbHandler.handleChange(gatt, receiveData);
        } else if (SLB_NOTIFY_CHARACTERISTIC.equals(uuid)) {
            slbHandler.handleChange(gatt, characteristic.getValue());
        }
    }

    /**
     * 停止定时器
     */
    private OTADevice stopTimer(BluetoothGatt gatt) {
        String address = gatt.getDevice().getAddress();
        OTADevice phyDevice = null;
        for (OTADevice device : core.getDeviceList()) {
            if (device.getMacAddress().equals(address)) {
                device.setConnectState(ConnectState.CONNECTED);
                device.setGatt(gatt);
                device.stopTimer();
                phyDevice = device;
                break;
            }
        }
        return phyDevice;
    }

    /**
     * 处理断开后的重连或失败上报（由 onConnectionStateChange 和安全网共用）
     *
     * @return true 表示已发起重连（调用方应 return 跳过后续流程），false 表示已上报失败
     */
    private boolean handleReconnectOrFail(OTADevice device, String address) {
        if (device.getDisconnectTimes() >= 3) {
            core.updateDeviceState(address, OtaState.ERROR_CANNOT_CONNECT, "无法连接设备!", LOG_E);
            core.getOtaCallback().deviceNotify(11031,
                    String.format(Locale.getDefault(), "设备异常: %d", device.getOtaType().code), device);
            return false;
        } else {
            device.incrementAndGetDisconnectTimes();
            if (device.getShbContext() == null) {
                device.setShbContext(new SHBContext(0, 0, 0));
            }
            device.getShbContext().setPartitionIndex(0);
            device.getShbContext().setBlockIndex(0);
            device.getShbContext().setDataIndex(0);
            String reconnectMsg = String.format(
                    core.getContext().getString(R.string.reconnecting_for_the_second_time),
                    device.getDisconnectTimes());
            core.updateDeviceState(address, OtaState.CONNECTING, reconnectMsg, LOG_I);
            core.reconnectDevice(device);
            scheduleReconnectSafetyNet(device, address);
            return true;
        }
    }

    /**
     * 重连超时安全网：若 RECONNECT_TIMEOUT 秒后连接仍未建立（BLE回调丢失），
     * 自动关闭pending gatt并触发下一次重连或失败上报。
     */
    private void scheduleReconnectSafetyNet(OTADevice device, String address) {
        int savedTimes = device.getDisconnectTimes();
        mainHandler.postDelayed(() -> {
            for (OTADevice dev : core.getDeviceList()) {
                if (dev.getMacAddress().equals(address)
                        && dev.getConnectState() != ConnectState.CONNECTED
                        && dev.getOtaType() == OtaState.CONNECTING
                        && savedTimes == dev.getDisconnectTimes()) {
                    // 关闭pending的gatt，防止残留定时器干扰后续流程
                    if (dev.getGatt() != null) {
                        dev.getGatt().close();
                    }
                    connManager.onDisconnected(address);
                    dev.setConnectState(ConnectState.DISCONNECTED);
                    if (dev.isRunTimer()) {
                        dev.stopTimer();
                    }
                    handleReconnectOrFail(dev, address);
                    break;
                }
            }
        }, RECONNECT_TIMEOUT * 1000L);
    }

    /**
     * 处理MTU
     */
    private void handleMtu(int mtu, OTADevice phyDevice) {
        Log.e(TAG, "收到处理MTU请求 handleMtu: " + core.getMtu() + ",新MTU：" + mtu);
        if (core.getMtu() == 0) {
            if (phyDevice.getOtaType() == OtaState.CONFIRMED_SBK_OTA) {
                if (core.getFilePath() == null || core.getShbFile() == null) {
                    core.updateDeviceState(phyDevice.getMacAddress(), OtaState.ERROR_FILE_MISMATCH,
                            BleUtils.getOTATypeInfo(core.getContext(), OtaState.ERROR_FILE_MISMATCH.code), LOG_D);
                    connManager.disconnectGatt(phyDevice.getGatt());
                    return;
                }
                core.printLog(LOG_D, "第一个设备进行文件分割");
                core.setMtu(mtu);
                core.getShbFile().buildFramesWithMTU(mtu);
            } else if (phyDevice.getOtaType() == OtaState.CONFIRMED_SLB) {
                if (core.getFilePath() == null || core.getSlbFile() == null) {
                    core.updateDeviceState(phyDevice.getMacAddress(), OtaState.ERROR_FILE_MISMATCH,
                            BleUtils.getOTATypeInfo(core.getContext(), OtaState.ERROR_FILE_MISMATCH.code), LOG_D);
                    connManager.disconnectGatt(phyDevice.getGatt());
                }
                core.setMtu(mtu);
            }
        } else {
            if (core.getMtu() != mtu) {
                core.updateDeviceState(phyDevice.getMacAddress(), OtaState.ERROR_MTU_MISMATCH,
                        "设备不支持当前MTU大小,MTU不一致", LOG_E);
                connManager.disconnectGatt(phyDevice.getGatt());
            }
        }
    }
}
