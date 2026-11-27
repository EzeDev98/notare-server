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
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
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

/** File storage in Cloudflare R2. */
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
                .region(Region.of(r2.getRegion() == null || r2.getRegion().isBlank() ? "auto" : r2.getRegion()))
                .serviceConfiguration(serviceConfiguration)
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }

    @Override
    public String store(InputStream content, String contentType, String originalFilename) {
        String key = UUID.randomUUID() + extension(originalFilename);

        byte[] bytes;
        try (InputStream in = content) {
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
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(storageKey).build());
        } catch (S3Exception e) {
            throw new StorageException("Failed to delete stored file: " + storageKey, e);
        }
    }

    @Override
    public boolean supportsListing() {
        return true;
    }

    @Override
    public java.util.List<StoredObject> list(String afterKey, int limit) {
        try {
            software.amazon.awssdk.services.s3.model.ListObjectsV2Request.Builder request =
                    software.amazon.awssdk.services.s3.model.ListObjectsV2Request.builder()
                            .bucket(bucket).maxKeys(Math.min(limit, 1000));
            if (afterKey != null) {
                request.startAfter(afterKey);
            }
            return s3.listObjectsV2(request.build()).contents().stream()
                    .map(object -> new StoredObject(object.key(), object.lastModified()))
                    .toList();
        } catch (S3Exception e) {
            throw new StorageException("Failed to list stored files.", e);
        }
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
            s3.copyObject(CopyObjectRequest.builder()
                    .sourceBucket(bucket)
                    .sourceKey(sourceKey)
                    .destinationBucket(bucket)
                    .destinationKey(key)
                    .build());
        } catch (S3Exception e) {
            throw new StorageException("Failed to copy stored file: " + sourceKey, e);
        }
        return key;
    }
}
