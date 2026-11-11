package com.tagme.content;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public record PublicationPolicy(boolean moderated) {
    public PublicationPolicy(@Value("${tagme.moderation.enabled:false}") boolean moderated) {
        this.moderated=moderated;
    }
}
