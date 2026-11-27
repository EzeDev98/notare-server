package com.notare.common.storage;

import org.springframework.core.io.Resource;

import java.io.InputStream;

/** Stores and reads files, whatever the backend. */
public interface FileStorageService {

    String store(InputStream content, String contentType, String originalFilename);

    Resource load(String storageKey);

    void delete(String storageKey);

    String copy(String sourceKey, String originalFilename);

    default boolean supportsListing() {
        return false;
    }

    default java.util.List<StoredObject> list(String afterKey, int limit) {
        throw new UnsupportedOperationException("This storage backend cannot list its files.");
    }
}
