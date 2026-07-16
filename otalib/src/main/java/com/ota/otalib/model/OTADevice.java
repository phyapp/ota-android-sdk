package com.ota.otalib.model;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.os.Handler;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 自定义蓝牙设备
 */
public class OTADevice {

    private String realName = "Unknown device"; //蓝牙设备真实名称
    private String macAddress;                  //蓝牙设备Mac地址
    private String originalMacAddress;          //设备原始Mac地址（Single Bank 模式下使用）
    private int rssi;                           //信号强度
    private long lastUpdateDate;                //上一次更新时间
    private boolean isSelect;                   //是否选中
    private boolean isBonded;                   //是否绑定
    private BluetoothDevice device;             //蓝牙设备
    private BluetoothGatt gatt;                 //gatt
    private volatile OtaState otaState = OtaState.IDLE;  //OTA状态
    private String otaMsg;                      //OTA状态描述
    private volatile ConnectState connectState;          //连接状态
    private final AtomicInteger disconnectTimes = new AtomicInteger(0); //断连次数
    private SHBContext shbContext;              //Single Bank 上下文
    private SLBContext slbContext;              //Slb 上下文
    private float totalSize;                    //当前文件总大小
    private float finishSize;                   //当前传输数据大小
    private float process;                      //当前传输进度
    private String firmwareData;                //加密OTA使用，仅在升级文件为.hexe16时使用
    private Handler timeoutHandler;             //超时任务所属 Handler
    private Runnable timeoutTask;               //超时任务
    private boolean runTimer;                   //超时是否在执行
    private String lastReceiveData;             //最后收到的指令

    public OTADevice(ScanResult scanResult) {
        this.device = scanResult.getDevice();
        final ScanRecord scanRecord = scanResult.getScanRecord();
        if (scanRecord != null) {
            if (scanRecord.getDeviceName() != null) {
                if (!scanRecord.getDeviceName().isEmpty()) {
                    this.realName = scanRecord.getDeviceName();
                }
            }
        }
        this.macAddress = device.getAddress();
        this.rssi = scanResult.getRssi();
        this.lastUpdateDate = System.currentTimeMillis();
    }

    @SuppressLint("MissingPermission")
    public OTADevice(BluetoothDevice device, boolean bonded, int rssi) {
        this.realName = (device.getName() == null || device.getName().isEmpty()) ? "Unknown device" : device.getName();
        this.device = device;
        this.macAddress = device.getAddress();
        this.isBonded = bonded;
        this.rssi = rssi;
    }

    public OTADevice(String realName, String macAddress, int rssi, long lastUpdateDate, BluetoothDevice device) {
        this.realName = realName;
        this.macAddress = macAddress;
        this.rssi = rssi;
        this.lastUpdateDate = lastUpdateDate;
        this.device = device;
    }

    public OTADevice(String realName, String macAddress, int rssi, long lastUpdateDate, BluetoothDevice device, boolean isBonded) {
        this(realName,macAddress,rssi,lastUpdateDate,device);
        this.isBonded = isBonded;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public String getMacAddress() {
        return macAddress;
    }

    public void setMacAddress(String macAddress) {
        this.macAddress = macAddress;
    }

    public String getOriginalMacAddress() {
        return originalMacAddress;
    }

    public void setOriginalMacAddress(String originalMacAddress) {
        this.originalMacAddress = originalMacAddress;
    }

    public int getRssi() {
        return rssi;
    }

    public void setRssi(int rssi) {
        this.rssi = rssi;
    }

    public long getLastUpdateDate() {
        return lastUpdateDate;
    }

    public void setLastUpdateDate(long lastUpdateDate) {
        this.lastUpdateDate = lastUpdateDate;
    }

    public boolean isSelect() {
        return isSelect;
    }

    public void setSelect(boolean select) {
        isSelect = select;
    }

    public boolean isBonded() {
        return isBonded;
    }

    public BluetoothDevice getDevice() {
        return device;
    }

    public void setDevice(BluetoothDevice device) {
        this.device = device;
    }

    public BluetoothGatt getGatt() {
        return gatt;
    }

    public void setGatt(BluetoothGatt gatt) {
        this.gatt = gatt;
    }

    public OtaState getOtaType() {
        return otaState;
    }

    public void setOtaType(OtaState otaState) {
        this.otaState = otaState;
    }

    public String getOtaMsg() {
        return otaMsg;
    }

    public void setOtaMsg(String otaMsg) {
        this.otaMsg = otaMsg;
    }

    public ConnectState getConnectState() {
        return connectState;
    }

    public void setConnectState(ConnectState connectState) {
        this.connectState = connectState;
    }

    public int getDisconnectTimes() {
        return disconnectTimes.get();
    }

    public void setDisconnectTimes(int disconnectTimes) {
        this.disconnectTimes.set(disconnectTimes);
    }

    /** 原子自增断连次数，返回自增后的值 */
    public int incrementAndGetDisconnectTimes() {
        return disconnectTimes.incrementAndGet();
    }

    public SHBContext getShbContext() {
        return shbContext;
    }

    public void setShbContext(SHBContext shbContext) {
        this.shbContext = shbContext;
    }

    public SLBContext getSlbContext() {
        return slbContext;
    }

    public void setSlbContext(SLBContext slbContext) {
        this.slbContext = slbContext;
    }

    public float getTotalSize() {
        return totalSize;
    }

    public void setTotalSize(float totalSize) {
        this.totalSize = totalSize;
    }

    public float getFinishSize() {
        return finishSize;
    }

    public void setFinishSize(float finishSize) {
        this.finishSize = finishSize;
    }

    public float getProcess() {
        return process;
    }

    public void setProcess(float process) {
        this.process = process;
    }

    public String getFirmwareData() {
        return firmwareData;
    }

    public void setFirmwareData(String firmwareData) {
        this.firmwareData = firmwareData;
    }

    public Handler getTimeoutHandler() {
        return timeoutHandler;
    }

    public Runnable getTimeoutTask() {
        return timeoutTask;
    }

    public void setTimeoutTask(Handler handler, Runnable task) {
        this.timeoutHandler = handler;
        this.timeoutTask = task;
    }

    public void clearTimeoutTask() {
        this.timeoutHandler = null;
        this.timeoutTask = null;
    }


    public boolean isRunTimer() {
        return runTimer;
    }

    public void setRunTimer(boolean runTimer) {
        this.runTimer = runTimer;
    }

    public void stopTimer(){
        if (isRunTimer()) {
            setRunTimer(false);
            if (timeoutHandler != null && timeoutTask != null) {
                timeoutHandler.removeCallbacks(timeoutTask);
            }
            clearTimeoutTask();
        }
    }

    public void setLastReceiveData(String lastReceiveData) {
        this.lastReceiveData = lastReceiveData;
    }

    public String getLastReceiveData() {
        return lastReceiveData;
    }
}
