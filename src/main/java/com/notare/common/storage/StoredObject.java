package com.notare.common.storage;

import java.time.Instant;

/** One stored file as a backend lists it. */
public record StoredObject(String key, Instant lastModified) {
}
