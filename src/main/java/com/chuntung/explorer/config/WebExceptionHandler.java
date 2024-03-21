package com.chuntung.explorer.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;


@RestControllerAdvice
public class WebExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(WebExceptionHandler.class);

    private ResponseEntityExceptionHandler responseEntityHandler = new ResponseEntityExceptionHandler() {
    };

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> exceptionHandler(HttpServletRequest request, HttpServletResponse response, Exception ex) {
        if (response.isCommitted()) {
            logger.warn("Response already committed: {}", ex.getMessage());
            return null;
        }

        // try to convert to response status exception
        try {
            ResponseEntity<Object> responseEntity = responseEntityHandler.handleException(ex, new ServletWebRequest(request, response));
            if (responseEntity != null && responseEntity.getStatusCode().is4xxClientError()) {
                logger.info("Invalid request {}, cause: {} - {}", request.getRequestURI(), responseEntity.getStatusCode(), ex.getMessage());
                return ResponseEntity.status(responseEntity.getStatusCode()).body(responseEntity.getBody());
            }
        } catch (Exception e) {
            // NOOP
        }

        logger.error("Failed to process request {}", request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
}