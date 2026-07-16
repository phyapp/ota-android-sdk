package com.ota.otalib.scan;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.provider.Settings;
import android.util.Log;

public class OTAReceiver extends BroadcastReceiver {

    public static final String TAG = OTAReceiver.class.getSimpleName();

    private ReceiverCallback callback;

    public void setCallback(ReceiverCallback callback) {
        this.callback = callback;
    }

    private  boolean isGpsEnabled = true;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                    BluetoothAdapter.ERROR);
            callback.BLEAdapterStateChanged(state);
            switch (state) {
                case BluetoothAdapter.STATE_OFF:
                    Log.d(TAG, "Phone bluetooth turned off：STATE_OFF ");
                    break;
                case BluetoothAdapter.STATE_TURNING_OFF:
                    Log.d(TAG, "Phone bluetooth is turning off：STATE_TURNING_OFF ");
                    break;
                case BluetoothAdapter.STATE_ON:
                    Log.d(TAG, "Phone bluetooth turned on：STATE_ON ");
                    break;
                case BluetoothAdapter.STATE_TURNING_ON:
                    Log.d(TAG, "Phone bluetooth is turning on：STATE_TURNING_ON ");
                    break;
            }
        } else if (action.equals(LocationManager.PROVIDERS_CHANGED_ACTION)) {
            Log.d(TAG, "监听到位置消息发生变化！");
            if (context == null) return;
            LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            if (locationManager == null) return;
            boolean isEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            Log.d(TAG, "onReceive: " + (isEnabled ? "打开" : "关闭"));
            if (isGpsEnabled != isEnabled) {
                isGpsEnabled = isEnabled;
                Log.d(TAG, "发送！");
            }else {
                return;
            }
            callback.locationStateChanged(isGpsEnabled);
        }
    }
}
