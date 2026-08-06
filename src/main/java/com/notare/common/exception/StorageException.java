package com.notare.common.exception;

/**
 * Thrown for storage-backend failures (I/O errors, missing files, an
 * un-initializable directory). Wired to a 500 in the exception handler in 4C —
 * a storage failure is a server-side problem, not the client's fault.
 */
public class StorageException extends RuntimeException {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}