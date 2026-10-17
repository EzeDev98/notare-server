package com.notare.common.storage;

import java.time.Instant;

/** One object as a storage backend lists it (step 19b). */
public record StoredObject(String key, Instant lastModified) {
}
