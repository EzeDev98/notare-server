package com.notare.common.storage;

import com.notare.common.exception.StorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Step 10c of the coverage roadmap.
 *
 * <p>R2FileStorageService builds its own {@link S3Client} inside its
 * constructor from {@link StorageProperties} — there is no seam to inject a
 * mock through, unlike every other collaborator in this codebase. Rather
 * than change production code just to make it testable (a real client isn't
 * hard to construct; it never touches the network until a request is made),
 * this test builds the service normally against harmless dummy R2 config,
 * then reflectively swaps its private {@code s3} field for a Mockito mock.
 * The dummy config (a syntactically valid but unreachable endpoint) is never
 * actually dialed — {@code S3Client.builder().build()} only configures a
 * client, it doesn't connect.
 *
 * <p>Covers the same three-method contract as the other two backends: the
 * UUID-plus-extension key scheme, store/load/delete happy paths verified
 * against the exact request the SDK was called with, and each of the
 * documented S3Exception / NoSuchKeyException / IOException failure paths
 * mapping to StorageException.
 */
@ExtendWith(MockitoExtension.class)
class R2FileStorageServiceTest {

    private static final String BUCKET = "test-bucket";

    @Mock
    private S3Client s3;

    private R2FileStorageService service;

    @BeforeEach
    void setUp() throws Exception {
        StorageProperties properties = new StorageProperties();
        properties.getR2().setEndpoint("http://localhost:9000");
        properties.getR2().setBucket(BUCKET);
        properties.getR2().setAccessKeyId("dummy-access-key");
        properties.getR2().setSecretAccessKey("dummy-secret-key");

        service = new R2FileStorageService(properties);

        // Swap the real SDK client the constructor built for our mock. Both
        // fields are `private final`, but that only blocks reassignment from
        // OUTSIDE reflection at the language level — Field#set on an
        // instance (non-static) final field works once made accessible.
        Field s3Field = R2FileStorageService.class.getDeclaredField("s3");
        s3Field.setAccessible(true);
        s3Field.set(service, s3);
    }

    @Test
    void store_putsTheObjectUnderAGeneratedUuidKeyWithTheOriginalExtension() {
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        String key = service.store(
                new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)), "audio/mpeg", "memo.mp3");

        assertThat(key).endsWith(".mp3");

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(captor.capture(), any(RequestBody.class));
        PutObjectRequest request = captor.getValue();
        assertThat(request.bucket()).isEqualTo(BUCKET);
        assertThat(request.key()).isEqualTo(key);
        assertThat(request.contentType()).isEqualTo("audio/mpeg");
    }

    @Test
    void store_originalFilenameHasNoExtension_keyHasNoExtensionEither() {
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        String key = service.store(new ByteArrayInputStream(new byte[]{1, 2, 3}), "audio/mpeg", "recording");

        assertThat(key).doesNotContain(".");
    }

    @Test
    void store_sendsTheExactBytesRead_asARequestBody() throws IOException {
        byte[] content = "hello voice note".getBytes(StandardCharsets.UTF_8);
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        service.store(new ByteArrayInputStream(content), "audio/mpeg", "memo.mp3");

        ArgumentCaptor<RequestBody> bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3).putObject(any(PutObjectRequest.class), bodyCaptor.capture());
        assertThat(bodyCaptor.getValue().contentStreamProvider().newStream().readAllBytes()).isEqualTo(content);
    }

    @Test
    void store_ioExceptionReadingTheUpload_throwsStorageExceptionBeforeAnyS3Call() {
        InputStream broken = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("disconnected");
            }
        };

        assertThatThrownBy(() -> service.store(broken, "audio/mpeg", "memo.mp3"))
                .isInstanceOf(StorageException.class);

        verify(s3, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void store_s3ExceptionOnPut_wrappedAsStorageException() {
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().message("access denied").build());

        assertThatThrownBy(() ->
                service.store(new ByteArrayInputStream(new byte[]{1, 2, 3}), "audio/mpeg", "memo.mp3"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Failed to store file");
    }

    @Test
    void load_existingKey_returnsAnInputStreamResourceOfTheObjectsBytes() throws IOException {
        byte[] content = "hello voice note".getBytes(StandardCharsets.UTF_8);
        ResponseInputStream<GetObjectResponse> responseStream = new ResponseInputStream<>(
                GetObjectResponse.builder().build(),
                AbortableInputStream.create(new ByteArrayInputStream(content)));
        when(s3.getObject(any(GetObjectRequest.class))).thenReturn(responseStream);

        Resource result = service.load("abc123.mp3");

        assertThat(result).isInstanceOf(InputStreamResource.class);
        assertThat(result.getInputStream().readAllBytes()).isEqualTo(content);

        ArgumentCaptor<GetObjectRequest> captor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3).getObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().key()).isEqualTo("abc123.mp3");
    }

    @Test
    void load_noSuchKey_throwsStorageException() {
        when(s3.getObject(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("not found").build());

        assertThatThrownBy(() -> service.load("missing.mp3"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("missing or unreadable");
    }

    @Test
    void load_otherS3Failure_throwsStorageException() {
        when(s3.getObject(any(GetObjectRequest.class)))
                .thenThrow(S3Exception.builder().message("service unavailable").build());

        assertThatThrownBy(() -> service.load("abc123.mp3"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Could not read stored file");
    }

    @Test
    void delete_deletesTheObjectAtTheGivenKey() {
        when(s3.deleteObject(any(DeleteObjectRequest.class))).thenReturn(DeleteObjectResponse.builder().build());

        service.delete("abc123.mp3");

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3).deleteObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().key()).isEqualTo("abc123.mp3");
    }

    @Test
    void delete_s3ExceptionOnDelete_wrappedAsStorageException() {
        when(s3.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(S3Exception.builder().message("service unavailable").build());

        assertThatThrownBy(() -> service.delete("abc123.mp3"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Failed to delete stored file");
    }
}