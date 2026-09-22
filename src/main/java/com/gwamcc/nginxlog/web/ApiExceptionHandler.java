package com.gwamcc.nginxlog.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(error(ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> conflict(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error(ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> other(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error(ex.getMessage()));
    }

    private static Map<String, Object> error(String message) {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("ok", false);
        map.put("message", message);
        return map;
    }
}
