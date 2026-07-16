package com.ota.otalib.model;

public class SLBContext {

    private int messageNumber;
    private int packetSize; //允许一次循环可传输的包个数,固定为16
    private int dataIndex;
    private byte[] receiveData;
    private boolean isSendOver;
    private int noResponseNum;//写入数据无响应次数，针对于写入22，未收到23的记录
    private String productID;
    private String booterVerson;

    public int getNoResponseNum() {
        return noResponseNum;
    }

    public void setNoResponseNum(int noResponseNum) {
        this.noResponseNum = noResponseNum;
    }

    public boolean isSendOver() {
        return isSendOver;
    }

    public void setSendOver(boolean sendOver) {
        isSendOver = sendOver;
    }

    public int getMessageNumber() {
        return messageNumber;
    }

    public void setMessageNumber(int messageNumber) {
        this.messageNumber = messageNumber;
    }

    public int getPacketSize() {
        return packetSize;
    }

    public void setPacketSize(int packetSize) {
        this.packetSize = packetSize;
    }

    public int getDataIndex() {
        return dataIndex;
    }

    public void setDataIndex(int dataIndex) {
        this.dataIndex = dataIndex;
    }

    public byte[] getReceiveData() {
        return receiveData;
    }

    public void setReceiveData(byte[] receiveData) {
        this.receiveData = receiveData;
    }

    public void setProductID(String value){
        this.productID = value;
    }

    public String getBooterVerson() {
        return booterVerson;
    }

    public void setBooterVerson(String value){
        this.booterVerson = value;
    }

    public String getProductID() {
        return productID;
    }

    public SLBContext(int messageNumber, int packetSize, int dataIndex) {
        this.messageNumber = messageNumber;
        this.packetSize = packetSize;
        this.dataIndex = dataIndex;
    }
}
