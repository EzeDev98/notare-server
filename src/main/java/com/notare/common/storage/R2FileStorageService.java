package com.notare.common.storage;

import com.notare.common.exception.StorageException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.UUID;

/**
 * Prod voice-note storage — Cloudflare R2, an S3-compatible object store, via
 * the plain AWS SDK v2 S3 client pointed at R2's endpoint instead of AWS's.
 * @Profile("prod") mirrors LocalFileStorageService's own @Profile("!prod").
 *
 * @ConditionalOnProperty(..., matchIfMissing = true) is the newer addition:
 * this bean now only activates when notare.storage.backend=r2 (or the
 * property is absent entirely, which preserves prior behavior for anyone
 * already running prod against R2). Set notare.storage.backend=postgres to
 * activate PostgresFileStorageService instead — see StorageProperties. Only
 * one FileStorageService bean is ever active per environment, same as
 * before; the selection is just explicit now instead of profile-only.
 *
 * Three settings below are NOT optional and getting any one of them wrong
 * produces a confusing failure rather than an obvious one, so they're
 * documented here rather than left as unexplained SDK calls:
 *
 *   - pathStyleAccessEnabled(true): without this, the SDK builds a
 *     virtual-hosted-style URL (bucket-name.endpoint-host), which requires
 *     wildcard DNS AWS provides but R2 doesn't — requests would fail to
 *     resolve, not just fail auth.
 *   - chunkedEncodingEnabled(false): the SDK defaults to chunked transfer
 *     encoding on putObject, which R2's signature verification rejects —
 *     every upload would come back as an opaque 403, unrelated-looking to
 *     the actual cause.
 *   - Region.of("auto"): the SDK requires SOME region value even though R2
 *     ignores it; "auto" is Cloudflare's own documented convention for this.
 *
 * Known limitation, not yet addressed: VoiceNoteController's /stream endpoint
 * serves HTTP Range requests (audio scrubbing) by wrapping whatever load()
 * returns in a Spring ResourceRegion, which satisfies a range by reading the
 * resource from byte 0 and discarding everything before the requested start.
 * That was cheap against LocalFileStorageService's file-backed Resource. Here,
 * it means seeking near the END of a long recording downloads-and-discards
 * everything before it from R2 first — functionally correct (playback and
 * seeking both still work), just wasteful and slower for a deep seek into a
 * long note. Fixing it properly means load() (or a new range-aware method)
 * issuing a ranged GetObjectRequest so R2 only sends the requested bytes —
 * a real follow-up, deliberately not bundled into this change.
 */
@Service
@Profile("prod")
@ConditionalOnProperty(prefix = "notare.storage", name = "backend", havingValue = "r2", matchIfMissing = true)
public class R2FileStorageService implements FileStorageService {

    private final S3Client s3;
    private final String bucket;

    public R2FileStorageService(StorageProperties properties) {
        StorageProperties.R2 r2 = properties.getR2();
        this.bucket = r2.getBucket();

        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                r2.getAccessKeyId(), r2.getSecretAccessKey());

        S3Configuration serviceConfiguration = S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .chunkedEncodingEnabled(false)
                .build();

        this.s3 = S3Client.builder()
                .endpointOverride(URI.create(r2.getEndpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .region(Region.of("auto"))
                .serviceConfiguration(serviceConfiguration)
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }

    @Override
    public String store(InputStream content, String contentType, String originalFilename) {
        // Same key scheme as LocalFileStorageService: a random UUID we generate
        // ourselves, never derived from client input, plus the original
        // extension for a sane Content-Type/filename on download later.
        String key = UUID.randomUUID() + extension(originalFilename);

        byte[] bytes;
        try (InputStream in = content) {
            // Voice notes are capped at 20MB (notare.voice.max-size, enforced
            // upstream by VoiceUploadValidator before this is ever called), so
            // buffering fully into memory is fine — this is never a multi-GB
            // stream, and RequestBody.fromInputStream() needs a known length
            // upfront anyway, which we don't have from a raw InputStream.
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            in.transferTo(buffer);
            bytes = buffer.toByteArray();
        } catch (IOException e) {
            throw new StorageException("Failed to read upload before storing.", e);
        }

        try {
            s3.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType)
                            .build(),
                    RequestBody.fromBytes(bytes));
        } catch (S3Exception e) {
            throw new StorageException("Failed to store file.", e);
        }

        return key;
    }

    @Override
    public Resource load(String storageKey) {
        try {
            InputStream in = s3.getObject(
                    GetObjectRequest.builder().bucket(bucket).key(storageKey).build());
            // InputStreamResource is single-use by design (Spring's own doc
            // comment on the class says so) — that's fine here because a
            // fresh Resource, backed by a fresh GetObject call, is created on
            // every load() invocation; nothing caches or reuses this instance
            // across requests.
            return new InputStreamResource(in);
        } catch (NoSuchKeyException e) {
            throw new StorageException("Stored file is missing or unreadable: " + storageKey, e);
        } catch (S3Exception e) {
            throw new StorageException("Could not read stored file: " + storageKey, e);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            // S3-compatible DELETE is already idempotent on a missing key —
            // it returns success rather than an error — so this satisfies the
            // interface's "a missing key is treated as success" contract with
            // no extra existence check needed.
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(storageKey).build());
        } catch (S3Exception e) {
            throw new StorageException("Failed to delete stored file: " + storageKey, e);
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