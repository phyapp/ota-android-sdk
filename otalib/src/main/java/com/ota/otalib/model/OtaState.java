package com.ota.otalib.model;

/**
 * OTA 设备状态枚举。
 * 取代散落在各处的 int 常量 1100x，集中管理状态定义和错误判断。
 */
public enum OtaState {

    IDLE                             (0),
    CONNECTING                       (11001),
    CONNECTED_DISCOVERING            (11002),
    DISCONNECTED                     (11003),
    SBK_APP_SERVICE                  (11004),
    SBK_OTA_SERVICE                  (11005),
    SLB_OTA_SERVICE                  (11006),
    ERROR_NO_SERVICE                 (11007),
    ERROR_ABNORMAL_CHARACTERISTIC    (11008),
    ERROR_SERVICE_STATE              (11009),
    CONFIRMED_SBK_APP                (11010),
    CONFIRMED_SBK_OTA                (11011),
    CONFIRMED_SLB                    (11012),
    ERROR_CHARACTERISTIC             (11013),
    ERROR_FILE_MISMATCH              (11014),
    ERROR_MTU_CHANGE                 (11015),
    ERROR_MTU_MISMATCH               (11016),
    ENABLE_FAILED_RECONNECT          (11017),
    ERROR_DESCRIPTOR_UUID            (11018),
    ERROR_ENABLE_STATE               (11019),
    ENABLED_SBK_APP_READY            (11020),
    ENABLED_SBK_OTA_READY            (11021),
    ENABLED_SLB_READY                (11022),
    APP_MODE_END_WAITING_SCAN        (11023),
    GET_MAC_VERSION                  (11024),
    FEEDBACK_BOOT_VERSION            (11025),
    UPGRADE_COMPLETE_WAITING_DISCONNECT (11026),
    FEEDBACK_PROGRESS                (11027),
    ERROR_CANNOT_CONNECT             (11028),
    FOUND_OTA_DEVICE_SWITCHED        (11029),
    ERROR_FIRMWARE_FEEDBACK          (11030);

    /** 与旧版 1100x 整数码对应的值，用于 deviceNotify 等外部接口 */
    public final int code;

    OtaState(int code) {
        this.code = code;
    }

    /** OTA 无法继续的错误状态（连接 / 发现 / 文件等阶段） */
    public boolean isError() {
        return this == ERROR_NO_SERVICE
                || this == ERROR_ABNORMAL_CHARACTERISTIC
                || this == ERROR_SERVICE_STATE
                || this == ERROR_CHARACTERISTIC
                || this == ERROR_FILE_MISMATCH
                || this == ERROR_MTU_MISMATCH
                || this == ERROR_CANNOT_CONNECT;
    }

    /** 终态：错误或升级完成，不再有后续流转 */
    public boolean isTerminal() {
        return isError() || this == UPGRADE_COMPLETE_WAITING_DISCONNECT;
    }

    public static OtaState fromCode(int code) {
        for (OtaState s : values()) {
            if (s.code == code) return s;
        }
        return IDLE;
    }
}
