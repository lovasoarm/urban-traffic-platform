package com.traffic.historyservice.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.net.URI;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(HistoryException.class)
    ProblemDetail handleHistory(HistoryException exception) {
        return problem(exception.status(), exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        String detail = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return problem(HttpStatus.BAD_REQUEST, detail);
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    ProblemDetail handleBadRequest(Exception exception) {
        return problem(HttpStatus.BAD_REQUEST, "The request contains an invalid value");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception) {
        log.error("Unhandled history-service request failure", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected server error occurred");
    }

    private ProblemDetail problem(HttpStatus status, String detail) {
        ProblemDetail result = ProblemDetail.forStatusAndDetail(status, detail);
        result.setType(URI.create("https://urban-traffic.local/problems/" + status.value()));
        result.setTitle(status.getReasonPhrase());
        return result;
    }
}
