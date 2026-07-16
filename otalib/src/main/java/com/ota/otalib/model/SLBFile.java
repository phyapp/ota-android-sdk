package com.ota.otalib.model;

import android.util.Log;

import com.ota.otalib.utils.HexString;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;

public class SLBFile {

    private final String path;

    private byte[] binData;
    private String slbResConfigAddress;//升级资源时使用
    private String productID;
    private String bootVersion;


    public SLBFile(String filePath) {
        this.path = filePath;
        analyzeFile(filePath);
    }

    private void analyzeFile(String path) {

        File file = new File(path);
        if (!file.exists()) {
            Log.d("SLBFile", "解析文件出错！");
            return;
        }
        //检查是否为升级资源文件
        if (file.getName().contains("res_") || file.getName().contains("RES_")) {
            String resAddress = null;
            String[] strings = file.getName().split("_");
            if (strings.length >= 2) {
                if (strings[1].contains(".")) {
                    String[] split = strings[1].split("\\.");
                    resAddress = split[0];
                } else if (strings[1].length() == 8) {
                    resAddress = strings[1];
                }
            }
            this.slbResConfigAddress = resAddress;
        }

        FileInputStream in = null;
        try {
            in = new FileInputStream(file);
            byte[] buffer = new byte[in.available()];
            in.read(buffer);
            this.binData = buffer;
            getPIDAndVID(binData);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public byte[] getBinData() {
        return binData;
    }

    public String getSlbResConfigAddress() {
        return slbResConfigAddress;
    }

    public String getBooterVerson() {
        return bootVersion;
    }

    public void setProductID(String PID) {
        productID = PID;
    }

    public void setBootVersion(String VID) {
        bootVersion = VID;
    }

    public String getProductID() {
        return productID;
    }

    private void getPIDAndVID(byte[] binData) {
        String randomStr = "3B03F2C00112EC9815F75357228A61330ACA4C23C47477CBA6AB1E2F04CC8269EF96CA95B02ED" +
                "F949FC5297E684CB1F1CEDF50E2EF976EB54AAB6C751DBDE3AA51622C9E838F0F286F34E2073D4519477FE97" +
                "1558AE969BC92E366E70E1692297359E7B7FB8179F845C1D829D538A66647A8130D6D52F559AD99052062B6CFC6";
        Log.e("TAG", "getPIDAndVID: 开始查找字符串" );
        byte[] randomArray = HexString.parseHexString(randomStr);
        int binDataLength = binData.length;
        int randomLength = randomArray.length;
        for (int i = 0; i <= binDataLength - randomLength; i++) {
            for (int j = 0; j < randomLength; j++) {
                if (binData[i + j] != randomArray[j]) {
                    break;
                }
                if (j == randomLength-1){
                    String tempStr = HexString.bytesToHex(Arrays.copyOfRange(binData, i+randomLength, i+randomLength+8)).toUpperCase();
                    this.productID = tempStr.substring(2,4)+tempStr.substring(0,2);
                    this.bootVersion = tempStr.substring(4,16);
                    Log.e("TAG", "getPIDAndVID: 找到字段 "+this.productID +" "+this.bootVersion );
                    return;
                }
            }
            // 找到匹配的字符串
        }
    }


}
