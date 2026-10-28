package com.resumetailor.storage;

import com.resumetailor.common.ServiceUnavailableException;

/**
 * The configured storage could not be reached or written. A 503 rather than a 500: the
 * fix is almost always on the user's side -- a full disk, a missing bucket, an expired
 * credential -- and the message says which.
 */
public class StorageException extends ServiceUnavailableException {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
