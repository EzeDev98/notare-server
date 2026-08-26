package com.notare.common.storage.repository;

import com.notare.common.storage.model.FileBlob;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * storageKey (String) is the primary key, so findById/existsById/deleteById
 * from JpaRepository already give PostgresFileStorageService everything it
 * needs — no custom query methods required.
 */
public interface FileBlobRepository extends JpaRepository<FileBlob, String> {
}