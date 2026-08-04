package com.notare.config;

import com.notare.common.web.ClientIp;
import com.notare.common.web.UserAgent;
import jakarta.annotation.PostConstruct;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpringDocConfig {

    @PostConstruct
    public void init() {
        SpringDocUtils.getConfig().addAnnotationsToIgnore(ClientIp.class, UserAgent.class);
    }
}