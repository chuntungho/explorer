package com.chuntung.explorer.util;

import java.nio.file.Path;

public class CustomFileTypeDetector extends java.nio.file.spi.FileTypeDetector {
    @Override
    public String probeContentType(Path path) {
        String pathString = path.toString();
        int dot = pathString.lastIndexOf('.');
        if (dot > -1) {
            String ext = pathString.substring(dot + 1);
            if (ext.endsWith("ico")) {
                return "image/x-icon";
            } else if (ext.equals("js")) {
                return "application/javascript";
            }
        }
        return null;
    }
}
