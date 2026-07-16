package com.ota.otalib.model;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

public class SHBFile {
    private int code;
    private final String path;
    private final ArrayList<Partition> list = new ArrayList<>();
    private String productID;
    private String booterVerson;
    private int mtu;

    public SHBFile(String filePath) {
        this.path = filePath;
        analyzeFile(filePath);
    }

    /**
     * analyse file
     *
     * @param path file path
     */
    private void analyzeFile(String path) {

        int size;
        StringBuilder result = new StringBuilder();
        int flag = 0;
        String address = "";

        File file = new File(path);
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(file));
            String readline = "";
            while ((readline = br.readLine()) != null) {
                readline = readline.trim();
                size = Integer.parseInt(readline.substring(1, 3), 16);
                if (readline.startsWith("04", 7)) {
                    if (result.length() > 0) {
                        list.add(new Partition(address, result.toString()));
                    }
                    address = readline.substring(9, 13);
                    flag = 0;
                    result = new StringBuilder();
                    continue;
                }
                if (readline.startsWith("05", 7) || readline.startsWith("01", 7)) {
                    list.add(new Partition(address, result.toString()));
                    break;
                }

                if (flag == 0) {
                    flag = 1;
                    address = address + readline.substring(3, 7);
                }

                result.append(readline.substring(9, 9 + size * 2));
            }
        } catch (FileNotFoundException e) {
            code = 100;
        } catch (IOException e) {
            code = 101;
        }

        code = 200;

        for (int i = 0; i < list.size(); i++) {
            Partition tempPa = list.get(i);
            if (tempPa.getProductID() != null &&!tempPa.getProductID().isEmpty()){
                this.productID = tempPa.getProductID();
                this.booterVerson = tempPa.getBooterVerson();
            }
        }
    }

    public int getCode() {
        return code;
    }

    public void buildFramesWithMTU(int mtuSize) {
        if (mtu == mtuSize){
            return;
        }
        mtu = mtuSize;
        for (int i = 0; i < list.size(); i++) {
            Partition tempPa = list.get(i);
            tempPa.analyzePartition(mtuSize);
        }
    }

    public ArrayList<Partition> getList() {
        return list;
    }

    public long getLength() {
        long size = 0;
        for (Partition partition : list) {
            size += partition.getPartitionLength();
        }

        return size;
    }

    public String getPath() {
        return path;
    }

    public String getBooterVerson() {
        return booterVerson;
    }

    public String getProductID() {
        return productID;
    }
}
