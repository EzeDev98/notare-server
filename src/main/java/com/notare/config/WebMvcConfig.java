package com.notare.config;

import com.notare.common.web.ClientIpArgumentResolver;
import com.notare.common.web.UserAgentArgumentResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import com.notare.common.version.ClientVersionInterceptor;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.notare.common.ratelimit.RateLimitInterceptor;
import com.notare.common.ratelimit.RateLimitProperties;
import com.notare.common.ratelimit.RateLimiter;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

@RequiredArgsConstructor
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final ClientIpArgumentResolver clientIpArgumentResolver;
    private final UserAgentArgumentResolver userAgentArgumentResolver;
    private final ClientVersionInterceptor clientVersionInterceptor;
    private final RateLimiter rateLimiter;
    private final RateLimitProperties rateLimitProperties;

    @Value("${app.title}")
    private String appTitle;


    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(clientIpArgumentResolver);
        resolvers.add(userAgentArgumentResolver);
    }

    /** Step 17j (B-15): per-user limit on the sync endpoint. */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Step 17k: first, so an outdated client is turned away before it uses up any rate limit.
        registry.addInterceptor(clientVersionInterceptor)
                .addPathPatterns("/" + appTitle + "/**")
                .excludePathPatterns("/" + appTitle + "/internal/**", "/" + appTitle + "/admin/**",
                        "/" + appTitle + "/keep-alive");
        registry.addInterceptor(new RateLimitInterceptor(rateLimiter, "sync", rateLimitProperties::getSyncPerMinute))
                .addPathPatterns("/" + appTitle + "/sync");
    }

}
