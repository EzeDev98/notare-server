package com.notare.feature.notetidy.engine;

import java.util.Optional;

/** Infers a short title for a note. Empty when nothing usable came back: never insert junk. */
public interface TitleEngine {

    Optional<String> inferTitle(String text);
}
