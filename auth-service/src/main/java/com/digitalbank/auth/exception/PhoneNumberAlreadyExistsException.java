package com.digitalbank.auth.exception;

public class PhoneNumberAlreadyExistsException extends RuntimeException {
    public PhoneNumberAlreadyExistsException(String phoneNumber) {
        super("Phone number already exists: " + phoneNumber);
    }
}
