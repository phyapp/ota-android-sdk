package com.ota.otalib.model;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class Partition {
    private final String address;
    private final String data;
    private final int partitionLength;
    private String productID;
    private String booterVerson;

    private final List<List<String>> blocks = new ArrayList<>();

    public Partition(String address, String data) {
        this.address = address;
        this.data = data;
        partitionLength = data.length() / 2;
        //checkSum是通过工具类，在指令发送时计算求得
        checkVIDAndPID();
    }

    public String getAddress() {
        return address;
    }

    public String getData() {
        return data;
    }

    public int getPartitionLength() {
        return partitionLength;
    }

    public List<List<String>> getBlocks() {
        return blocks;
    }

    public String getProductID(){return productID;}

    public String getBooterVerson(){return booterVerson;}

    private void checkVIDAndPID () {
        String randomStr = "3B03F2C00112EC9815F75357228A61330ACA4C23C47477CBA6AB1E2F04CC8269EF96CA95B02ED" +
                "F949FC5297E684CB1F1CEDF50E2EF976EB54AAB6C751DBDE3AA51622C9E838F0F286F34E2073D4519477FE97" +
                "1558AE969BC92E366E70E1692297359E7B7FB8179F845C1D829D538A66647A8130D6D52F559AD99052062B6CFC6";
        if (this.data.toUpperCase().contains(randomStr)){
            int index = this.data.toUpperCase().indexOf(randomStr);
            String remainStr = this.data.substring(index+randomStr.length());
            if (remainStr.length() < 16){
                Log.e("Partition", "checkVIDAndPID:315新版文件VID和PID信息不全" );
            }else {
                this.productID = remainStr.substring(2,4)+remainStr.substring(0,2);
                this.booterVerson = remainStr.substring(4,16);
            }
        }
    }

    public void analyzePartition(int mtu) {
        String partitionStr = this.data;
        int size = mtu - 3;
        List<String> list = new ArrayList<>();
        while (true) {
            if (partitionStr.length() <= size * 2) {
                list.add(partitionStr);
                blocks.add(list);
                break;
            } else {
                String str = partitionStr.substring(0, size * 2);
                partitionStr = partitionStr.substring(size * 2);
                list.add(str);
            }
            if (list.size() == 16) {
                blocks.add(list);
                list = new ArrayList<>();
            }
        }
    }

}
