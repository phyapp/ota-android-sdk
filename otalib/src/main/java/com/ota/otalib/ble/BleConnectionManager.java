package com.ota.otalib.ble;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothGatt;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * BLE连接池管理
 */
public class BleConnectionManager {

    public static final int MAX_CONNECTED_NUM = 6;
    private final List<String> connectList = new CopyOnWriteArrayList<>();

    /**
     * 尝试加入连接池，返回true表示可以连接
     */
    public boolean tryConnect(String address) {
        if (connectList.size() < MAX_CONNECTED_NUM) {
            connectList.add(address);
            return true;
        }
        return false;
    }

    /**
     * 设备断连时从连接池移除
     */
    public void onDisconnected(String address) {
        connectList.remove(address);
    }

    public boolean isConnected(String address) {
        return connectList.contains(address);
    }

    public List<String> getConnectList() {
        return connectList;
    }

    public int getConnectedCount() {
        return connectList.size();
    }

    @SuppressLint("MissingPermission")
    public void disconnectGatt(BluetoothGatt gatt) {
        if (gatt != null) {
            gatt.disconnect();
        }
    }

    public void clear() {
        connectList.clear();
    }
}
