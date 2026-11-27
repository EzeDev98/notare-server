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

/** Disk-backed file storage for dev and tests. */
@Service
@Profile("!prod")
public class LocalFileStorageService implements FileStorageService {

    private final Path root;

    public LocalFileStorageService(StorageProperties properties) {
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

    @Override
    public String copy(String sourceKey, String originalFilename) {
        String key = UUID.randomUUID() + extension(originalFilename);
        try {
            Files.copy(resolve(sourceKey), resolve(key), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new StorageException("Failed to copy stored file: " + sourceKey, e);
        }
        return key;
    }
}
