package org.tb.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j

@RestControllerAdvice
public class MyExceptionHandler extends ResponseEntityExceptionHandler {

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Object> exception(Exception ex) {
    log.error("caught uncaught exception", ex);
    return new ResponseEntity<>(
        "there was an exception: look at the logs",
        HttpStatus.INTERNAL_SERVER_ERROR);
  }
}
