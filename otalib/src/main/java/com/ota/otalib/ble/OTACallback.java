package com.ota.otalib.ble;

import com.ota.otalib.model.OTADevice;
import com.ota.otalib.model.OTALog;

public interface OTACallback {
    /**
     * 日志输出
     */
    default void onLogOutput(OTALog log) {}

    void bleAdapterNotify(int code, String message);

    void deviceNotify(int code, String message, OTADevice phyDevice);

}
