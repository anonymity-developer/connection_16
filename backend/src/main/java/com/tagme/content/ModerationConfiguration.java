package com.tagme.content;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration @EnableScheduling
public class ModerationConfiguration {
    @Bean @ConditionalOnMissingBean(ModerationProvider.class)
    ModerationProvider unconfiguredModerationProvider() {
        return text -> new ModerationProvider.Decision("REVIEW_REQUIRED","PROVIDER_NOT_CONFIGURED","NONE","unconfigured");
    }
}
