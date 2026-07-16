package com.ota.otalib.model;

/**
 * 日志
 */
public class OTALog {

    private LogType logType;
    private DataType dataType;
    private String log;

    public OTALog(LogType logType, DataType dataType, String log) {
        this.logType = logType;
        this.dataType = dataType;
        this.log = log;
    }

    public LogType getLogType() {
        return logType;
    }

    public void setLogType(LogType logType) {
        this.logType = logType;
    }

    public DataType getDataType() {
        return dataType;
    }

    public void setDataType(DataType dataType) {
        this.dataType = dataType;
    }

    public String getLog() {
        return log;
    }

    public void setLog(String log) {
        this.log = log;
    }
}
