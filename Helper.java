package com.shtf.zfr.utils;

import lombok.Data;

import java.io.UnsupportedEncodingException;

/**
 * @author chenlingyu
 */
public class Helper {
    public static String cacheableNameFormat(String name) {
        int signIndex = name.lastIndexOf(":");
        if (signIndex == name.length() - 1)
            return name.substring(0, signIndex - 1);
        else
            return name;
    }

    public static String byte2Str(byte[] buffer) throws UnsupportedEncodingException {
        int length = 0;
        for (int i = 0; i < buffer.length; ++i) {
            if (buffer[i] == 0) {
                length = i;
                break;
            }
        }
        return new String(buffer, 0, length, "UTF-8");
    }
}
