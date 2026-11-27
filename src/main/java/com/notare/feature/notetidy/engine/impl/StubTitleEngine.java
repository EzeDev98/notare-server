package com.notare.feature.notetidy.engine.impl;

import com.notare.feature.notetidy.engine.TitleEngine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Placeholder title engine for dev and tests that never returns a title. */
@Component
@ConditionalOnProperty(prefix = "notare.tidy", name = "engine", havingValue = "stub", matchIfMissing = true)
public class StubTitleEngine implements TitleEngine {

    @Override
    public Optional<String> inferTitle(String text) {
        return Optional.empty();
    }
}
