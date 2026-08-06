package com.notare.common.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers StorageProperties as a bean. In 4B this same config will select and
 * expose the FileStorageService implementation (local vs S3) by profile.
 */
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {
}