package org.visallo.web.auth;

public class AuthTokenException extends Exception {
    public AuthTokenException(String message) {
        super(message);
    }

    public AuthTokenException(Throwable cause) {
        super(cause);
    }
}
