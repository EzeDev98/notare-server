package com.notare.feature.sync.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Settings for sync. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "notare.sync")
public class SyncProperties {

    private int overlapSeconds = 5;

    private int defaultPageSize = 100;

    private int maxPageSize = 500;

    private int retentionDays = 30;
}
