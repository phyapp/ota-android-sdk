package com.ota.otalib.utils;

import static com.ota.otalib.utils.HexString.parseStringHex;
import static com.ota.otalib.utils.OTAConstant.DATA_TYPE_CMD;
import static com.ota.otalib.utils.OTAConstant.DATA_TYPE_FILE;
import static com.ota.otalib.utils.OTAConstant.METHOD_SBK_APP;
import static com.ota.otalib.utils.OTAConstant.METHOD_SBK_OTA;
import static com.ota.otalib.utils.OTAConstant.METHOD_SLB_OTA;
import static com.ota.otalib.utils.OTAConstant.SHB_OTA_NOTIFY_CHARACTERISTIC;
import static com.ota.otalib.utils.OTAConstant.SHB_OTA_SERVICE_UUID;
import static com.ota.otalib.utils.OTAConstant.SHB_OTA_WRITE_CHARACTERISTIC;
import static com.ota.otalib.utils.OTAConstant.SHB_OTA_WRITE_CHARACTERISTIC_NO_RSP;
import static com.ota.otalib.utils.OTAConstant.SLB_NOTIFY_CHARACTERISTIC;
import static com.ota.otalib.utils.OTAConstant.SLB_SERVICE_UUID;
import static com.ota.otalib.utils.OTAConstant.SLB_WRITE_CHARACTERISTIC;
import static com.ota.otalib.utils.OTAConstant.SLB_WRITE_CHARACTERISTIC_NO_RSP;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import com.ota.otalib.R;
import com.ota.otalib.ble.OTACore;
import com.ota.otalib.model.OtaState;
import com.ota.otalib.model.Partition;
import com.ota.otalib.model.OTADevice;
import com.ota.otalib.model.SHBFile;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@SuppressLint("MissingPermission")
public class BleUtils {

    private static final String TAG = OTACore.class.getSimpleName();

    private static final char[] HEX_ARRAY = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    private static final int MESSAGE_HEADER_SIZE = 4;

    public static OTADevice getPhyDevice(List<OTADevice> devices, String address) {
        for (OTADevice device : devices) {
            if (device.getMacAddress().equals(address)) {
                return device;
            }
        }
        Log.e(TAG, "===============: 不可能找不到设备！" );
        return devices.get(0);
    }

    /**
     * 获取OTA类型描述信息
     *
     * @param otaType Ota 状态类型
     */
    public static String getOTATypeInfo(Context context, int otaType) {
        switch (otaType) {
            case 0:
                return "";
            case 11001:
                return context.getString(R.string.connecting);
            case 11002:
                return context.getString(R.string.the_connection_is_successful_discover_service);
            case 11003:
                return context.getString(R.string.device_connection_has_been_disconnected);
            case 11004:
                return "SBK App服务";
            case 11005:
                return "SBK OTA服务";
            case 11006:
                return "SLB OTA服务";
            case 11007:
                return context.getString(R.string.device_is_missing_ota_bluetooth_service);
            case 11008:
                return context.getString(R.string.abnormal_characteristics);
            case 11009:
                return "蓝牙服务状态异常";
            case 11010:
                return context.getString(R.string.feature_confirmed_sbh_app_mode);
            case 11011:
                return context.getString(R.string.features_confirmed_sbh_ota_mode);
            case 11012:
                return context.getString(R.string.feature_confirmed_slb_upgrade_mode);
            case 11013:
                return "特性错误";
            case 11014:
                return "文件和设备类型不匹配!";
            case 11015:
                return context.getString(R.string.modifying_the_mtu_the_connection_is_disconnected_abnormally);
            case 11016:
                return context.getString(R.string.the_device_is_not_supported_the_mtu_size_is_inconsistent);
            case 11017:
                return context.getString(R.string.enable_failed_reconnect);
            case 11018:
                return "描述符UUID错误";
            case 11019:
                return "Enable时状态异常";
            case 11020:
                return context.getString(R.string.feature_enable_successful_sbh_app_is_ready);
            case 11021:
                return context.getString(R.string.feature_enable_successful_sbh_ota_is_ready);
            case 11022:
                return context.getString(R.string.feature_enable_succeeds_the_slb_device_is_ready);
            case 11023:
                return context.getString(R.string.the_app_mode_ends_wait_for_the_second_scan);
            case 11024:
                return "获取MAC地址及版本信息";
            case 11025:
                return "反馈OTA Boot版本信息";
            case 11026:
                return context.getString(R.string.upgrade_complete_wait_for_disconnect);
            case 11027:
                return "反馈升级进度";
            case 11028:
                return context.getString(R.string.unable_to_connect_device);
            case 11029:
                return "找到由App模式切换成功的OTA设备";
            case 11030:
                return "固件端反馈错误";
        }
        return context.getString(R.string.unknown_state);
    }

    public static OtaState getOTATypeForService(List<BluetoothGattService> services) {
        int serviceFlag = 0;
        BluetoothGattService mService = null;
        for (BluetoothGattService service : services) {
            String serviceUUID = service.getUuid().toString().toUpperCase();
            if (SLB_SERVICE_UUID.equals(serviceUUID)) {
                serviceFlag |= 1;
                mService = service;
            } else if (SHB_OTA_SERVICE_UUID.equals(serviceUUID)) {
                serviceFlag |= (1 << 1);
                mService = service;
            }
        }
        OtaState otaType;
        if (serviceFlag == 0) {
            otaType = OtaState.ERROR_NO_SERVICE;
        } else if (serviceFlag == 1 || serviceFlag == 2) {
            otaType = getOTATypeForCharacteristic(mService.getCharacteristics());
        } else {
            otaType = OtaState.ERROR_ABNORMAL_CHARACTERISTIC;
        }
        return otaType;
    }

    private static OtaState getOTATypeForCharacteristic(List<BluetoothGattCharacteristic> characteristics) {
        int SLBFlag = 0, SBHFlag = 0;
        for (BluetoothGattCharacteristic characteristic : characteristics) {
            String characteristicUUID = characteristic.getUuid().toString().toUpperCase();
            switch (characteristicUUID) {
                case SLB_WRITE_CHARACTERISTIC_NO_RSP:
                    SLBFlag |= 1;
                    break;
                case SLB_NOTIFY_CHARACTERISTIC:
                    SLBFlag |= (1 << 1);
                    break;
                case SLB_WRITE_CHARACTERISTIC:
                    SLBFlag |= (1 << 2);
                    break;
                case SHB_OTA_NOTIFY_CHARACTERISTIC:
                    SBHFlag |= 1;
                    break;
                case SHB_OTA_WRITE_CHARACTERISTIC:
                    SBHFlag |= (1 << 1);
                    break;
                case SHB_OTA_WRITE_CHARACTERISTIC_NO_RSP:
                    SBHFlag |= (1 << 2);
                    break;
            }
        }
        OtaState otaType;
        if (SLBFlag == 7 && SBHFlag == 0) {
            otaType = OtaState.CONFIRMED_SLB;
        } else if (SLBFlag == 0 && SBHFlag == 3) {
            otaType = OtaState.CONFIRMED_SBK_APP;
        } else if (SLBFlag == 0 && SBHFlag == 7) {
            otaType = OtaState.CONFIRMED_SBK_OTA;
        } else {
            otaType = OtaState.ERROR_CHARACTERISTIC;
        }
        return otaType;
    }

    /**
     * enable notify
     *
     * @param gatt      gatt
     * @param otaDevice 设备
     * @return true or false
     */
    public static boolean enableNotify( BluetoothGatt gatt, OTADevice otaDevice) {
        OtaState otaType = otaDevice.getOtaType();

        BluetoothGattService service = gatt.getService(UUID.fromString(otaType == OtaState.CONFIRMED_SLB ?
                SLB_SERVICE_UUID : SHB_OTA_SERVICE_UUID));
        if (service == null) {
            Log.e(TAG, "Enable command notification Failed to get OTA service.");
            return false;
        }
        BluetoothGattCharacteristic gattCharacteristic = service.getCharacteristic(UUID.fromString(
                otaType == OtaState.CONFIRMED_SLB ? SLB_NOTIFY_CHARACTERISTIC : SHB_OTA_NOTIFY_CHARACTERISTIC));
        if (gattCharacteristic == null) {
            Log.e(TAG, "Enable command notification Failed to get OTA feature.");
            return false;
        }
        boolean isEnableNotification = gatt.setCharacteristicNotification(gattCharacteristic, true);
        if (!isEnableNotification) {
            Log.e(TAG, "Failed to set feature notification.");
            return false;
        }
        Log.d(TAG, "enable: characteristic uuid : " + gattCharacteristic.getUuid().toString());
        BluetoothGattDescriptor gattDescriptor = gattCharacteristic.getDescriptor(UUID.fromString(OTAConstant.DESCRIPTOR_UUID));
        gattDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        return gatt.writeDescriptor(gattDescriptor);
    }

    public static String getRandomStr() {
        StringBuilder buffer = new StringBuilder();
        int length = 32;
        char[] allChar = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};
        SecureRandom random = new SecureRandom();
        for (int i = 0; i < length; i++) {
            buffer.append(allChar[random.nextInt(allChar.length)]);
        }
        return String.valueOf(buffer);
    }

    public static String makeResourceCmd(SHBFile SHBFile) {
        String startAddress = SHBFile.getList().get(0).getAddress();
        //&0x12000
        long flashLongAdd = Long.parseLong(startAddress, 16) & 0xfffff000L;
        long flashLongSize = Long.parseLong(startAddress, 16) & 0xfff;
        for (Partition partition : SHBFile.getList()) {
            flashLongSize += partition.getPartitionLength();
        }
        flashLongSize = (flashLongSize + 0xfff) & 0xfffff000L;

        String fa = translateStr(strAdd0(Long.toHexString(flashLongAdd), 8));
        String sz = translateStr(strAdd0(Long.toHexString(flashLongSize), 8));

        return "05" + fa + sz;
    }

    public static String strAdd0(String str, int length) {
        int strLength = str.length();
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < length - strLength; i++) {
            result.append("0");
        }

        return result.append(str).toString();
    }

    public static String translateStr(String str) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < str.length() / 2; i++) {
            result.insert(0, str.substring(i * 2, i * 2 + 2));
        }

        return result.toString();
    }


    /**
     * 比较App 与 OTA 下的Mac地址 是否一致
     */
    public static boolean compareMac(String appAddress, String otaAddress, int rescanByte) {
        // 处理rescanByte=0时的特殊情况
        if (rescanByte == 0) {
            // 尝试修改第一个字节
            String firstModified = String.format("%02X", (Integer.parseInt(appAddress.substring(0, 2), 16) + 1) & 0xFF)
                    + appAddress.substring(2);
            if (firstModified.equals(otaAddress)) {
                return true;
            }

            // 尝试修改最后一个字节
            int lastPos = appAddress.length() - 2;
            String lastModified = appAddress.substring(0, lastPos)
                    + String.format("%02X", (Integer.parseInt(appAddress.substring(lastPos), 16) + 1) & 0xFF);
            if (lastModified.equals(otaAddress)) {
                return true;
            }

            // 都不匹配
            return false;
        }

        // 原有的逻辑（rescanByte > 0）
        int bytePosition = 15 - 3 * rescanByte;
        String firstBytes = appAddress.substring(0, bytePosition);
        String midBytes = String.format("%02X", (Integer.parseInt(appAddress.substring(bytePosition, bytePosition + 2), 16) + 1) & 0xFF);
        String lastByte = appAddress.substring(bytePosition + 2);

        return (firstBytes + midBytes + lastByte).equals(otaAddress);
    }

    public static int getPartitionCheckSum(Partition partition) {
        byte[] data = HexString.parseHexString(partition.getData());
        int crc = 0;
        // Store the data that needs to generate a checksum
        byte[] buf = new byte[data.length];
        System.arraycopy(data, 0, buf, 0, data.length);

        for (byte b : buf) {
            if (b < 0) {
                // XOR byte into least sig. byte of
                crc ^= (int) b + 256;
                // crc
            } else {
                // XOR byte into least sig. byte of crc
                crc ^= (int) b;
            }
            // Loop over each bit
            for (int i = 8; i != 0; i--) {
                // If the LSB is set
                if ((crc & 0x0001) != 0) {
                    // Shift right and XOR 0xA001
                    crc >>= 1;
                    crc ^= 0xA001;
                } else {
                    // Else LSB is not set
                    // Just shift right
                    crc >>= 1;
                }
            }
        }

        return crc;
    }

    public static String makePartitionCmd(int index, long flash_addr, String run_addr, int size, int checksum) {
        //Log.e(TAG, "index：" + index + "，flash_addr：" + flash_addr + "，run_addr：" + run_addr + "，size：" + size + "，checksum：" + checksum);
        String fa = translateStr(strAdd0(Long.toHexString(flash_addr), 8));
        String ra = translateStr(strAdd0(run_addr, 8));
        String sz = translateStr(strAdd0(Integer.toHexString(size), 8));
        String cs = translateStr(strAdd0(Integer.toHexString(checksum), 4));
        String in = strAdd0(Integer.toHexString(index), 2);
        String result = "02" + in + fa + ra + sz + cs;
        Log.e(TAG, result);
        return result;
    }

    public static String makePartitionSecurityCmd(int index, long flash_addr, String run_addr, int size, String micCode) {
        String fa = translateStr(strAdd0(Long.toHexString(flash_addr), 8));
        String ra = translateStr(strAdd0(run_addr, 8));
        String sz = translateStr(strAdd0(Integer.toHexString(size), 8));
        String cs = strAdd0(micCode, 8);
        String in = strAdd0(Integer.toHexString(index), 2);

        return "02" + in + fa + ra + sz + cs;
    }

    public static String bytesToHex(final byte[] bytes, final int start, final int length, final boolean add0x) {
        if (bytes == null || bytes.length <= start || length <= 0)
            return "";

        final int maxLength = Math.min(length, bytes.length - start);
        final char[] hexChars = new char[maxLength * 2];
        for (int j = 0; j < maxLength; j++) {
            final int v = bytes[start + j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        if (!add0x)
            return new String(hexChars);
        return "0x" + new String(hexChars);
    }

    public static byte[] getMessageData(byte[] data) {
        return (Arrays.copyOfRange(data, MESSAGE_HEADER_SIZE, MESSAGE_HEADER_SIZE + data[3]));
    }

    public static String getVersionCode(byte[] bytes) {
        String code = null;
        if (bytes.length == 4) {
            int major = Integer.parseInt(parseStringHex(new byte[]{bytes[2]}));
            int minor = Integer.parseInt(parseStringHex(new byte[]{bytes[1]}));
            int amendmentNo = Integer.parseInt(parseStringHex(new byte[]{bytes[0]}));
            code = major + "." + minor + "." + amendmentNo;
        }
        return code;
    }

    public static int versionStringToCode(String codeStr) {
        String result = "0";
        if (codeStr == null) {
            return -1;
        }
        if (codeStr.contains(".")) {
            String[] split = codeStr.split("\\.");
            if (split.length == 3) {
                result = split[0] + split[1] + split[2];
            }
        }
        return Integer.parseInt(result);
    }

    public static int calculateCRC16(int sum, byte[] data) {
        int result = sum;
        int poly = 0x1021;

        for (int itr0 = 0; itr0 < data.length; itr0 += 1) {
            result ^= (data[itr0] << 8);
            for (int itr1 = 0; itr1 < 8; itr1 += 1) {
                if (0 != (result & 0x8000)) {
                    result = ((result << 1) ^ poly);
                } else {
                    result = (result << 1);
                }
            }
        }

        return (result);
    }

    public static byte[] hexToBytes(String data) {
        int parseInt = Integer.parseInt(data, 16);
        return intToBytes(parseInt);
    }

    public static byte[] intToBytes(int i) {
        java.nio.ByteBuffer b = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(i);
        return b.array();
    }

    public static int bytesToInt(byte[] copyOfRange) {
        return copyOfRange.length == 4 ?
                ByteBuffer.wrap(copyOfRange).order(ByteOrder.LITTLE_ENDIAN).getInt() :
                ByteBuffer.wrap(copyOfRange).order(ByteOrder.LITTLE_ENDIAN).getShort();
    }

    public static int checkDescriptorWrite(BluetoothGattDescriptor descriptor, int status){
        if (!OTAConstant.DESCRIPTOR_UUID.equalsIgnoreCase(descriptor.getUuid().toString())) {
            return 11018; //"onDescriptorWrite: onReady Failed"
        }
        if (status != BluetoothGatt.GATT_SUCCESS) {
            Log.e(TAG, "checkDescriptorWrite出错，error code: "+ status);
            return 11019;
        }
        return 0;
    }

    /**
     *
     * @param gatt：gatt对象
     * @param cmd:BLE数据内容
     * @param methodType:OTAMethodType
     * @param dataType :OTADataType
     */
    public static void sendData(Context context, BluetoothGatt gatt, String cmd, int methodType, int dataType){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }
        String serviceStr = SHB_OTA_SERVICE_UUID;
        String characteristicStr = SHB_OTA_WRITE_CHARACTERISTIC;
        int mWriteType = dataType==0x01 ? BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT: BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;

        if ((methodType == METHOD_SBK_APP || methodType == METHOD_SBK_OTA) && dataType == DATA_TYPE_CMD){
            serviceStr = SHB_OTA_SERVICE_UUID;
            characteristicStr = SHB_OTA_WRITE_CHARACTERISTIC;
        }else if (methodType == METHOD_SBK_OTA && dataType == DATA_TYPE_FILE){
            serviceStr = SHB_OTA_SERVICE_UUID;
            characteristicStr = SHB_OTA_WRITE_CHARACTERISTIC_NO_RSP;
        }else if (methodType == METHOD_SLB_OTA && dataType == DATA_TYPE_CMD){
            serviceStr = SLB_SERVICE_UUID;
            characteristicStr = SLB_WRITE_CHARACTERISTIC;
        }else if (methodType == METHOD_SLB_OTA && dataType == DATA_TYPE_FILE){
            serviceStr = SLB_SERVICE_UUID;
            characteristicStr = SLB_WRITE_CHARACTERISTIC_NO_RSP;
        }
        BluetoothGattService service = gatt.getService(UUID.fromString(serviceStr));
        if (service == null) {
            return;
        }
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(characteristicStr));
        characteristic.setWriteType(mWriteType);
        byte[] bytes = HexString.parseHexString(cmd);
        characteristic.setValue(bytes);
        boolean isOK = gatt.writeCharacteristic(characteristic);
        Log.e(TAG, (isOK ? "发送操作成功 " : "发送操作失败 ") + HexString.parseStringHex(characteristic.getValue()));
    }


}
