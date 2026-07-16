package com.ota.otalib.scan;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import com.ota.otalib.utils.VersionUtils;

import java.util.ArrayList;
import java.util.List;


/**
 * 扫描
 */
@SuppressLint("MissingPermission")
public class OTAScan {

    private BluetoothAdapter mBluetoothAdapter;

    private BluetoothLeScanner mScanner;

    private OTAScanCallback phyScanCallback;

    @SuppressLint("StaticFieldLeak")
    private final Context mContext;

    @SuppressLint("StaticFieldLeak")
    private static volatile OTAScan mInstance;

    //初始化
    public static OTAScan getInstance(Context context) {
        if (mInstance == null) {
            synchronized (OTAScan.class) {
                if (mInstance == null) {
                    mInstance = new OTAScan(context);
                }
            }
        }
        return mInstance;
    }

    public OTAScan(Context context) {
        mContext = context;

        if (mContext != null) {
            BluetoothManager mBluetoothManager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager != null) {
                mBluetoothAdapter = mBluetoothManager.getAdapter();
                if (mBluetoothAdapter != null) {
                    mScanner = mBluetoothAdapter.getBluetoothLeScanner();
                }
            }
        }
    }

    public void setPhyScanCallback(OTAScanCallback phyScanCallback) {
        this.phyScanCallback = phyScanCallback;
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (phyScanCallback != null) {
                phyScanCallback.onScanResult(result);
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            if (phyScanCallback != null) {
                phyScanCallback.onBatchScanResults(results);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            String failed;
            switch (errorCode) {
                case SCAN_FAILED_ALREADY_STARTED:
                    failed = "Fails to start scan as BLE scan with the same settings is already started by the app.";
                    break;
                case SCAN_FAILED_APPLICATION_REGISTRATION_FAILED:
                    failed = "Fails to start scan as app cannot be registered.";
                    break;
                case SCAN_FAILED_INTERNAL_ERROR:
                    failed = "Fails to start scan due an internal error";
                    break;
                case SCAN_FAILED_FEATURE_UNSUPPORTED:
                    failed = "Fails to start power optimized scan as this feature is not supported.";
                    break;
                default:
                    failed = "UNKNOWN_ERROR";
            }
            localScanFailed(failed);
        }
    };

    private void localScanFailed(String failed) {
        if (phyScanCallback != null) {
            phyScanCallback.onScanFailed(failed);
        }
    }

    /**
     * Start scanning
     */
    public boolean startScan() {
        if (!isOpenBluetooth()) {
            localScanFailed("Bluetooth is not turned on.");
            return false;
        }
        if (VersionUtils.isAndroid12()) {
            if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
                localScanFailed("Android 12 needs to dynamically request bluetooth scan permission.");
                return false;
            }
        } else {
            if (!hasAccessFineLocation()) {
                localScanFailed("Android 6 to 12 requires dynamic request location permission.");
                return false;
            }
        }

        if (mScanner == null) mScanner = mBluetoothAdapter.getBluetoothLeScanner();
        if (!mBluetoothAdapter.isEnabled()) {
            localScanFailed("Bluetooth not turned on.");
            return false;
        }
        List<ScanFilter> filters = new ArrayList<>();
        ScanSettings.Builder builder = new ScanSettings.Builder();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setLegacy(false);
            mScanner.startScan(filters, builder.build(), scanCallback);
        }else {
            ScanSettings settings = builder.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
            mScanner.startScan(filters, settings, scanCallback);
        }

        return true;
    }

    /**
     * Stop scanning
     */
    public void stopScan() {
        if (mScanner == null) {
            localScanFailed("BluetoothLeScanner is Null.");
            return;
        }
        if (!mBluetoothAdapter.isEnabled()) {
            localScanFailed("Bluetooth not turned on.");
            return;
        }
        mScanner.stopScan(scanCallback);
    }

    /**
     * Is bluetooth turned on?
     *
     * @return true or false
     */
    protected boolean isOpenBluetooth() {
        if (mBluetoothAdapter == null) {
            localScanFailed("BluetoothAdapter is Null.");
            return false;
        }
        return mBluetoothAdapter.isEnabled();
    }

    private boolean hasAccessFineLocation() {
        return hasPermission(Manifest.permission.ACCESS_FINE_LOCATION);
    }

    private boolean hasPermission(String permission) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return mContext.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
        } else {
            return true;
        }
    }
}
