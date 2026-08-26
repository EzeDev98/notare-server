package com.notare.feature.voicenote.validation;

import com.notare.common.exception.InvalidVoiceUploadException;
import com.notare.common.exception.UnsupportedAudioFormatException;
import com.notare.common.exception.VoiceFileTooLargeException;
import com.notare.feature.voicenote.config.VoiceNoteProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Step 2b of the coverage roadmap.
 *
 * <p>Covers {@link VoiceUploadValidator#validate}: the fail-fast order
 * (missing/empty file, then content type, then size, then duration), the
 * case-insensitive content-type match, and both edges of the size and
 * duration ceilings - "at exactly the limit" is documented by the code as
 * allowed ({@code >}, not {@code >=}), which is exactly the kind of
 * off-by-one a test needs to pin down rather than assume.
 *
 * <p>Expected byte thresholds are derived from the same {@link DataSize}
 * object the validator itself reads ({@code properties.getMaxSize()}),
 * never hand-computed, so these tests can't drift from whatever Spring's
 * {@code DataSize} actually does internally.
 */
class VoiceUploadValidatorTest {

    private VoiceNoteProperties properties;
    private VoiceUploadValidator validator;

    @BeforeEach
    void setUp() {
        properties = new VoiceNoteProperties();
        properties.setAllowedContentTypes(List.of("audio/mpeg", "audio/wav"));
        properties.setMaxSize(DataSize.ofMegabytes(5));
        properties.setMaxDurationSeconds(120);
        validator = new VoiceUploadValidator(properties);
    }

    private MultipartFile validFile() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getContentType()).thenReturn("audio/mpeg");
        when(file.getSize()).thenReturn(1024L);
        return file;
    }

    @Test
    void validate_nullFileThrowsInvalidVoiceUploadException() {
        assertThatThrownBy(() -> validator.validate(null, null))
                .isInstanceOf(InvalidVoiceUploadException.class)
                .hasMessage("Audio file is missing or empty.");
    }

    @Test
    void validate_emptyFileThrowsInvalidVoiceUploadException() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(true);

        assertThatThrownBy(() -> validator.validate(file, null))
                .isInstanceOf(InvalidVoiceUploadException.class)
                .hasMessage("Audio file is missing or empty.");
    }

    @Test
    void validate_emptyFileIsRejectedBeforeContentTypeIsEverInspected() {
        // Fail-fast ordering: an empty file shouldn't even trigger a
        // getContentType() call.
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(true);

        assertThatThrownBy(() -> validator.validate(file, null))
                .isInstanceOf(InvalidVoiceUploadException.class);

        verify(file, never()).getContentType();
    }

    @Test
    void validate_missingContentTypeThrowsUnsupportedAudioFormatException() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getContentType()).thenReturn(null);

        assertThatThrownBy(() -> validator.validate(file, null))
                .isInstanceOf(UnsupportedAudioFormatException.class);
    }

    @Test
    void validate_disallowedContentTypeThrowsUnsupportedAudioFormatExceptionListingAllowedTypes() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getContentType()).thenReturn("video/mp4");

        assertThatThrownBy(() -> validator.validate(file, null))
                .isInstanceOf(UnsupportedAudioFormatException.class)
                .hasMessageContaining("video/mp4")
                .hasMessageContaining("audio/mpeg")
                .hasMessageContaining("audio/wav");
    }

    @Test
    void validate_contentTypeMatchIsCaseInsensitive() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getContentType()).thenReturn("AUDIO/MPEG");
        when(file.getSize()).thenReturn(1024L);

        assertThatCode(() -> validator.validate(file, null)).doesNotThrowAnyException();
    }

    @Test
    void validate_fileSizeAtExactlyTheLimitIsAllowed() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getContentType()).thenReturn("audio/mpeg");
        when(file.getSize()).thenReturn(properties.getMaxSize().toBytes());

        assertThatCode(() -> validator.validate(file, null)).doesNotThrowAnyException();
    }

    @Test
    void validate_fileSizeOneByteOverTheLimitThrowsVoiceFileTooLargeException() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getContentType()).thenReturn("audio/mpeg");
        when(file.getSize()).thenReturn(properties.getMaxSize().toBytes() + 1);

        assertThatThrownBy(() -> validator.validate(file, null))
                .isInstanceOf(VoiceFileTooLargeException.class)
                .hasMessageContaining("5 MB");
    }

    @Test
    void validate_durationAtExactlyTheLimitIsAllowed() {
        MultipartFile file = validFile();

        assertThatCode(() -> validator.validate(file, properties.getMaxDurationSeconds()))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_durationOneSecondOverTheLimitThrowsInvalidVoiceUploadException() {
        MultipartFile file = validFile();

        assertThatThrownBy(() -> validator.validate(file, properties.getMaxDurationSeconds() + 1))
                .isInstanceOf(InvalidVoiceUploadException.class)
                .hasMessageContaining("120 seconds");
    }

    @Test
    void validate_nullDurationSkipsTheDurationCheckEntirely() {
        MultipartFile file = validFile();

        assertThatCode(() -> validator.validate(file, null)).doesNotThrowAnyException();
    }

    @Test
    void validate_aFullyValidUploadDoesNotThrow() {
        MultipartFile file = validFile();

        assertThatCode(() -> validator.validate(file, 60)).doesNotThrowAnyException();
    }
}