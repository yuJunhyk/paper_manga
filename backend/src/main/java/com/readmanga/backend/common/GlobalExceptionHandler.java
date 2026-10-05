package com.readmanga.backend.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 모든 API의 오류 응답을 한 가지 모양(RFC 9457 Problem Details)으로 통일한다.
 * 서비스가 던진 ResponseStatusException(404, 409 등)은 부모 클래스가 이미 이 모양으로 바꿔 준다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 숫자 자리에 글자: /api/papers/abc → 어느 값이 틀렸는지 한국어로 알려 준다 */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status,
                                                        WebRequest request) {
        String name = ex instanceof MethodArgumentTypeMismatchException mismatch ? mismatch.getName() : ex.getPropertyName();
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "'%s' 값 '%s'의 형식이 올바르지 않습니다.".formatted(name, ex.getValue()));
        return handleExceptionInternal(ex, body, headers, HttpStatus.BAD_REQUEST, request);
    }

    /** 파일 없이 업로드: 전에는 서버 고장(500)으로 보였다. 요청이 잘못된 것이므로 400 */
    @ExceptionHandler(MultipartException.class)
    ProblemDetail handleMultipart(MultipartException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "PDF 파일을 'file'이라는 이름으로 올려 주세요.");
    }

    /** 예상하지 못한 오류: 내부 정보(예외 메시지, DB 주소 등)는 응답에 싣지 않고 서버 로그에만 남긴다 */
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception e, HttpServletRequest request) {
        log.error("처리하지 못한 오류: {} {}", request.getMethod(), request.getRequestURI(), e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다. 잠시 뒤 다시 시도해 주세요.");
    }
}