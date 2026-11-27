package com.notare.common.storage;

import com.notare.common.exception.StorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tests for LocalFileStorageService. */
class LocalFileStorageServiceTest {

    @TempDir
    Path tempDir;

    private LocalFileStorageService serviceFor(Path root) {
        StorageProperties properties = new StorageProperties();
        properties.getLocal().setLocation(root.toString());
        return new LocalFileStorageService(properties);
    }

    private LocalFileStorageService service;

    @BeforeEach
    void setUp() {
        service = serviceFor(tempDir);
    }

    @Test
    void constructor_createsTheConfiguredDirectoryIfItDoesNotYetExist() {
        Path notYetCreated = tempDir.resolve("nested/does/not/exist/yet");
        assertThat(Files.exists(notYetCreated)).isFalse();

        serviceFor(notYetCreated);

        assertThat(Files.isDirectory(notYetCreated)).isTrue();
    }

    @Test
    void constructor_throwsStorageException_whenTheLocationCannotBeCreated() throws IOException {
        Path blocker = tempDir.resolve("blocker");
        Files.createFile(blocker);
        Path uncreatable = blocker.resolve("subdir");

        assertThatThrownBy(() -> serviceFor(uncreatable))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Could not initialize storage directory");
    }

    @Test
    void store_thenLoad_roundTripsTheOriginalBytes() throws IOException {
        byte[] original = "hello voice note".getBytes(StandardCharsets.UTF_8);
        String key = service.store(new ByteArrayInputStream(original), "audio/mpeg", "memo.mp3");

        Resource loaded = service.load(key);

        assertThat(loaded.exists()).isTrue();
        assertThat(loaded.getInputStream().readAllBytes()).isEqualTo(original);
    }

    @Test
    void store_generatesAUuidKeyWithTheOriginalFilenamesExtension() {
        String key = service.store(new ByteArrayInputStream(new byte[]{1, 2, 3}), "audio/mpeg", "memo.mp3");

        assertThat(key).endsWith(".mp3");
        String uuidPart = key.substring(0, key.length() - ".mp3".length());
        assertThat(uuidPart).matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    }

    @Test
    void store_originalFilenameHasNoExtension_keyHasNoExtensionEither() {
        String key = service.store(new ByteArrayInputStream(new byte[]{1, 2, 3}), "audio/mpeg", "recording");

        assertThat(key).doesNotContain(".");
    }

    @Test
    void store_nullOriginalFilename_keyHasNoExtension() {
        String key = service.store(new ByteArrayInputStream(new byte[]{1, 2, 3}), "audio/mpeg", null);

        assertThat(key).doesNotContain(".");
    }

    private boolean wasClosed = false;

    @Test
    void store_closesTheSourceStream() {
        InputStream in = new ByteArrayInputStream("bytes".getBytes(StandardCharsets.UTF_8));
        InputStream tracked = new java.io.FilterInputStream(in) {
            @Override
            public void close() throws IOException {
                wasClosed = true;
                super.close();
            }
        };

        service.store(tracked, "audio/mpeg", "memo.mp3");

        assertThat(wasClosed).isTrue();
    }

    @Test
    void load_missingKey_throwsStorageException() {
        assertThatThrownBy(() -> service.load("never-stored.mp3"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("missing or unreadable");
    }

    @Test
    void load_keyThatEscapesTheStorageRoot_isRejected() {
        assertThatThrownBy(() -> service.load("../../../../../../etc/passwd"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("escapes storage root");
    }

    @Test
    void delete_removesAStoredFile_soASubsequentLoadFails() {
        String key = service.store(new ByteArrayInputStream(new byte[]{1, 2, 3}), "audio/mpeg", "memo.mp3");

        service.delete(key);

        assertThatThrownBy(() -> service.load(key)).isInstanceOf(StorageException.class);
    }

    @Test
    void delete_missingKey_isTreatedAsSuccess_noExceptionThrown() {
        assertThat(Files.exists(tempDir.resolve("nonexistent.mp3"))).isFalse();

        service.delete("nonexistent.mp3");
    }

    @Test
    void delete_keyThatEscapesTheStorageRoot_isRejected() {
        assertThatThrownBy(() -> service.delete("../../../../../../etc/passwd"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("escapes storage root");
    }
}
