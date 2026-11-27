package com.notare.common.storage;

import com.notare.common.exception.StorageException;
import com.notare.common.storage.model.FileBlob;
import com.notare.common.storage.repository.FileBlobRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/** File storage in the Postgres database. */
@Service
@Profile("prod")
@ConditionalOnProperty(prefix = "notare.storage", name = "backend", havingValue = "postgres")
public class PostgresFileStorageService implements FileStorageService {

    private final FileBlobRepository repository;

    public PostgresFileStorageService(FileBlobRepository repository) {
        this.repository = repository;
    }

    @Override
    public String store(InputStream content, String contentType, String originalFilename) {
        String key = UUID.randomUUID() + extension(originalFilename);

        byte[] bytes;
        try (InputStream in = content) {
            bytes = in.readAllBytes();
        } catch (IOException e) {
            throw new StorageException("Failed to read upload before storing.", e);
        }

        FileBlob blob = new FileBlob();
        blob.setStorageKey(key);
        blob.setContent(bytes);
        blob.setContentType(contentType);
        blob.setSizeBytes(bytes.length);

        try {
            repository.save(blob);
        } catch (RuntimeException e) {
            throw new StorageException("Failed to store file.", e);
        }

        return key;
    }

    @Override
    public Resource load(String storageKey) {
        FileBlob blob = repository.findById(storageKey)
                .orElseThrow(() -> new StorageException("Stored file is missing or unreadable: " + storageKey));
        return new ByteArrayResource(blob.getContent());
    }

    @Override
    @Transactional
    public void delete(String storageKey) {
        if (repository.existsById(storageKey)) {
            repository.deleteById(storageKey);
        }
    }

    private String extension(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : "";
    }

    @Override
    public String copy(String sourceKey, String originalFilename) {
        FileBlob source = repository.findById(sourceKey)
                .orElseThrow(() -> new StorageException("Stored file is missing or unreadable: " + sourceKey));

        String key = UUID.randomUUID() + extension(originalFilename);
        FileBlob blob = new FileBlob();
        blob.setStorageKey(key);
        blob.setContent(source.getContent());
        blob.setContentType(source.getContentType());
        blob.setSizeBytes(source.getSizeBytes());
        try {
            repository.save(blob);
        } catch (RuntimeException e) {
            throw new StorageException("Failed to copy stored file: " + sourceKey, e);
        }
        return key;
    }
}
