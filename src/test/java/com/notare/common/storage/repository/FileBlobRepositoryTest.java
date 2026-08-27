package com.notare.common.storage.repository;

import com.notare.common.storage.model.FileBlob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class FileBlobRepositoryTest {

    @Autowired
    private FileBlobRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void save_and_findById_round_trip_file_blob() {
        FileBlob blob =
                blob(
                        "voice/abc123",
                        new byte[]{1, 2, 3, 4},
                        "audio/mpeg"
                );

        repository.saveAndFlush(blob);

        assertThat(repository.findById("voice/abc123"))
                .isPresent()
                .get()
                .satisfies(saved -> {
                    assertThat(saved.getStorageKey())
                            .isEqualTo("voice/abc123");

                    assertThat(saved.getContent())
                            .containsExactly(1, 2, 3, 4);

                    assertThat(saved.getContentType())
                            .isEqualTo("audio/mpeg");

                    assertThat(saved.getSizeBytes())
                            .isEqualTo(4L);

                    assertThat(saved.getCreatedAt())
                            .isNotNull();
                });
    }

    @Test
    void existsById_uses_storage_key_as_primary_key() {
        repository.saveAndFlush(
                blob(
                        "voice/existing",
                        new byte[]{10, 20},
                        "audio/wav"
                )
        );

        assertThat(repository.existsById("voice/existing"))
                .isTrue();

        assertThat(repository.existsById("voice/missing"))
                .isFalse();
    }

    @Test
    void deleteById_removes_blob() {
        repository.saveAndFlush(
                blob(
                        "voice/delete-me",
                        new byte[]{1, 2, 3},
                        "audio/mpeg"
                )
        );

        assertThat(repository.existsById("voice/delete-me"))
                .isTrue();

        repository.deleteById("voice/delete-me");
        repository.flush();

        assertThat(repository.existsById("voice/delete-me"))
                .isFalse();
    }

    @Test
    void storage_key_is_the_unique_identity_of_a_blob() {
        repository.saveAndFlush(
                blob(
                        "voice/same-key",
                        new byte[]{1},
                        "audio/mpeg"
                )
        );

        FileBlob replacement =
                blob(
                        "voice/same-key",
                        new byte[]{9, 8, 7},
                        "audio/wav"
                );

        repository.saveAndFlush(replacement);

        FileBlob saved =
                repository.findById("voice/same-key")
                        .orElseThrow();

        assertThat(saved.getContent())
                .containsExactly(9, 8, 7);

        assertThat(saved.getContentType())
                .isEqualTo("audio/wav");
    }

    @Test
    void binary_content_is_persisted_without_corruption() {
        byte[] content = new byte[]{
                0,
                1,
                2,
                127,
                -1,
                -128,
                42
        };

        repository.saveAndFlush(
                blob(
                        "voice/binary",
                        content,
                        "application/octet-stream"
                )
        );

        byte[] stored =
                repository.findById("voice/binary")
                        .orElseThrow()
                        .getContent();

        assertThat(stored)
                .isNotNull();

        assertThat(Arrays.equals(stored, content))
                .isTrue();
    }

    private static FileBlob blob(
            String storageKey,
            byte[] content,
            String contentType
    ) {
        FileBlob blob = new FileBlob();

        blob.setStorageKey(storageKey);
        blob.setContent(content);
        blob.setContentType(contentType);
        blob.setSizeBytes(content.length);

        // This also verifies that @PrePersist does not overwrite an
        // explicitly supplied creation timestamp.
        blob.setCreatedAt(
                LocalDateTime.of(2026, 1, 1, 12, 0)
        );

        return blob;
    }
}
