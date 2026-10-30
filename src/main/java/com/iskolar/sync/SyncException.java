package com.iskolar.sync;

/**
 * Any failure talking to the central catalog server (network trouble, bad
 * payload, HTTP error). Carries a user-presentable message.
 */
public class SyncException extends RuntimeException {

    public SyncException(String message) {
        super(message);
    }

    public SyncException(String message, Throwable cause) {
        super(message, cause);
    }

    /** The server rejected our credentials (HTTP 401) — the caller should re-authenticate. */
    public static final class UnauthorizedException extends SyncException {

        public UnauthorizedException(String message) {
            super(message);
        }
    }
}
