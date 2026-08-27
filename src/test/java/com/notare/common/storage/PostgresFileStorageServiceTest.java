package com.notare.common.storage;

import com.notare.common.exception.StorageException;
import com.notare.common.storage.model.FileBlob;
import com.notare.common.storage.repository.FileBlobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Step 10b of the coverage roadmap.
 *
 * <p>Mirrors the other two FileStorageService test classes' scope: the
 * store/load/delete contract, the UUID-plus-extension key scheme, the
 * "missing key on delete is success" contract this class implements
 * explicitly (a check-then-act, unlike Local's deleteIfExists or R2's
 * naturally-idempotent DELETE — worth pinning down for exactly that reason),
 * and each failure path wrapped as StorageException.
 */
@ExtendWith(MockitoExtension.class)
class PostgresFileStorageServiceTest {

    @Mock
    private FileBlobRepository repository;

    private PostgresFileStorageService service;

    @BeforeEach
    void setUp() {
        service = new PostgresFileStorageService(repository);
    }

    @Test
    void store_savesABlobWithTheGeneratedKeyAndReturnsThatSameKey() {
        byte[] content = "hello voice note".getBytes(StandardCharsets.UTF_8);

        String key = service.store(new ByteArrayInputStream(content), "audio/mpeg", "memo.mp3");

        assertThat(key).endsWith(".mp3");

        ArgumentCaptor<FileBlob> captor = ArgumentCaptor.forClass(FileBlob.class);
        verify(repository).save(captor.capture());
        FileBlob saved = captor.getValue();
        assertThat(saved.getStorageKey()).isEqualTo(key);
        assertThat(saved.getContent()).isEqualTo(content);
        assertThat(saved.getContentType()).isEqualTo("audio/mpeg");
        assertThat(saved.getSizeBytes()).isEqualTo(content.length);
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

    @Test
    void store_ioExceptionReadingTheUpload_throwsStorageExceptionBeforeAnySave() {
        InputStream broken = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("disconnected");
            }
        };

        assertThatThrownBy(() -> service.store(broken, "audio/mpeg", "memo.mp3"))
                .isInstanceOf(StorageException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void store_repositorySaveFails_wrappedAsStorageException() {
        when(repository.save(any(FileBlob.class)))
                .thenThrow(new DataIntegrityViolationException("constraint violation"));

        assertThatThrownBy(() ->
                service.store(new ByteArrayInputStream(new byte[]{1, 2, 3}), "audio/mpeg", "memo.mp3"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Failed to store file");
    }

    @Test
    void load_existingKey_returnsTheStoredBytesAsAByteArrayResource() throws IOException {
        byte[] content = "hello voice note".getBytes(StandardCharsets.UTF_8);
        FileBlob blob = new FileBlob();
        blob.setStorageKey("abc123.mp3");
        blob.setContent(content);
        blob.setContentType("audio/mpeg");
        blob.setSizeBytes(content.length);
        when(repository.findById("abc123.mp3")).thenReturn(Optional.of(blob));

        Resource result = service.load("abc123.mp3");

        assertThat(result).isInstanceOf(ByteArrayResource.class);
        assertThat(result.getInputStream().readAllBytes()).isEqualTo(content);
    }

    @Test
    void load_missingKey_throwsStorageException() {
        when(repository.findById("missing.mp3")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.load("missing.mp3"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("missing or unreadable");
    }

    @Test
    void delete_existingKey_deletesTheRow() {
        when(repository.existsById("abc123.mp3")).thenReturn(true);

        service.delete("abc123.mp3");

        verify(repository).deleteById("abc123.mp3");
    }

    @Test
    void delete_missingKey_isTreatedAsSuccess_deleteByIdNeverCalled() {
        // Explicit check-then-act, unlike Local's deleteIfExists (which is
        // atomic) or R2's naturally-idempotent DELETE — JpaRepository
        // .deleteById() throws EmptyResultDataAccessException on a missing
        // row, so this class guards it itself rather than inheriting the
        // "missing key = success" contract for free.
        when(repository.existsById(anyString())).thenReturn(false);

        service.delete("nonexistent.mp3");

        verify(repository, never()).deleteById(any());
    }
}