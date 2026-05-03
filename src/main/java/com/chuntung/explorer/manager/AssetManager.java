package com.chuntung.explorer.manager;

import com.chuntung.explorer.config.ExplorerProperties;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

@Singleton
public class AssetManager {
    private static final Logger logger = LoggerFactory.getLogger(AssetManager.class);
    private final ExplorerProperties explorerProperties;

    public AssetManager(ExplorerProperties explorerProperties) {
        this.explorerProperties = explorerProperties;
    }

    public MutableHttpResponse<byte[]> getAsset(String path) {
        InputStream in = null;
        File localFile = new File(explorerProperties.getAssetsPath() + path);
        Long lastModified = null;

        if (localFile.exists()) {
            try {
                in = new FileInputStream(localFile);
                lastModified = localFile.lastModified();
            } catch (IOException e) {
                // NOOP
            }
        } else {
            in = getClass().getResourceAsStream("/static" + path);
        }

        if (in == null) {
            return HttpResponse.status(HttpStatus.NOT_FOUND);
        }

        String mimeType;
        try {
            mimeType = Files.probeContentType(Path.of(path));
            if (mimeType == null) mimeType = "application/octet-stream";
        } catch (Exception e) {
            logger.warn("Failed to get content type: {}", path);
            mimeType = "application/octet-stream";
        }

        try {
            byte[] bytes = in.readAllBytes();
            MutableHttpResponse<byte[]> response = HttpResponse.ok(bytes);
            response.contentType(mimeType);

            boolean isHtml = mimeType.contains("html");
            if (!isHtml) {
                response.header("Cache-Control", "max-age=" + TimeUnit.DAYS.toSeconds(1));
                if (lastModified != null) {
                    response.header("Last-Modified", String.valueOf(lastModified));
                } else {
                    String etag = md5Hex(bytes);
                    response.header("ETag", "\"" + etag + "\"");
                }
            }
            return response;
        } catch (IOException e) {
            logger.warn("Failed to read asset: {}", path);
            return HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private static String md5Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (Exception e) {
            return "";
        }
    }
}
