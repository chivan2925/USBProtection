package com.group.usbshield.server.common;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
@RestControllerAdvice
public class ApiExceptionHandler {
    public static Map<String,Object> body(int status, String message) {
        return Map.of("status",status,"message",message,"timestamp",Instant.now().toString());
    }
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<?> status(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(body(e.getStatusCode().value(), e.getReason()==null?"Request rejected":e.getReason()));
    }
    @ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class,
        org.springframework.http.converter.HttpMessageNotReadableException.class,
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
        jakarta.validation.ConstraintViolationException.class})
    ResponseEntity<?> invalid(Exception e) {return ResponseEntity.badRequest().body(body(400,"Invalid request fields"));}
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    ResponseEntity<?> conflict(Exception e) {return ResponseEntity.status(409).body(body(409,"Conflicting data"));}
}
