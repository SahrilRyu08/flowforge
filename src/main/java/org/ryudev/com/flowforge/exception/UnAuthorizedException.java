package org.ryudev.com.flowforge.exception;

public class UnAuthorizedException extends RuntimeException {
    public UnAuthorizedException(String notAuthenticated) {
        super(notAuthenticated);
    }
}
