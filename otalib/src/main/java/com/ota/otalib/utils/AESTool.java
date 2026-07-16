package com.ota.otalib.utils;

import android.util.Log;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;


public class AESTool {
    private static final String TAG = AESTool.class.getSimpleName();

    /**
     * AES encrypted string
     *
     * @param content  String to be encrypted
     * @param password Password required for encryption
     * @return ciphertext
     */
    public static String encrypt(String content, String password) {

        try {
            // Determine whether the Key is 16 bits
            if (password.length() != 32) {
                return null;
            }
            byte[] raw = parseHexStr2Byte(password);
            SecretKeySpec skeySpec = new SecretKeySpec(raw, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");//"algorithm/model/Complement way"
            cipher.init(Cipher.ENCRYPT_MODE, skeySpec);
            byte[] result = cipher.doFinal(parseHexStr2Byte(content));
            return parseByte2HexStr(result);

        } catch (NoSuchPaddingException | NoSuchAlgorithmException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
            e.toString();
        }
        return null;
    }

    /**
     * Decrypt AES encrypted string
     *
     * @param content  AES encrypted content
     * @param password password when encrypting
     * @return plaintext
     */
    public static byte[] decrypt(byte[] content, String password) {
        try {
            // Determine if the Key is correct
            if (password == null) {
                Log.e(TAG, "decrypt: Key is empty null");
                return null;
            }
            // 判断Key是否为16位
            if (password.length() != 32) {
                Log.e(TAG, "decrypt: Key length is not 16 bits");
                return null;
            }
            byte[] raw = parseHexStr2Byte(password);
            SecretKeySpec skeySpec = new SecretKeySpec(raw, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, skeySpec);

            try {
                return cipher.doFinal(content);
            } catch (Exception e) {
                Log.e(TAG, "decrypt: " + e);
                return null;
            }

        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String decrypt(String content, String password) {
        byte[] decryptFrom = parseHexStr2Byte(content);
        byte[] decrypt = decrypt(decryptFrom, password);
        if (decrypt != null && decrypt.length != 0) {
            return parseByte2HexStr(decrypt);
        }
        return null;
    }

    public static String parseByte2HexStr(byte[] buf) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < buf.length; i++) {
            String hex = Integer.toHexString(buf[i] & 0xFF);
            if (hex.length() == 1) {
                hex = '0' + hex;
            }
            sb.append(hex.toUpperCase());
        }
        return sb.toString();
    }

    public static byte[] parseHexStr2Byte(String hexStr) {
        if (hexStr.length() < 1) {
            return null;
        }
        byte[] result = new byte[hexStr.length() / 2];
        for (int i = 0; i < hexStr.length() / 2; i++) {
            int high = Integer.parseInt(hexStr.substring(i * 2, i * 2 + 1), 16);
            int low = Integer.parseInt(hexStr.substring(i * 2 + 1, i * 2 + 2), 16);
            result[i] = (byte) (high * 16 + low);
        }
        return result;
    }

}
