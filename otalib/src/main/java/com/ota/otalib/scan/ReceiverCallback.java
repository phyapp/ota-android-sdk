package com.ota.otalib.scan;

/**
 * Broadcast callback, notification bar Bluetooth off and positioning off
 */
public interface ReceiverCallback {

    void BLEAdapterStateChanged(int state);

    void locationStateChanged(boolean isGpsEnabled);
}
