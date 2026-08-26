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

/**
 * Temporary prod voice-note storage: bytes live in Postgres (the same
 * database as everything else) instead of R2. This exists purely to unblock
 * a first production deploy before an R2 account is set up — R2FileStorageService
 * is untouched and ready to take back over the moment it's configured.
 *
 * Selected via notare.storage.backend=postgres (see StorageProperties). Both
 * this class and R2FileStorageService stay under @Profile("prod") — dev/test
 * are unaffected either way, they always get LocalFileStorageService.
 *
 * Same contract as every other FileStorageService implementation:
 *   - the backend generates the key (random UUID + original extension),
 *     never derived from client input
 *   - store() is never @Transactional at the call site above this class —
 *     see FileStorageService's own javadoc on why upload is a saga, not a
 *     transaction. Here specifically that no longer matters much (there's no
 *     separate file I/O step, just one DB write), but the interface contract
 *     is identical across backends on purpose: callers never need to know
 *     which one is active.
 *   - delete() treats a missing key as success.
 *
 * Trade-off worth knowing: voice notes now count against your Postgres
 * storage quota (Neon's free tier included), and every /stream Range request
 * pulls the full row back from Postgres before slicing it in memory — same
 * "read from byte 0, discard the rest" limitation R2FileStorageService's
 * javadoc already documents for R2, just against Postgres instead. Fine at
 * MVP scale; revisit if voice notes get long or numerous before R2 lands.
 */
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
                .orElseThrow(() -> new StorageException(
                        "Stored file is missing or unreadable: " + storageKey));
        return new ByteArrayResource(blob.getContent());
    }

    @Override
    @Transactional
    public void delete(String storageKey) {
        // Mirrors the "missing key = success" contract every other backend
        // follows: JpaRepository.deleteById() throws on a missing row, so we
        // check first instead of catching the exception.
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
}