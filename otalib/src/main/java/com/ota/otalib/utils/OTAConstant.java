package com.ota.otalib.utils;

public class OTAConstant {

    /**
     * SLB Service
     */
    public static final String SLB_SERVICE_UUID = "0000FEB3-0000-1000-8000-00805F9B34FB";
    public static final String SLB_WRITE_CHARACTERISTIC = "0000FED5-0000-1000-8000-00805F9B34FB";
    public static final String SLB_WRITE_CHARACTERISTIC_NO_RSP = "0000FED7-0000-1000-8000-00805F9B34FB";
    public static final String SLB_NOTIFY_CHARACTERISTIC = "0000FED8-0000-1000-8000-00805F9B34FB";


    /**
     * Single Bank Ota
     */
    public static final String SHB_OTA_SERVICE_UUID = "5833FF01-9B8B-5191-6142-22A4536EF123";
    public static final String SHB_OTA_WRITE_CHARACTERISTIC = "5833FF02-9B8B-5191-6142-22A4536EF123";
    public static final String SHB_OTA_NOTIFY_CHARACTERISTIC = "5833FF03-9B8B-5191-6142-22A4536EF123";
    public static final String SHB_OTA_WRITE_CHARACTERISTIC_NO_RSP = "5833FF04-9B8B-5191-6142-22A4536EF123";

    /**
     * OTADataType:对应写数据时，是否有响应方式
     */
    public static final int DATA_TYPE_CMD = 0x01;
    public static final int DATA_TYPE_FILE = 0x02;

    /**
     * OTAMethodType
     */
    public static final int METHOD_SBK_APP = 0x01;
    public static final int METHOD_SBK_OTA = 0x02;
    public static final int METHOD_SLB_OTA = 0x04;
    public static final int METHOD_SBK_E_FUSE = 0x08;

    /**
     * 描述符 UUID - （SLB和Single Bank通用）
     */
    public static final String DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805F9B34FB";

    /**
     * 获取蓝牙设备固件版本
     */
    public static final int REQUEST_DEVICE_FIRMWARE_VERSION = 0x20;

    /**
     * 返回蓝牙设备固件版本信息
     */
    public static final int RESPONSE_DEVICE_FIRMWARE_VERSION = 0x21;

    /**
     * 发送升级请求及固件信息
     */
    public static final int REQUEST_SENDS_AN_UPGRADE_REQUEST = 0x22;

    /**
     * 设备响应升级请求 1：允许升级 0：不能升级。
     */
    public static final int RESPONSE_TO_UPGRADE_REQUEST = 0x23;

    /**
     * 手机下发固件分包数据
     */
    public static final int SEND_FIRMWARE_PACKET_DATA = 0x2F;

    /**
     * 接收帧号和接收数据长度
     */
    public static final int RESPONSE_RECEIVE_DATA_CHECK = 0x24;

    /**
     * 手机通知固件传输完成并验证
     */
    public static final int NOTIFY_FIRMWARE_SEND_COMPLETE = 0x25;

    /**
     * 设备报告固件验证结果
     */
    public static final int RESPONSE_FIRMWARE_VERIFICATION_RESULTS = 0x26;

    /**
     * SLB 加密OTA 第一步，App 发送给固件
     */
    public static final int SLB_ENC_1 = 0x27;
    /**
     * SLB 加密OTA 第二步，固件 回复给App
     */
    public static final int SLB_ENC_2 = 0x28;
    /**
     * SLB 加密OTA 第三步，App 发送给固件
     */
    public static final int SLB_ENC_3 = 0x29;
    /**
     * SLB 加密OTA 第四步，固件 回复给App
     */
    public static final int SLB_ENC_4 = 0x2A;
    /**
     * SLB 加密OTA 第五步，App 发送给固件
     */
    public static final int SLB_ENC_5 = 0x2B;
    /**
     * SLB 加密OTA 第六步，固件 回复给App
     */
    public static final int SLB_ENC_6 = 0x2C;

}
