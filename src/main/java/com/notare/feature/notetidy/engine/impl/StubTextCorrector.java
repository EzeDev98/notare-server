package com.notare.feature.notetidy.engine.impl;

import com.notare.feature.notetidy.engine.CorrectionResult;
import com.notare.feature.notetidy.engine.TextCorrector;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Dev and tests: says every note is English and changes nothing. */
@Component
@ConditionalOnProperty(prefix = "notare.tidy", name = "corrector", havingValue = "stub", matchIfMissing = true)
public class StubTextCorrector implements TextCorrector {

    @Override
    public CorrectionResult correct(String text) {
        return new CorrectionResult("en-US", text);
    }
}
