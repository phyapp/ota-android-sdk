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
import android.content.Context;
import android.util.Log;

import com.ota.otalib.model.OtaState;
import com.ota.otalib.model.OTADevice;
import com.ota.otalib.model.Partition;
import com.ota.otalib.model.SHBContext;
import com.ota.otalib.model.SHBFile;
import com.ota.otalib.utils.AESTool;
import com.ota.otalib.utils.BleUtils;
import com.ota.otalib.utils.HexString;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Single Bank OTA 协议处理
 */
public class ShbOtaHandler {

    private static final String TAG = "ShbOtaHandler";
    private final OTACore core;

    public ShbOtaHandler(OTACore core) {
        this.core = core;
    }

    public void startSHBApp(OTADevice device) {
        String cmd = null;
        if (core.getFilePath().endsWith(".res")) {
            cmd = "0103";
        } else if (core.getFilePath().endsWith(".hex") || core.getFilePath().endsWith(".hex4") || core.getFilePath().endsWith(".hex16")) {
            cmd = "0102";
        } else if (core.getFilePath().endsWith(".hexe16")) {
            if (device.getShbContext() == null) {
                device.setShbContext(new SHBContext(0, 0, 0));
            }
            core.setRandomStr(BleUtils.getRandomStr());
            cmd = "05" + AESTool.encrypt(core.getRandomStr(), core.getKey());
            core.updateDeviceState(device.getMacAddress(), device.getOtaType(), "开始密钥校验", LOG_I);
            sendCmd(device.getGatt(), cmd);
            return;
        }
        if (cmd == null) {
            core.updateDeviceState(device.getMacAddress(), OtaState.ERROR_FILE_MISMATCH, BleUtils.getOTATypeInfo(core.getContext(), OtaState.ERROR_FILE_MISMATCH.code), LOG_D);
            core.getConnManager().disconnectGatt(device.getGatt());
            return;
        }
        sendCmd(device.getGatt(), cmd);
    }

    public void startSHBOTA(OTADevice device) {
        device.setTotalSize(core.getShbFile().getLength());
        if (device.getShbContext() == null) {
            device.setShbContext(new SHBContext(0, 0, 0));
        }
        if (core.getFilePath().endsWith(".hexe16")) {
            core.setRandomStr(BleUtils.getRandomStr());
            String cmd = "06" + AESTool.encrypt(core.getRandomStr(), core.getKey());
            sendCmd(device.getGatt(), cmd);
            return;
        }
        if (core.getShbFile().getProductID() != null && !core.getShbFile().getProductID().isEmpty()
                && !device.getShbContext().getFamewareCheck()) {
            device.getShbContext().setFamewareCheck(true);
            String versionInfoCMD = "21" + core.getShbFile().getProductID() + core.getShbFile().getBooterVerson();
            sendCmd(device.getGatt(), versionInfoCMD);
            return;
        }
        sendPartitionCmd(device, core.getShbFile());
    }

    public void sendPartitionInfo(OTADevice phyDevice, SHBFile shbFile, long flashAddress) {
        int partitionIndex = phyDevice.getShbContext().getPartitionIndex();
        Partition partition = shbFile.getList().get(partitionIndex);
        if ((0x11000000 <= Long.parseLong(partition.getAddress(), 16)) && (Long.parseLong(partition.getAddress(), 16) <= 0x1107ffff)) {
            flashAddress = Long.parseLong(partition.getAddress(), 16);
        }
        if (shbFile.getPath().endsWith(".res")) {
            phyDevice.getShbContext().setFlashAddress(0);
            flashAddress = 0;
        }
        int checkSum = BleUtils.getPartitionCheckSum(partition);
        Log.e(TAG, "sendPartitionInfo: "+partitionIndex+","+partition.getAddress()+","+partition.getPartitionLength()+","+checkSum);
        String cmd = BleUtils.makePartitionCmd(partitionIndex, flashAddress, partition.getAddress(), partition.getPartitionLength(), checkSum);
        if (shbFile.getPath().endsWith(".hexe16")) {
            List<List<String>> blocks = partition.getBlocks();
            List<String> lastBlock = blocks.get(blocks.size() - 1);
            String lastData = lastBlock.get(lastBlock.size() - 1);
            if (lastData.length() < 8) {
                lastData = lastBlock.get(lastBlock.size() - 2) + lastData;
            }
            String micCode = lastData.substring(lastData.length() - 8);
            cmd = BleUtils.makePartitionSecurityCmd(partitionIndex, flashAddress, partition.getAddress(), partition.getPartitionLength(), micCode);
        }
        sendCmd(phyDevice.getGatt(), cmd);
    }

    public void sendPartitionCmd(OTADevice phyDevice, SHBFile SHBFile) {
        String cmd = "01" + HexString.int2ByteString(SHBFile.getList().size()) + "00";
        sendCmd(phyDevice.getGatt(), cmd);
        phyDevice.setFinishSize(0);
    }

    public void sendResourceInfo(OTADevice phyDevice, SHBFile SHBFile) {
        String cmd = BleUtils.makeResourceCmd(SHBFile);
        sendCmd(phyDevice.getGatt(), cmd);
    }

    public void sendCmd(BluetoothGatt gatt, String cmd) {
        String addressKey = gatt.getDevice().getAddress();
        List<String> cmdTempArray = core.getCmdTempMap().get(addressKey);
        if (cmdTempArray == null) cmdTempArray = new ArrayList<>();
        cmdTempArray.add(cmd);
        core.getCmdTempMap().put(addressKey, cmdTempArray);
        core.printLog(LOG_I, TX, "Send:"+cmd);
        if (cmdTempArray.size() > 1) {
            core.printLog(LOG_I, "Waiting");
            return;
        } else {
            if (cmd.equals("0102") || cmd.equals("0103")) {
                core.updateDeviceState(addressKey, OtaState.APP_MODE_END_WAITING_SCAN, BleUtils.getOTATypeInfo(core.getContext(), OtaState.APP_MODE_END_WAITING_SCAN.code), LOG_I);
            }
        }
        BleUtils.sendData(core.getContext(), gatt, cmd, METHOD_SBK_APP, DATA_TYPE_CMD);
    }

    public void sendData(BluetoothGatt gatt, String data) {
        BleUtils.sendData(core.getContext(), gatt, data, METHOD_SBK_OTA, DATA_TYPE_FILE);
    }

    @SuppressLint("DefaultLocale")
    public void handleChange(BluetoothGatt gatt, String receiveData) {
        OTADevice phyDevice = BleUtils.getPhyDevice(core.getDeviceList(), gatt.getDevice().getAddress());
        phyDevice.setLastReceiveData(receiveData);
        if (phyDevice.getOtaType() == OtaState.ENABLED_SBK_APP_READY) {
            if (receiveData.length() == 36) {
                core.updateDeviceState(phyDevice.getMacAddress(), OtaState.FEEDBACK_BOOT_VERSION, receiveData.substring(14, 32), LOG_D);
                int rescanByte = Integer.parseInt(receiveData.substring(32, 34), 16);
                if (rescanByte >= 6) rescanByte = 0;
                if (phyDevice.getShbContext() == null) {
                    phyDevice.setShbContext(new SHBContext(0, 0, 0));
                }
                phyDevice.getShbContext().setCheckByte(rescanByte);
                if (core.getShbFile() != null && core.getShbFile().getProductID() != null && !core.getShbFile().getProductID().isEmpty()) {
                    String receivePID = receiveData.substring(16, 18) + receiveData.substring(14, 16);
                    if (!receivePID.equals(core.getShbFile().getProductID())) {
                        core.updateDeviceState(phyDevice.getMacAddress(), OtaState.ERROR_FILE_MISMATCH, "文件与芯片类型不一致，无法升级！", LOG_D);
                        core.getConnManager().disconnectGatt(phyDevice.getGatt());
                        return;
                    }
                    String versionInfoCMD = "04" + receiveData.substring(14, 18) + core.getShbFile().getBooterVerson();
                    sendCmd(gatt, versionInfoCMD);
                } else {
                    startSHBApp(phyDevice);
                }
            } else {
                startSHBApp(phyDevice);
            }
        } else if (("00").equals(receiveData)) {
            startSHBApp(phyDevice);
        } else if (("05FF").equals(receiveData)) {
            core.getConnManager().disconnectGatt(gatt);
        } else if (("0087").equals(receiveData)) {
            phyDevice.getShbContext().setBlockIndex(phyDevice.getShbContext().getBlockIndex() + 1);
            phyDevice.getShbContext().setDataIndex(0);
            int partitionIndex = phyDevice.getShbContext().getPartitionIndex();
            if (phyDevice.getShbContext().getBlockIndex() < core.getShbFile().getList().get(partitionIndex).getBlocks().size()) {
                phyDevice.getShbContext().setDataList(core.getShbFile().getList().get(partitionIndex)
                        .getBlocks().get(phyDevice.getShbContext().getBlockIndex()));
                String data = phyDevice.getShbContext().getDataList().get(phyDevice.getShbContext().getDataIndex());
                sendData(phyDevice.getGatt(), data);
            }
        } else if (("0085").equals(receiveData)) {
            int partitionIndex = phyDevice.getShbContext().getPartitionIndex() + 1;
            phyDevice.getShbContext().setPartitionIndex(partitionIndex);
            phyDevice.getShbContext().setBlockIndex(0);
            if (partitionIndex < core.getShbFile().getList().size()) {
                if (core.getShbFile().getPath().endsWith(".hex16") || core.getShbFile().getPath().endsWith(".hex4")
                        || core.getShbFile().getPath().endsWith(".hex")
                        || core.getShbFile().getPath().endsWith(".hexe") || core.getShbFile().getPath().endsWith("hexe16")) {
                    Partition prePartition = core.getShbFile().getList().get(partitionIndex - 1);
                    if ((0x11000000 > Long.parseLong(prePartition.getAddress(), 16))
                            || (Long.parseLong(prePartition.getAddress(), 16) > 0x1107ffff)) {
                        if (core.getShbFile().getPath().endsWith("hexe16")) {
                            phyDevice.getShbContext().setFlashAddress(phyDevice.getShbContext().getFlashAddress() + prePartition.getPartitionLength() + 4);
                        } else {
                            phyDevice.getShbContext().setFlashAddress(phyDevice.getShbContext().getFlashAddress() + prePartition.getPartitionLength() + 8);
                        }
                    }
                }
                sendPartitionInfo(phyDevice, core.getShbFile(), phyDevice.getShbContext().getFlashAddress());
            }
        } else if (("0083").equals(receiveData)) {
            sendCmd(gatt, "04");
        } else if ("6887".equals(receiveData)) {
            core.printLog(LOG_D, "Error Code: 6887");
            core.getConnManager().disconnectGatt(gatt);
        } else if ("0081".equals(receiveData)) {
            if (core.getFilePath().endsWith(".res")) {
                sendResourceInfo(phyDevice, core.getShbFile());
            } else {
                phyDevice.getShbContext().setPartitionIndex(0);
                phyDevice.getShbContext().setBlockIndex(0);
                phyDevice.getShbContext().setDataIndex(0);
                sendPartitionInfo(phyDevice, core.getShbFile(), 0);
            }
        } else if ("0084".equals(receiveData)) {
            String addressKey = gatt.getDevice().getAddress();
            List<String> cmdTempArray = core.getCmdTempMap().get(addressKey);
            if (cmdTempArray == null || cmdTempArray.isEmpty()) {
                phyDevice.getShbContext().setDataIndex(0);
                int partitionIndex = phyDevice.getShbContext().getPartitionIndex();
                int blockIndex = phyDevice.getShbContext().getBlockIndex();
                phyDevice.getShbContext().setDataList(core.getShbFile().getList().get(partitionIndex).getBlocks().get(blockIndex));
                String data = phyDevice.getShbContext().getDataList().get(phyDevice.getShbContext().getDataIndex());
                sendData(phyDevice.getGatt(), data);
            }
        } else if ("0089".equals(receiveData)) {
            sendPartitionInfo(phyDevice, core.getShbFile(), phyDevice.getShbContext().getFlashAddress());
            phyDevice.getShbContext().setBlockIndex(0);
        } else if ("0091".equals(receiveData) || "FF".equals(receiveData)) {
            startSHBOTA(phyDevice);
        } else if ("0591".equals(receiveData) || "0901".equals(receiveData) || "2B91".equals(receiveData)) {
            core.updateDeviceState(phyDevice.getMacAddress(), OtaState.ERROR_FIRMWARE_FEEDBACK, "Version校验失败", LOG_E);
            core.getConnManager().disconnectGatt(gatt);
        } else if (receiveData.length() == 34 && receiveData.startsWith("71")) {
            phyDevice.setFirmwareData(receiveData.substring(2));
        } else if (receiveData.length() == 34 && (receiveData.startsWith("72") || receiveData.startsWith("73")
                || receiveData.startsWith("8B") || receiveData.startsWith("8C") || receiveData.startsWith("8D"))) {
            core.printLog(LOG_D, "加密OTA中间处理");
        } else if (receiveData.length() == 34 && receiveData.startsWith("71")) {
            phyDevice.setFirmwareData(receiveData.substring(2));
            sendCmd(phyDevice.getGatt(), "06" + core.getRandomStr());
        } else if (receiveData.length() == 34 && receiveData.startsWith("72")) {
            String firmwareStr = AESTool.decrypt(phyDevice.getFirmwareData(), core.getKey());
            if (receiveData.substring(2).equals(firmwareStr)) {
                String encString = AESTool.encrypt(firmwareStr, core.getRandomStr());
                String secondEncStr = AESTool.encrypt(encString, core.getKey());
                sendCmd(phyDevice.getGatt(), "07" + secondEncStr);
            } else {
                core.printLog(LOG_E, "AES加密认证失败");
            }
        } else if (receiveData.length() == 34 && receiveData.startsWith("73")) {
            sendCmd(phyDevice.getGatt(), "0102");
        } else if (receiveData.length() == 34 && receiveData.startsWith("8B")) {
            phyDevice.setFirmwareData(receiveData.substring(2));
            sendCmd(phyDevice.getGatt(), "07" + core.getRandomStr());
        } else if (receiveData.length() == 34 && receiveData.startsWith("8C")) {
            String firmwareStr = AESTool.decrypt(phyDevice.getFirmwareData(), core.getKey());
            if (receiveData.substring(2).equals(firmwareStr)) {
                String encString = AESTool.encrypt(firmwareStr, core.getRandomStr());
                String secondEncStr = AESTool.encrypt(encString, core.getKey());
                sendCmd(phyDevice.getGatt(), "08" + secondEncStr);
            } else {
                core.printLog(LOG_E, "AES加密认证失败");
            }
        } else if (receiveData.length() == 34 && receiveData.startsWith("8D")) {
            sendPartitionCmd(phyDevice, core.getShbFile());
        } else if ("0102".equals(receiveData) || "0103".equals(receiveData)) {
            core.printLog(LOG_D, "收到0102断开连接");
            core.getConnManager().disconnectGatt(gatt);
        } else {
            core.printLog(LOG_E, "error: " + receiveData);
            core.updateDeviceState(phyDevice.getMacAddress(), OtaState.ERROR_FIRMWARE_FEEDBACK, "OTA响应错误", LOG_E);
            core.getConnManager().disconnectGatt(gatt);
        }
    }

    @SuppressLint("DefaultLocale")
    public void handleWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        String addressKey = gatt.getDevice().getAddress();
        OTADevice phyDevice = BleUtils.getPhyDevice(core.getDeviceList(), addressKey);
        List<String> cmdTempArray = core.getCmdTempMap().get(addressKey);
        if (cmdTempArray != null && !cmdTempArray.isEmpty()) {
            cmdTempArray.remove(0);
            core.getCmdTempMap().put(addressKey, cmdTempArray);
            if (!cmdTempArray.isEmpty()) {
                String nextCmd = cmdTempArray.get(0);
                if (nextCmd.equals("0102") || nextCmd.equals("0103")) {
                    core.updateDeviceState(addressKey, OtaState.APP_MODE_END_WAITING_SCAN, BleUtils.getOTATypeInfo(core.getContext(), OtaState.APP_MODE_END_WAITING_SCAN.code), LOG_I);
                }
                BleUtils.sendData(core.getContext(), gatt, nextCmd, METHOD_SBK_APP, DATA_TYPE_CMD);
            }
        }

        String writeSuccessData = HexString.parseStringHex(characteristic.getValue());
        if ("04".equals(writeSuccessData)) {
            core.printLog(LOG_D, "04指令写入成功");
            core.updateDeviceState(addressKey, OtaState.UPGRADE_COMPLETE_WAITING_DISCONNECT, "升级完成，等待设备断开连接", LOG_I);
        }

        if (phyDevice == null || phyDevice.getShbContext() == null) {
            return;
        }
        if (phyDevice.getLastReceiveData().equals("0084")
                && characteristic.getUuid().toString().toUpperCase().equals(SHB_OTA_WRITE_CHARACTERISTIC)) {
            phyDevice.getShbContext().setDataIndex(0);
            phyDevice.getShbContext().setDataList(core.getShbFile().getList()
                    .get(phyDevice.getShbContext().getPartitionIndex()).getBlocks().get(phyDevice.getShbContext().getBlockIndex()));
            String data = phyDevice.getShbContext().getDataList().get(phyDevice.getShbContext().getDataIndex());
            sendData(phyDevice.getGatt(), data);
        }
        if (characteristic.getUuid().toString().toUpperCase().equals(SHB_OTA_WRITE_CHARACTERISTIC_NO_RSP)) {
            phyDevice.setFinishSize(phyDevice.getFinishSize() + characteristic.getValue().length);
            phyDevice.setProcess(phyDevice.getFinishSize() * 100 / phyDevice.getTotalSize());
            core.updateDeviceState(addressKey, OtaState.FEEDBACK_PROGRESS, String.format("%.2f", phyDevice.getProcess()), LOG_I);
            phyDevice.getShbContext().setDataIndex(phyDevice.getShbContext().getDataIndex() + 1);
            if (phyDevice.getShbContext().getDataIndex() < phyDevice.getShbContext().getDataList().size()) {
                String data = phyDevice.getShbContext().getDataList().get(phyDevice.getShbContext().getDataIndex());
                sendData(phyDevice.getGatt(), data);
            }
        }
    }
}
