package com.ota.otalib.ble;

import static android.bluetooth.BluetoothDevice.PHY_LE_2M_MASK;
import static android.bluetooth.BluetoothDevice.TRANSPORT_LE;
import static com.ota.otalib.model.LogType.LOG_D;
import static com.ota.otalib.model.LogType.LOG_E;
import static com.ota.otalib.model.LogType.LOG_I;
import static com.ota.otalib.utils.OTAConstant.*;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.IntentFilter;
import android.location.LocationManager;
import android.os.Build;
import android.util.Log;

import com.ota.otalib.R;
import com.ota.otalib.model.ConnectState;
import com.ota.otalib.model.DataType;
import com.ota.otalib.model.LogType;
import com.ota.otalib.model.OtaState;
import com.ota.otalib.model.OTADevice;
import com.ota.otalib.model.OTALog;
import com.ota.otalib.model.SHBContext;
import com.ota.otalib.model.SHBFile;
import com.ota.otalib.model.SLBContext;
import com.ota.otalib.model.SLBFile;
import com.ota.otalib.scan.OTAScan;
import com.ota.otalib.scan.OTAScanCallback;
import com.ota.otalib.scan.OTAReceiver;
import com.ota.otalib.scan.ReceiverCallback;
import com.ota.otalib.utils.BleUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * OTA核心类 - 门面
 */
@SuppressLint("MissingPermission")
public class OTACore implements ReceiverCallback {

    public static final String TAG = OTACore.class.getSimpleName();
    @SuppressLint("StaticFieldLeak")
    private static volatile OTACore mInstance;
    private final Context mContext;
    private final OTAScan phyScan;

    // === 共享状态 ===
    private final List<OTADevice> devices = new CopyOnWriteArrayList<>();
    private OTACallback mOtaCallback;
    private static int MTU = 0;
    private String mFilePath;
    private SLBFile mSLBFile;
    private SHBFile mSHBFile;
    private int completeChangeOtaNum = 0;
    private int noOtaNum = 0;
    private String mKey;
    private static String randomStr;
    private String mVersion = "";
    final Map<String, List<String>> cmdTempMap = new ConcurrentHashMap<>();
    private boolean isScanning = false;

    // === 蓝牙扫描 ===
    private final BluetoothLeScanner mScanner;
    private boolean isRescanning;

    // === 拆分后的模块 ===
    final BleConnectionManager connManager;
    final ShbOtaHandler shbHandler;
    final SlbOtaHandler slbHandler;
    private final BluetoothGattCallback mBleGattCallback;

    // ==================== 构造函数 ====================

    public OTACore(Context context) {
        mContext = context;
        BluetoothManager bluetoothManager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
        mScanner = bluetoothManager.getAdapter().getBluetoothLeScanner();
        phyScan = OTAScan.getInstance(context);

        // 初始化拆分模块
        connManager = new BleConnectionManager();
        shbHandler = new ShbOtaHandler(this);
        slbHandler = new SlbOtaHandler(this);
        mBleGattCallback = new BleGattCallback();

        // 广播接收（保持不变）
        OTAReceiver receiver = new OTAReceiver();
        receiver.setCallback(this);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        intentFilter.addAction(LocationManager.PROVIDERS_CHANGED_ACTION);
        context.registerReceiver(receiver, intentFilter);
    }

    public static OTACore getInstance(Context context) {
        if (mInstance == null) {
            synchronized (OTACore.class) {
                if (mInstance == null) {
                    mInstance = new OTACore(context);
                }
            }
        }
        return mInstance;
    }

    // ==================== 公开接口（保持兼容） ====================

    public SLBFile getSLBFile() {
        return mSLBFile;
    }

    public void setOtaCallback(OTACallback mOtaCallback) {
        this.mOtaCallback = mOtaCallback;
    }

    public List<OTADevice> getDevices() {
        return devices;
    }

    public void setDevices(List<OTADevice> deviceList) {
        for (OTADevice device : deviceList) {
            device.setOtaType(OtaState.IDLE);
            device.setConnectState(ConnectState.NOT_CONNECTED);
        }
        devices.clear();
        devices.addAll(deviceList);
    }

    public void selectDevices(List<OTADevice> devices) {
        if (!devices.isEmpty()) {
            setDevices(devices);
            startUpgrade();
        }
    }

    public void setPhyScanCallback(OTAScanCallback phyScanCallback) {
        phyScan.setPhyScanCallback(phyScanCallback);
    }

    public void setKey(String mKey) {
        this.mKey = mKey;
    }

    public boolean isScanning() {
        return isScanning;
    }

    public void cancelUpgrade() {
        for (int i = 0; i < connManager.getConnectedCount(); i++) {
            for (int j = 0; j < devices.size(); j++) {
                if (connManager.getConnectList().get(i).equals(devices.get(j).getMacAddress())) {
                    printLog(LOG_D, mContext.getString(R.string.cancel_the_upgrade_disconnect));
                    connManager.disconnectGatt(devices.get(j).getGatt());
                }
            }
        }
        devices.clear();
        MTU = 0;
        noOtaNum = 0;
        completeChangeOtaNum = 0;
        mKey = null;
        randomStr = null;
        mFilePath = null;
        mSHBFile = null;
        mSLBFile = null;
    }

    public void init() {
        MTU = 0;
        noOtaNum = 0;
        completeChangeOtaNum = 0;
        mKey = null;
        randomStr = null;
        for (int i = devices.size() - 1; i >= 0; i--) {
            OTADevice phyDevice = devices.get(i);
            if (connManager.isConnected(phyDevice.getGatt().getDevice().getAddress())) {
                connManager.disconnectGatt(phyDevice.getGatt());
            }
            devices.remove(i);
        }
    }

    public void connectDevice(OTADevice phyDevice) {
        if (connManager.tryConnect(phyDevice.getMacAddress())) {
            updateDeviceState(phyDevice.getMacAddress(), OtaState.CONNECTING,
                    mContext.getString(R.string.connecting), LOG_I);
            doConnect(phyDevice);
        }
    }

    /**
     * 重连设备（仅连接管理，不触发状态更新，由调用方自行处理消息）
     */
    public void reconnectDevice(OTADevice phyDevice) {
        if (connManager.tryConnect(phyDevice.getMacAddress())) {
            doConnect(phyDevice);
        }
    }

    public void connectNextDevice() {
        int size = 0;
        noOtaNum = 0;
        for (int i = 0; i < devices.size(); i++) {
            OTADevice device = devices.get(i);
            if (device.getOtaType() == OtaState.IDLE) {
                updateDeviceState(device.getMacAddress(), OtaState.CONNECTING,
                        mContext.getString(R.string.connecting), LOG_I);
                connectDevice(device);
                return;
            } else if (device.getOtaType().isError()) {
                noOtaNum++;
            } else if (device.getOtaType() == OtaState.APP_MODE_END_WAITING_SCAN) {
                size++;
            }
        }
        if (!devices.isEmpty() && size == devices.size() - noOtaNum && size > 0) {
            startRescan();
        } else {
            Log.d(TAG, "无需重新扫描");
        }
    }

    public void selectFile(String filePath) {
        if (filePath == null) {
            printLog(LOG_E, "filePath is Null!!!");
            return;
        }
        mFilePath = filePath;
        MTU = 0;
        if (filePath.endsWith(".bin")) {
            mSLBFile = new SLBFile(filePath);
            if (mSLBFile.getBinData() == null || mSLBFile.getBinData().length == 0) {
                mOtaCallback.bleAdapterNotify(10006, "The ota upgrade file is empty.");
            } else if (mOtaCallback != null && mSLBFile.getProductID() != null
                    && !mSLBFile.getProductID().isEmpty()) {
                mOtaCallback.bleAdapterNotify(10005,
                        mSLBFile.getProductID() + mSLBFile.getBooterVerson());
            }
        } else {
            mSHBFile = new SHBFile(filePath);
            if (mSHBFile.getList().isEmpty() && mOtaCallback != null) {
                mOtaCallback.bleAdapterNotify(10006, "The ota upgrade file is empty.");
            } else if (mOtaCallback != null && mSHBFile.getProductID() != null
                    && !mSHBFile.getProductID().isEmpty()) {
                mOtaCallback.bleAdapterNotify(10005,
                        mSHBFile.getProductID() + mSHBFile.getBooterVerson());
            }
        }
    }

    public void startUpgrade() {
        if (devices.isEmpty()) {
            printLog(LOG_E, mContext.getString(R.string.no_upgrade_device));
            return;
        }
        if (mFilePath == null) {
            printLog(LOG_E, mContext.getString(R.string.no_upgrade_file));
            return;
        }
        if ((mFilePath.endsWith(".bin") && (mSLBFile.getBinData() == null
                || mSLBFile.getBinData().length == 0))
                || (!mFilePath.endsWith(".bin") && mSHBFile.getList().isEmpty())) {
            mOtaCallback.bleAdapterNotify(10006, "The ota upgrade file is empty.");
            printLog(LOG_E, "The ota upgrade file is empty.");
            return;
        }

        for (OTADevice device : devices) {
            if (device.getOtaType() == OtaState.ENABLED_SLB_READY && mFilePath.endsWith("hexe16.bin")) {
                slbHandler.startSecuritySLB(device);
            } else if (device.getOtaType() == OtaState.ENABLED_SLB_READY && mFilePath.endsWith(".bin")) {
                if (device.getSlbContext() == null) {
                    device.setSlbContext(new SLBContext(1, 0, 0));
                }
                slbHandler.startSLBOTA(device);
            } else if (device.getOtaType() == OtaState.ENABLED_SBK_APP_READY
                    && (mFilePath.endsWith(".hex") || mFilePath.endsWith(".hex4")
                    || mFilePath.endsWith(".hex16") || mFilePath.endsWith(".res")
                    || mFilePath.endsWith(".hexe16"))) {
                shbHandler.startSHBApp(device);
            } else if (device.getOtaType() == OtaState.ENABLED_SBK_OTA_READY && (mFilePath.endsWith(".hex")
                    || mFilePath.endsWith(".hex4") || mFilePath.endsWith(".hex16")
                    || mFilePath.endsWith(".res") || mFilePath.endsWith(".hexe16"))) {
                shbHandler.startSHBOTA(device);
            } else if (connManager.getConnectedCount() == BleConnectionManager.MAX_CONNECTED_NUM) {
                printLog(LOG_D, mContext.getString(R.string.the_maximum_number_of_connections_is_reached));
                break;
            } else if ((device.getConnectState() == ConnectState.NOT_CONNECTED
                    || device.getConnectState() == ConnectState.DISCONNECTED)
                    && connManager.getConnectedCount() < BleConnectionManager.MAX_CONNECTED_NUM) {
                connectDevice(device);
                if (connManager.getConnectedCount() == BleConnectionManager.MAX_CONNECTED_NUM) break;
            }
        }
    }

    public void startScan() {
        if (!isScanning) {
            isScanning = phyScan.startScan();
        } else {
            Log.d(TAG, "已经在扫描中~");
        }
    }

    public void stopScan() {
        if (isScanning) {
            isScanning = false;
            phyScan.stopScan();
        }
    }

    public void updateDeviceState(String address, OtaState state, String msg, LogType logType) {
        printLog(logType, "address: " + address + "，otaCode：" + state.code + "，msg：" + msg);
        if (state == OtaState.CONNECTING) {
            printLog(LOG_I, String.format(Locale.getDefault(),
                    mContext.getString(R.string.number_of_connected_devices),
                    connManager.getConnectedCount()));
        }
        if (devices.isEmpty()) return;
        for (int i = 0; i < devices.size(); i++) {
            if (devices.get(i).getMacAddress().equals(address)) {
                devices.get(i).setOtaType(state);
                devices.get(i).setOtaMsg(msg);
                if (mOtaCallback != null) {
                    mOtaCallback.deviceNotify(state.code, msg, devices.get(i));
                }
                break;
            }
        }
    }

    public void setSlbVersion(String version) {
        mVersion = version;
    }

    // ==================== ReceiverCallback ====================

    @Override
    public void BLEAdapterStateChanged(int state) {
        if (state == BluetoothAdapter.STATE_OFF) {
            if (mOtaCallback != null) {
                mOtaCallback.bleAdapterNotify(10001, "手机蓝牙已关闭！");
            }
            isScanning = false;
        } else if (state == BluetoothAdapter.STATE_ON) {
            if (mOtaCallback != null) {
                mOtaCallback.bleAdapterNotify(10002, "手机蓝牙已打开！");
            }
        }
    }

    @Override
    public void locationStateChanged(boolean isGpsEnabled) {
        if (!isGpsEnabled) {
            if (mOtaCallback != null) {
                mOtaCallback.bleAdapterNotify(10003, "手机定位已关闭！");
            }
            isScanning = false;
        } else {
            if (mOtaCallback != null) {
                mOtaCallback.bleAdapterNotify(10004, "手机定位已打开！");
            }
        }
    }

    // ==================== 二次扫描 ====================

    void startRescan() {
        printLog(LOG_D, mContext.getString(R.string.secondary_scan));
        if (!isRescanning) {
            isRescanning = true;
            List<ScanFilter> filters = new ArrayList<>();
            ScanSettings.Builder builder = new ScanSettings.Builder();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setLegacy(false);
                mScanner.startScan(filters, builder.build(), scanCallback);
            } else {
                ScanSettings settings = builder.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
                mScanner.startScan(filters, settings, scanCallback);
            }
        }
    }

    private void stopRescan() {
        if (isRescanning) {
            isRescanning = false;
            mScanner.stopScan(scanCallback);
        }
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (result.getScanRecord() == null) return;
            String deviceName = result.getScanRecord().getDeviceName();
            if (deviceName == null || deviceName.isEmpty()) return;

            String scanAddress = result.getDevice().getAddress();
            printLog(LOG_D, String.format(Locale.getDefault(),
                    mContext.getString(R.string.the_device_address_can_be_scanned_twice), scanAddress));
            for (OTADevice phyDevice : devices) {
                if (phyDevice.getShbContext() == null) {
                    phyDevice.setShbContext(new SHBContext(0, 0, 0));
                }
                if (BleUtils.compareMac(phyDevice.getMacAddress(), scanAddress,
                        phyDevice.getShbContext().getCheckByte())
                        && phyDevice.getOtaType() == OtaState.APP_MODE_END_WAITING_SCAN) {
                    phyDevice.setDevice(result.getDevice());
                    phyDevice.setRealName(deviceName);
                    phyDevice.setOriginalMacAddress(phyDevice.getMacAddress());
                    phyDevice.setMacAddress(scanAddress);
                    phyDevice.getShbContext().setFamewareCheck(true);
                    phyDevice.getShbContext().setPartitionIndex(0);
                    phyDevice.getShbContext().setBlockIndex(0);
                    phyDevice.getShbContext().setDataIndex(0);
                    phyDevice.setOtaType(OtaState.FOUND_OTA_DEVICE_SWITCHED);
                    completeChangeOtaNum++;
                    printLog(LOG_D, mContext.getString(R.string.the_device_address_was_scanned_and_updated)
                            + ":" + phyDevice.getMacAddress() + "，Num：" + completeChangeOtaNum);
                    break;
                }
            }
            if (completeChangeOtaNum == devices.size() - noOtaNum) {
                printLog(LOG_D, mContext.getString(R.string.complete_ota_mode_switchover_ota_starts));
                completeChangeOtaNum = 0;
                stopRescan();
                startUpgrade();
            }
        }
    };

    // ==================== enable notify 严格串行（协议共用） ====================

    void enableNotifySerial(BluetoothGatt gatt, OTADevice phyDevice) {
        OtaState otaType = phyDevice.getOtaType();
        boolean isSlb = otaType == OtaState.CONFIRMED_SLB;
        BluetoothGattService service = gatt.getService(UUID.fromString(isSlb ?
                SLB_SERVICE_UUID : SHB_OTA_SERVICE_UUID));
        if (service == null) {
            updateDeviceState(phyDevice.getMacAddress(), OtaState.ENABLE_FAILED_RECONNECT,
                    mContext.getString(R.string.enable_failed_reconnect), LOG_E);
            connManager.disconnectGatt(gatt);
            return;
        }

        BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(
                isSlb ? SLB_NOTIFY_CHARACTERISTIC : SHB_OTA_NOTIFY_CHARACTERISTIC));
        if (characteristic == null) {
            updateDeviceState(phyDevice.getMacAddress(), OtaState.ENABLE_FAILED_RECONNECT,
                    mContext.getString(R.string.enable_failed_reconnect), LOG_E);
            connManager.disconnectGatt(gatt);
            return;
        }

        if (!gatt.setCharacteristicNotification(characteristic, true)) {
            updateDeviceState(phyDevice.getMacAddress(), OtaState.ENABLE_FAILED_RECONNECT,
                    mContext.getString(R.string.enable_failed_reconnect), LOG_E);
            connManager.disconnectGatt(gatt);
            return;
        }

        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString(DESCRIPTOR_UUID));
        if (descriptor == null) {
            updateDeviceState(phyDevice.getMacAddress(), OtaState.ENABLE_FAILED_RECONNECT,
                    mContext.getString(R.string.enable_failed_reconnect), LOG_E);
            connManager.disconnectGatt(gatt);
            return;
        }

        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        if (!gatt.writeDescriptor(descriptor)) {
            updateDeviceState(phyDevice.getMacAddress(), OtaState.ENABLE_FAILED_RECONNECT,
                    mContext.getString(R.string.enable_failed_reconnect), LOG_E);
            connManager.disconnectGatt(gatt);
        }
    }

    // ==================== 包级私有访问器（供 Handler 使用） ====================

    Context getContext() { return mContext; }
    List<OTADevice> getDeviceList() { return devices; }
    OTACallback getOtaCallback() { return mOtaCallback; }
    Map<String, List<String>> getCmdTempMap() { return cmdTempMap; }
    BleConnectionManager getConnManager() { return connManager; }
    BluetoothGattCallback getBleGattCallback() { return mBleGattCallback; }
    String getFilePath() { return mFilePath; }
    SLBFile getSlbFile() { return mSLBFile; }
    SHBFile getShbFile() { return mSHBFile; }
    String getKey() { return mKey; }
    String getVersion() { return mVersion; }
    void setVersion(String v) { mVersion = v; }
    int getMtu() { return MTU; }
    void setMtu(int mtu) { MTU = mtu; }

    static String getRandomStr() { return randomStr; }
    static void setRandomStr(String str) { randomStr = str; }

    void printLog(LogType logType, String log) {
        printLog(logType, DataType.OTHER, log);
    }

    void printLog(LogType logType, DataType dataType, String log) {
        switch (logType) {
            case LOG_I: Log.i(TAG, log); break;
            case LOG_W: Log.w(TAG, log); break;
            case LOG_D: Log.d(TAG, log); break;
            case LOG_E: Log.e(TAG, log); break;
        }
        if (mOtaCallback != null) {
            mOtaCallback.onLogOutput(new OTALog(logType, dataType, log));
        }
    }

    // ==================== 内部连接 ====================

    private void doConnect(OTADevice phyDevice) {
        BluetoothGatt gatt;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            gatt = phyDevice.getDevice().connectGatt(mContext, false,
                    mBleGattCallback, TRANSPORT_LE, PHY_LE_2M_MASK);
        } else {
            gatt = phyDevice.getDevice().connectGatt(mContext, false, mBleGattCallback);
        }
        ((BleGattCallback) mBleGattCallback).addTimeoutListener(phyDevice, gatt);
    }

    // ==================== 向后兼容：BleGattCallback 内部类 ====================

    public class BleGattCallback extends GattCallbackHandler {
        BleGattCallback() {
            super(OTACore.this, shbHandler, slbHandler, connManager);
        }
    }
}
