package com.planelyx.api.exception;

/** A request that is well-formed but collides with something already stored. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
