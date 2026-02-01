package com.billbill2.Util;

import java.io.File;

public class FileOperate {
    //计算文件大小
    public static Long getFileSize(String filePath){
        File videoFile = new File(filePath);
        if (videoFile.exists() && videoFile.isFile() && videoFile.length() > 0) {
            return videoFile.length();
        }
        // 兼容flv格式
        String flvPath = filePath.replace(".mp4", ".flv");
        File flvFile = new File(flvPath);
        if (flvFile.exists() && flvFile.isFile() && flvFile.length() > 0) {
            return flvFile.length();
        }
        return 0L;
    }

    //防止不合法文件名
    public static String filterIllegalFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "未知标题";
        }
        // Windows禁止字符：\ / : * ? " < > |
        // Linux禁止字符：/
        String illegalChars = "[\\\\/:*?\"<>|]";
        // 替换为下划线_
        return fileName.replaceAll(illegalChars, "_");
    }
}
