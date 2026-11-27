package com.notare.feature.notetidy.engine;

import java.util.Optional;

/** Infers a short title for a note. */
public interface TitleEngine {

    Optional<String> inferTitle(String text);
}
