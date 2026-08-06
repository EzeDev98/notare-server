package com.notare.common.storage;

import com.notare.common.exception.StorageException;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Disk-backed storage for dev and test.
 *
 * @Profile("!prod") is the profile-based Strategy selection: this bean is active
 * in every profile EXCEPT prod. The prod backend (S3) will carry @Profile("prod"),
 * so exactly one FileStorageService exists per environment. (Until the S3 bean is
 * added, running the prod profile will have no storage bean — which is fine, since
 * prod also needs real S3 credentials before it can serve uploads.)
 */
@Service
@Profile("!prod")
public class LocalFileStorageService implements FileStorageService {

    private final Path root;

    public LocalFileStorageService(StorageProperties properties) {
        // Resolve the configured location to an absolute, normalized path once,
        // at startup, and ensure the directory exists.
        this.root = Paths.get(properties.getLocal().getLocation())
                .toAbsolutePath()
                .normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new StorageException("Could not initialize storage directory: " + root, e);
        }
    }

    @Override
    public String store(InputStream content, String contentType, String originalFilename) {
        // We generate the key ourselves (random UUID + original extension), so a
        // malicious filename can never dictate where bytes land.
        String key = UUID.randomUUID() + extension(originalFilename);
        Path target = resolve(key);
        try (InputStream in = content) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new StorageException("Failed to store file.", e);
        }
        return key;
    }

    @Override
    public Resource load(String storageKey) {
        Path target = resolve(storageKey);
        try {
            Resource resource = new UrlResource(target.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new StorageException("Stored file is missing or unreadable: " + storageKey);
            }
            return resource;
        } catch (MalformedURLException e) {
            throw new StorageException("Could not read stored file: " + storageKey, e);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException e) {
            throw new StorageException("Failed to delete stored file: " + storageKey, e);
        }
    }

    /**
     * Resolves a key against the storage root and guarantees the result stays
     * INSIDE the root. This is a path-traversal guard: even though we generate
     * keys, we never resolve a path blindly — a key containing "../" would
     * otherwise let a read/delete escape the storage directory.
     */
    private Path resolve(String key) {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw new StorageException("Resolved path escapes storage root.");
        }
        return target;
    }

    private String extension(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : "";
    }
}