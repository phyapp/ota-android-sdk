package com.ota.otalib.model;

import java.util.List;

/**
 * Single Bank OTA 发送数据上下文
 */
public class SHBContext {

    private int partitionIndex;
    private int blockIndex;//数据段游标
    private List<String> dataList;//数据列表
    private int dataIndex;//16次计时？数据游标
    private long flashAddress;
    private boolean isFamewareCheck; //是否做过固件端校验
    private int checkByte; // 0表示最后一个字节+1,0x01表示MAC地址第5字节,0x02表示MAC地址第4字节,0x03表示第3个,0x04表示第2个,0x05表示第1个字节+1

    public int getPartitionIndex() {
        return partitionIndex;
    }

    public void setPartitionIndex(int partitionIndex) {
        this.partitionIndex = partitionIndex;
    }

    public int getBlockIndex() {
        return blockIndex;
    }

    public void setBlockIndex(int blockIndex) {
        this.blockIndex = blockIndex;
    }

    public List<String> getDataList() {
        return dataList;
    }

    public void setDataList(List<String> dataList) {
        this.dataList = dataList;
    }

    public int getDataIndex() {
        return dataIndex;
    }

    public void setDataIndex(int dataIndex) {
        this.dataIndex = dataIndex;
    }

    public long getFlashAddress() {
        return flashAddress;
    }

    public void setFlashAddress(long flashAddress) {
        this.flashAddress = flashAddress;
    }

    public void setFamewareCheck(boolean value) {
        this.isFamewareCheck = value;
    }

    public boolean getFamewareCheck() {
        return this.isFamewareCheck;
    }

    public void setCheckByte(int checkByte) {
        this.checkByte = checkByte;
    }

    public int getCheckByte() {
        return checkByte;
    }

    public SHBContext(int partitionIndex, int blockIndex, int flashAddress) {
        this.partitionIndex = partitionIndex;
        this.blockIndex = blockIndex;
        this.flashAddress = flashAddress;
    }
}
