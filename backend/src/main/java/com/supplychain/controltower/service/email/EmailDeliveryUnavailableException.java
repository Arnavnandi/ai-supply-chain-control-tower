package com.supplychain.controltower.service.email;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class EmailDeliveryUnavailableException extends RuntimeException {

    public EmailDeliveryUnavailableException(String message) {
        super(message);
    }

    public EmailDeliveryUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
