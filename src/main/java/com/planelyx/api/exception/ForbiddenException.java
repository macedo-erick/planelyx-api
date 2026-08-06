package com.planelyx.api.exception;

/**
 * The resource exists and belongs to the caller's account, but it is not theirs to change.
 *
 * Distinct from {@link NotFoundException} on purpose: a user can see their adjustment categories
 * — they are named on the corrections in their own history — so answering a write with 404 would
 * contradict the read that just returned one.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
