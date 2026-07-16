package com.ota.otalib.ble;

import static com.ota.otalib.model.DataType.RX;
import static com.ota.otalib.model.DataType.TX;
import static com.ota.otalib.model.LogType.LOG_D;
import static com.ota.otalib.model.LogType.LOG_E;
import static com.ota.otalib.model.LogType.LOG_I;
import static com.ota.otalib.utils.OTAConstant.*;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.util.Log;

import com.ota.otalib.model.OtaState;
import com.ota.otalib.model.OTADevice;
import com.ota.otalib.model.SLBContext;
import com.ota.otalib.utils.AESTool;
import com.ota.otalib.utils.BleUtils;
import com.ota.otalib.utils.HexString;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * SLB OTA 协议处理
 */
public class SlbOtaHandler {

    private static final String TAG = "SlbOtaHandler";
    private static final int MESSAGE_HEADER_SIZE = 4;
    private final OTACore core;

    public SlbOtaHandler(OTACore core) {
        this.core = core;
    }

    public void startSLBOTA(OTADevice device) {
        if (device.getSlbContext() == null) {
            device.setSlbContext(new SLBContext(1, 0, 0));
        }
        String cmd = generateSlbData(device, REQUEST_DEVICE_FIRMWARE_VERSION, new byte[]{(byte) 0x00}, 1, 0);
        core.updateDeviceState(device.getMacAddress(), device.getOtaType(), "获取设备固件版本", LOG_I);
        sendCmd(device.getGatt(), cmd);
    }

    public void startSecuritySLB(OTADevice device) {
        if (device.getSlbContext() == null) {
            device.setSlbContext(new SLBContext(1, 0, 0));
        }
        core.setRandomStr(BleUtils.getRandomStr());
        byte[] keyStrArr = HexString.parseHexString(Objects.requireNonNull(AESTool.encrypt(core.getRandomStr(), core.getKey())));
        String cmd = generateSlbData(device, SLB_ENC_1, keyStrArr, 1, 0);
        core.updateDeviceState(device.getMacAddress(), device.getOtaType(), "SLB 密钥验证第一步", LOG_I);
        sendCmd(device.getGatt(), cmd);
    }

    public String generateSlbData(OTADevice phyDevice, int opcode, byte[] data, int totleFrame, int currentFrame) {
        int payLoadLength = core.getMtu() - 3 - MESSAGE_HEADER_SIZE;
        int actualSize = Math.min(data.length, payLoadLength);
        byte[] message = new byte[actualSize + MESSAGE_HEADER_SIZE];

        message[0] = (byte) (((0) << 4) | ((phyDevice.getSlbContext().getMessageNumber() & 0x0F)));
        message[1] = (byte) opcode;
        message[2] = (byte) ((((totleFrame - 1) & 0x0F) << 4) | ((currentFrame & 0x0F)));
        message[3] = (byte) actualSize;

        System.arraycopy(data, 0, message, MESSAGE_HEADER_SIZE, actualSize);

        phyDevice.getSlbContext().setMessageNumber(phyDevice.getSlbContext().getMessageNumber() + 1);
        phyDevice.getSlbContext().setMessageNumber(phyDevice.getSlbContext().getMessageNumber() % 16);

        return BleUtils.bytesToHex(message, 0, message.length, false);
    }

    private byte[] getBinsData(int offs, int size) {
        int remaining = core.getSlbFile().getBinData().length - offs;
        return Arrays.copyOfRange(core.getSlbFile().getBinData(), offs, offs + Math.min(remaining, size));
    }

    public void sendCmd(BluetoothGatt gatt, String cmd) {
        String addressKey = gatt.getDevice().getAddress();
        List<String> cmdTempArray = core.getCmdTempMap().get(addressKey);
        if (cmdTempArray == null) cmdTempArray = new ArrayList<>();
        cmdTempArray.add(cmd);
        core.getCmdTempMap().put(addressKey, cmdTempArray);
        core.printLog(LOG_I, TX, cmd);
        if (cmdTempArray.size() > 1) {
            Log.e(TAG, "sendSLBCmd: 等一等再发送！");
            return;
        }
        BleUtils.sendData(core.getContext(), gatt, cmd, METHOD_SLB_OTA, DATA_TYPE_CMD);
    }

    public void sendData(BluetoothGatt gatt, String data) {
        BleUtils.sendData(core.getContext(), gatt, data, METHOD_SLB_OTA, DATA_TYPE_FILE);
    }

    public void handleChange(BluetoothGatt gatt, byte[] data) {
        OTADevice phyDevice = BleUtils.getPhyDevice(core.getDeviceList(), gatt.getDevice().getAddress());
        switch (data[1]) {
            case RESPONSE_DEVICE_FIRMWARE_VERSION:
                sendUpgradeRequestCmd(phyDevice, data);
                break;
            case RESPONSE_TO_UPGRADE_REQUEST:
                handleUpgradeRequest(phyDevice, data);
                break;
            case RESPONSE_RECEIVE_DATA_CHECK:
                handlerNextData(phyDevice, data);
                break;
            case RESPONSE_FIRMWARE_VERIFICATION_RESULTS:
                handleSLBComplete(phyDevice, data);
                break;
            case SLB_ENC_2:
                handleSLBSecurity_One(phyDevice, data);
                break;
            case SLB_ENC_4:
                handleSLBSecurity_Two(phyDevice, data);
                break;
            case SLB_ENC_6:
                handleSLBSecurity_Three(phyDevice, data);
                break;
        }
        phyDevice.getSlbContext().setReceiveData(data);
    }

    private void handleSLBSecurity_Three(OTADevice phyDevice, byte[] bytes) {
        core.printLog(LOG_D, "SLB密钥验证通过，开始升级");
        phyDevice.setSlbContext(null);
        core.updateDeviceState(phyDevice.getMacAddress(), phyDevice.getOtaType(), "SLB密钥验证通过，开始升级", LOG_I);
        startSLBOTA(phyDevice);
    }

    private void handleSLBSecurity_Two(OTADevice phyDevice, byte[] bytes) {
        String data = HexString.parseStringHex(bytes);
        String firmwareRandom = AESTool.decrypt(phyDevice.getFirmwareData(), core.getKey());
        if (data.substring(8).equals(firmwareRandom)) {
            String encString = AESTool.encrypt(firmwareRandom, core.getRandomStr());
            String secondEncStr = AESTool.encrypt(encString, core.getKey());
            String cmd = generateSlbData(phyDevice, SLB_ENC_5, HexString.parseHexString(Objects.requireNonNull(secondEncStr)), 1, 0);
            core.updateDeviceState(phyDevice.getMacAddress(), phyDevice.getOtaType(), "SLB 密钥验证第三步", LOG_I);
            sendCmd(phyDevice.getGatt(), cmd);
        } else {
            core.printLog(LOG_E, "AES加密认证失败");
        }
    }

    private void handleSLBSecurity_One(OTADevice phyDevice, byte[] bytes) {
        String data = HexString.parseStringHex(bytes);
        phyDevice.setFirmwareData(data.substring(8));
        String cmd = generateSlbData(phyDevice, SLB_ENC_3, HexString.parseHexString(core.getRandomStr()), 1, 0);
        core.updateDeviceState(phyDevice.getMacAddress(), phyDevice.getOtaType(), "SLB 密钥验证第二步", LOG_I);
        sendCmd(phyDevice.getGatt(), cmd);
    }

    @SuppressLint("DefaultLocale")
    public void handleWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        String addressKey = gatt.getDevice().getAddress();
        List<String> cmdTempArray = core.getCmdTempMap().get(addressKey);
        if (cmdTempArray != null && !cmdTempArray.isEmpty()) {
            cmdTempArray.remove(0);
            core.getCmdTempMap().put(addressKey, cmdTempArray);
            if (!cmdTempArray.isEmpty()) {
                String nextCmd = cmdTempArray.get(0);
                BleUtils.sendData(core.getContext(), gatt, nextCmd, METHOD_SLB_OTA, DATA_TYPE_CMD);
            }
        }

        OTADevice phyDevice = BleUtils.getPhyDevice(core.getDeviceList(), gatt.getDevice().getAddress());
        String uuid = characteristic.getUuid().toString().toUpperCase();
        byte[] writeSuccessValue = characteristic.getValue();
        byte[] receiveSaveData = phyDevice.getSlbContext().getReceiveData();
        byte opcode = writeSuccessValue[1];

        if (uuid.equals(SLB_WRITE_CHARACTERISTIC)) {
            if (opcode == REQUEST_SENDS_AN_UPGRADE_REQUEST && receiveSaveData[1] == RESPONSE_TO_UPGRADE_REQUEST) {
                handleUpgradeRequest(phyDevice, receiveSaveData);
            }
        } else if (uuid.equals(SLB_WRITE_CHARACTERISTIC_NO_RSP)) {
            if (opcode == SEND_FIRMWARE_PACKET_DATA) {
                int mBinsFrsz = phyDevice.getSlbContext().getPacketSize();
                int dataIndex = phyDevice.getSlbContext().getDataIndex();
                int currentCount = dataIndex % mBinsFrsz;
                int payLoadLength = core.getMtu() - 3 - MESSAGE_HEADER_SIZE;
                int totalFileCount = (int) Math.ceil((double) core.getSlbFile().getBinData().length / payLoadLength);
                if (dataIndex + mBinsFrsz - currentCount > totalFileCount) {
                    mBinsFrsz = totalFileCount - dataIndex + currentCount;
                }
                if (mBinsFrsz == 0) {
                    Log.d(TAG, "handleSLBWrite: 不处理");
                    return;
                }
                if (currentCount % mBinsFrsz == 0) {
                    if (!phyDevice.getSlbContext().isSendOver()) {
                        phyDevice.getSlbContext().setSendOver(true);
                    }
                    return;
                }
                String segmentedData = generateSlbData(phyDevice, SEND_FIRMWARE_PACKET_DATA,
                        getBinsData(payLoadLength * dataIndex, payLoadLength), mBinsFrsz, dataIndex);
                sendData(phyDevice.getGatt(), segmentedData);
                phyDevice.getSlbContext().setDataIndex(phyDevice.getSlbContext().getDataIndex() + 1);
            }
        }
    }

    @SuppressLint("DefaultLocale")
    private void sendUpgradeRequestCmd(OTADevice phyDevice, byte[] data) {
        byte[] PayLoadData = BleUtils.getMessageData(data);
        String PayLoadStr = HexString.parseStringHex(PayLoadData);

        if (data.length == 9) {
            String versionStr = HexString.parseStringHex(new byte[]{data[5], data[6], data[7], data[8]});
            core.updateDeviceState(phyDevice.getMacAddress(), OtaState.FEEDBACK_BOOT_VERSION, "设备固件版本" + versionStr, LOG_I);
        } else if (data.length >= 13) {
            if ((core.getSlbFile() != null && core.getSlbFile().getProductID() != null && !core.getSlbFile().getProductID().isEmpty())
                    || core.getFilePath().toUpperCase().contains("RES_")) {
                String deviceProductID = PayLoadStr.substring(4, 6) + PayLoadStr.substring(2, 4);
                if (!deviceProductID.equals(core.getSlbFile().getProductID())) {
                    core.updateDeviceState(phyDevice.getMacAddress(), OtaState.ERROR_FILE_MISMATCH, "文件与芯片类型不一致，无法升级！", LOG_E);
                    core.getConnManager().disconnectGatt(phyDevice.getGatt());
                    return;
                }
                phyDevice.getSlbContext().setProductID(deviceProductID);
                String deviceBooterVersion = PayLoadStr.substring(6, 12);
                phyDevice.getSlbContext().setBooterVerson(deviceBooterVersion);
                core.updateDeviceState(phyDevice.getMacAddress(), OtaState.FEEDBACK_BOOT_VERSION, deviceProductID + deviceBooterVersion, LOG_I);
            } else {
                core.updateDeviceState(phyDevice.getMacAddress(), OtaState.ERROR_FILE_MISMATCH, "新版本固件必须使用新文件！", LOG_E);
                core.getConnManager().disconnectGatt(phyDevice.getGatt());
                return;
            }
        } else {
            Log.e(TAG, "sendUpgradeRequestCmd: error length！");
            return;
        }

        if (core.getSlbFile().getBinData().length == 0) {
            return;
        }
        int crc16 = BleUtils.calculateCRC16(0xFFFF, core.getSlbFile().getBinData());
        byte[] param;
        if (data.length == 9) {
            param = new byte[12];
        } else {
            param = new byte[16];
            core.setVersion(PayLoadStr.substring(2, 6) + core.getSlbFile().getBooterVerson());
        }

        if (core.getSlbFile().getSlbResConfigAddress() == null) {
            param[0] = (byte) 0;
            byte[] bytes;
            if (core.getVersion().isEmpty()) {
                bytes = BleUtils.intToBytes(0x00);
            } else {
                bytes = HexString.parseHexString(core.getVersion());
            }
            System.arraycopy(bytes, 0, param, 1, bytes.length);
        } else {
            param[0] = (byte) 1;
            byte[] bytes = HexString.parseHexString(core.getSlbFile().getSlbResConfigAddress());
            System.arraycopy(bytes, 0, param, 1, 4);
        }

        if (data.length == 9) {
            System.arraycopy(BleUtils.intToBytes(core.getSlbFile().getBinData().length), 0, param, 5, 4);
            System.arraycopy(BleUtils.intToBytes(crc16), 0, param, 9, 2);
            param[11] = 0;
        } else {
            System.arraycopy(BleUtils.intToBytes(core.getSlbFile().getBinData().length), 0, param, 9, 4);
            System.arraycopy(BleUtils.intToBytes(crc16), 0, param, 13, 2);
            param[15] = 0;
        }

        String slb22Cmd = generateSlbData(phyDevice, REQUEST_SENDS_AN_UPGRADE_REQUEST, param, 1, 0);
        sendCmd(phyDevice.getGatt(), slb22Cmd);
    }

    @SuppressLint("DefaultLocale")
    private void handlerNextData(OTADevice phyDevice, byte[] data) {
        byte[] para = BleUtils.getMessageData(data);
        int totalFrame = (para[0] & 0xF0) >>> 4;
        int currentFrame = para[0] & 0x0F;
        if (totalFrame == currentFrame) {
            currentFrame = 0x0F;
        }
        int confirmLength = BleUtils.bytesToInt(Arrays.copyOfRange(para, 1, 5));
        int payLoadLength = core.getMtu() - 3 - MESSAGE_HEADER_SIZE;
        int dataIndex = phyDevice.getSlbContext().getDataIndex();
        if (currentFrame == 0x0F && (payLoadLength * dataIndex == confirmLength ||
                confirmLength == core.getSlbFile().getBinData().length)) {
        } else {
            dataIndex = confirmLength / payLoadLength;
            phyDevice.getSlbContext().setDataIndex(dataIndex);
        }

        int totalFileCount = (int) Math.ceil((double) core.getSlbFile().getBinData().length / payLoadLength);
        int mBinsFrsz = phyDevice.getSlbContext().getPacketSize();
        if (mBinsFrsz == 0) {
            Log.d(TAG, "未收到0x23");
            return;
        }
        int currentCount = dataIndex % mBinsFrsz;
        if (dataIndex + mBinsFrsz - currentCount > totalFileCount) {
            mBinsFrsz = totalFileCount - dataIndex + currentCount;
        }
        if (confirmLength < core.getSlbFile().getBinData().length) {
            String segmentedData = generateSlbData(phyDevice, SEND_FIRMWARE_PACKET_DATA,
                    getBinsData(payLoadLength * dataIndex, payLoadLength), mBinsFrsz, currentCount % mBinsFrsz);
            sendData(phyDevice.getGatt(), segmentedData);
            phyDevice.getSlbContext().setDataIndex(phyDevice.getSlbContext().getDataIndex() + 1);
        } else {
            String cmd = generateSlbData(phyDevice, NOTIFY_FIRMWARE_SEND_COMPLETE, new byte[]{(byte) 0x01}, 1, 0);
            sendCmd(phyDevice.getGatt(), cmd);
        }
        int progress = confirmLength * 100 / core.getSlbFile().getBinData().length;
        phyDevice.setProcess(progress);
        core.updateDeviceState(phyDevice.getGatt().getDevice().getAddress(), OtaState.FEEDBACK_PROGRESS, String.format("%.2f", (float) progress), LOG_I);
    }

    private void handleSLBComplete(OTADevice phyDevice, byte[] data) {
        byte[] paramData = BleUtils.getMessageData(data);
        if ((1 == paramData[0])) {
            core.updateDeviceState(phyDevice.getMacAddress(), OtaState.UPGRADE_COMPLETE_WAITING_DISCONNECT, "升级完成，等待设备断开连接", LOG_I);
        } else {
            core.updateDeviceState(phyDevice.getMacAddress(), OtaState.ERROR_FIRMWARE_FEEDBACK, "固件校验失败，断开设备重新连接", LOG_E);
            core.getConnManager().disconnectGatt(phyDevice.getGatt());
        }
    }

    @SuppressLint("DefaultLocale")
    private void handleUpgradeRequest(OTADevice phyDevice, byte[] data) {
        String addressKey = phyDevice.getMacAddress();
        List<String> cmdTempArray = core.getCmdTempMap().get(addressKey);
        if (cmdTempArray != null && !cmdTempArray.isEmpty()) {
            return;
        }
        if (phyDevice.isRunTimer()) {
            phyDevice.stopTimer();
        }

        byte[] messageData = BleUtils.getMessageData(data);
        int upgradeFlag = messageData[0];
        int offset = BleUtils.bytesToInt(Arrays.copyOfRange(messageData, 1, 5));
        int packetNum = (messageData[5] & 0x0F) + 1;
        phyDevice.getSlbContext().setPacketSize(packetNum);

        if (upgradeFlag != 1) {
            core.updateDeviceState(phyDevice.getMacAddress(), phyDevice.getOtaType(), "固件端不允许升级", LOG_E);
            return;
        }
        core.updateDeviceState(phyDevice.getMacAddress(), phyDevice.getOtaType(), "开始升级", LOG_I);
        int payLoadLength = core.getMtu() - 3 - MESSAGE_HEADER_SIZE;
        String segmentedData = generateSlbData(phyDevice, SEND_FIRMWARE_PACKET_DATA, getBinsData(offset, payLoadLength), packetNum, 0);
        sendData(phyDevice.getGatt(), segmentedData);
        phyDevice.getSlbContext().setDataIndex(1);
    }
}
