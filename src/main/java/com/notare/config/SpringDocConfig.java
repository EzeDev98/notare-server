package com.notare.config;

import com.notare.common.web.ClientIp;
import com.notare.common.web.UserAgent;
import jakarta.annotation.PostConstruct;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

/** Hides internal parameter types from the generated API docs. */
@Configuration
public class SpringDocConfig {

    @PostConstruct
    public void init() {
        SpringDocUtils.getConfig().addAnnotationsToIgnore(ClientIp.class, UserAgent.class, AuthenticationPrincipal.class);
    }
}
