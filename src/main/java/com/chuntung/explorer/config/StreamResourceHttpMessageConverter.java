package com.chuntung.explorer.config;

import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ResourceHttpMessageConverter;

import java.io.IOException;

public class StreamResourceHttpMessageConverter extends ResourceHttpMessageConverter {
    @Override
    protected Long getContentLength (Resource resource, MediaType contentType) throws IOException {
        // Don't try to determine contentLength on InputStreamResource - cannot be read afterwards...
        if (InputStreamResource.class.isAssignableFrom(resource.getClass())) {
            return null;
        }
        long contentLength = resource.contentLength();
        return (contentLength < 0 ? null : contentLength);
    }
}
