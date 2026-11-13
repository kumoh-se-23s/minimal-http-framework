package com.minihttp.convert;


public class ConversionException extends RuntimeException {
    private final String errorType;

    public ConversionException(String errorType, String message) {
        super(message, null, false, false);
        this.errorType = errorType;
    }

    public String errorType() {
        return this.errorType;
    }
}
