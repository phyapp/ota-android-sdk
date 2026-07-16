package com.ota.otalib.scan;


import android.bluetooth.le.ScanResult;

import java.util.List;

public interface OTAScanCallback {

    void onScanResult(ScanResult result);

    default void onBatchScanResults(List<ScanResult> results){}

    default void onScanFailed(String failed){}
}
