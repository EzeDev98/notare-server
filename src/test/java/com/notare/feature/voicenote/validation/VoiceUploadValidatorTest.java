package com.notare.feature.voicenote.validation;

import com.notare.common.exception.InvalidVoiceUploadException;
import com.notare.common.exception.UnsupportedAudioFormatException;
import com.notare.common.exception.VoiceFileTooLargeException;
import com.notare.feature.voicenote.config.VoiceNoteProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Tests for VoiceUploadValidator. */
class VoiceUploadValidatorTest {

    private VoiceNoteProperties properties;
    private VoiceUploadValidator validator;

    @BeforeEach
    void setUp() {
        properties = new VoiceNoteProperties();
        properties.setMaxSize(DataSize.ofMegabytes(5));
        properties.setMaxDurationSeconds(120);
        validator = new VoiceUploadValidator(properties);
    }

    private static final byte[] MP3 = {0x49, 0x44, 0x33, 0, 0, 0, 0, 0, 0, 0, 0, 0};

    private static void stubContent(MultipartFile file, byte[] bytes) {
        try {
            when(file.getInputStream()).thenReturn(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private MultipartFile validFile() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(1024L);
        stubContent(file, MP3);
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
    void validate_emptyFileIsRejectedBeforeTheFileIsEverRead() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(true);

        assertThatThrownBy(() -> validator.validate(file, null))
                .isInstanceOf(InvalidVoiceUploadException.class);

        verify(file, never()).getInputStream();
    }

    @Test
    void validate_unrecognisedBytesThrowUnsupportedAudioFormatExceptionWhateverTheDeclaredType() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(1024L);
        when(file.getContentType()).thenReturn("audio/mpeg");
        stubContent(file, "not audio at all".getBytes());

        assertThatThrownBy(() -> validator.validate(file, null))
                .isInstanceOf(UnsupportedAudioFormatException.class)
                .hasMessageContaining("Unsupported audio format");
    }

    @Test
    void validate_headerShorterThanAnySignatureThrowsUnsupportedAudioFormatException() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(1L);
        stubContent(file, new byte[] {0x49});

        assertThatThrownBy(() -> validator.validate(file, null))
                .isInstanceOf(UnsupportedAudioFormatException.class);
    }

    @Test
    void validate_declaredContentTypeIsIgnoredWhenTheBytesAreSupportedAudio() {
        MultipartFile file = validFile();
        when(file.getContentType()).thenReturn("video/mp4");

        assertThatCode(() -> validator.validate(file, null)).doesNotThrowAnyException();
    }

    @Test
    void validate_fileSizeAtExactlyTheLimitIsAllowed() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(properties.getMaxSize().toBytes());
        stubContent(file, MP3);

        assertThatCode(() -> validator.validate(file, null)).doesNotThrowAnyException();
    }

    @Test
    void validate_fileSizeOneByteOverTheLimitThrowsVoiceFileTooLargeException() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
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
