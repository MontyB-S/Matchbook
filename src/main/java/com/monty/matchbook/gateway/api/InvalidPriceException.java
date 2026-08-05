package com.monty.matchbook.gateway.api;

public class InvalidPriceException extends RuntimeException {

    public InvalidPriceException(String message, Throwable cause) {
        super(message, cause);
    }
}
