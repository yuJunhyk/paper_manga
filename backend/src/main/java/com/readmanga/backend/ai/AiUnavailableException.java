package com.readmanga.backend.ai;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** 주 서버와 예비 서버가 모두 응답하지 않을 때. API 응답은 503(서비스를 쓸 수 없음)이 된다. */
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class AiUnavailableException extends RuntimeException {

    public AiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
