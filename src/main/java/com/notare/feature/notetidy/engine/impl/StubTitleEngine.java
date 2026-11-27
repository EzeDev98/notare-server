package com.notare.feature.notetidy.engine.impl;

import com.notare.feature.notetidy.engine.TitleEngine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Dev and tests: never infers a title, so no note gets a first line it did not have. */
@Component
@ConditionalOnProperty(prefix = "notare.tidy", name = "engine", havingValue = "stub", matchIfMissing = true)
public class StubTitleEngine implements TitleEngine {

    @Override
    public Optional<String> inferTitle(String text) {
        return Optional.empty();
    }
}
