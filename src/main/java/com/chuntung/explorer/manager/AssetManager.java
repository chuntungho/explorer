package com.chuntung.explorer.manager;

import com.chuntung.explorer.config.ExplorerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@Component
public class AssetManager {
    private static final Logger logger = LoggerFactory.getLogger(AssetManager.class);
    private ExplorerProperties explorerProperties;

    public AssetManager(ExplorerProperties explorerProperties) {
        this.explorerProperties = explorerProperties;
    }

    public ResponseEntity<?> getAsset(String path) {
        InputStream in = null;
        // find local file first
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
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        MediaType contentType;
        try {
            String mimeType = Files.probeContentType(Path.of(path));
            contentType = MediaType.valueOf(mimeType);
        } catch (Exception e) {
            logger.warn("Failed to get content type: {}", path);
            contentType = MediaType.APPLICATION_OCTET_STREAM;
        }

        String etag = null;
        CacheControl cacheControl = null; //CacheControl.empty();
        if (!contentType.equals(MediaType.TEXT_HTML)) {
            cacheControl = CacheControl.maxAge(1, TimeUnit.DAYS);
            if (lastModified == null) {
                try {
                    byte[] bytes = in.readAllBytes();
                    etag = DigestUtils.md5DigestAsHex(bytes);
                    in = new ByteArrayInputStream(bytes);
                } catch (IOException e) {
                    logger.warn("Failed to generate MD5 for {}", path);
                    // NOOP
                }
            }
        }

        ResponseEntity.BodyBuilder bodyBuilder = ResponseEntity.ok().contentType(contentType);
        if (cacheControl != null) {
            bodyBuilder.cacheControl(cacheControl);
        }
        if (lastModified != null) {
            bodyBuilder.lastModified(lastModified);
        }
        if (etag != null) {
            bodyBuilder.eTag(etag);
        }
        return bodyBuilder.body(new InputStreamResource(in));
    }
}
