package com.notare.common.storage.repository;

import com.notare.common.storage.model.FileBlob;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for files stored in the database. */
public interface FileBlobRepository extends JpaRepository<FileBlob, String> {
}
