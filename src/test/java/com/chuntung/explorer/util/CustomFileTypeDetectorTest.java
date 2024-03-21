package com.chuntung.explorer.util;

import com.chuntung.explorer.util.CustomFileTypeDetector;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CustomFileTypeDetectorTest {

    @Test
    void probeContentType() throws IOException {
        CustomFileTypeDetector fileTypeDetector = new CustomFileTypeDetector();
        String contentType = fileTypeDetector.probeContentType(Path.of("abc.ico"));
        assertEquals("image/x-icon", contentType);

        String js = Files.probeContentType(Path.of("abc.js"));
        assertEquals("application/javascript", js);
    }

}