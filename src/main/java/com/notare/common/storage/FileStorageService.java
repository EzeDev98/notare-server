package com.notare.common.storage;

import org.springframework.core.io.Resource;

import java.io.InputStream;

/**
 * Abstraction over "where do the bytes live". Everything in the app depends on
 * this interface, never on a concrete backend, so we can swap local disk (dev)
 * for S3 (prod) by changing one bean — no caller changes. This is the Strategy
 * pattern: one contract, interchangeable implementations selected at runtime.
 */
public interface FileStorageService {

    /**
     * Persists the given content and returns an opaque storage key used later to
     * load or delete it. The backend generates the key; callers MUST treat it as
     * opaque and never build paths or URLs from it themselves.
     *
     * contentType is passed through for backends that record it (S3 sets it as
     * object metadata); the local backend simply ignores it.
     */
    String store(InputStream content, String contentType, String originalFilename);

    /** Loads previously-stored content as a Spring Resource (for download/stream). */
    Resource load(String storageKey);

    /** Deletes previously-stored content. A missing key is treated as success. */
    void delete(String storageKey);

    /**
     * Duplicates a stored object under a new key, inside the backend (step 17f). Nothing is
     * downloaded or uploaded through the app. The new key is generated here, never taken from input.
     */
    String copy(String sourceKey, String originalFilename);

    /** True when {@link #list} works on this backend (step 19b). Only the R2 backend lists its files. */
    default boolean supportsListing() {
        return false;
    }

    /** Up to {@code limit} objects after {@code afterKey} in key order (null starts at the beginning). */
    default java.util.List<StoredObject> list(String afterKey, int limit) {
        throw new UnsupportedOperationException("This storage backend cannot list its files.");
    }
}