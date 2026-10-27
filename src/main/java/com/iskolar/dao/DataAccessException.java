package com.iskolar.dao;

/**
 * Unchecked wrapper for SQL failures inside DAOs, so service and controller
 * layers never have to deal with checked {@link java.sql.SQLException}.
 */
public class DataAccessException extends RuntimeException {

    public DataAccessException(String message) {
        super(message);
    }

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
