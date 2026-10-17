package com.notare.common.version;

import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A client version such as "0.1.0" (step 17k). Missing minor and patch parts count as 0, a leading "v"
 * and a suffix such as "-beta.1" are ignored, so "v1.2-beta.1" is 1.2.0.
 */
public record ClientVersion(int major, int minor, int patch) implements Comparable<ClientVersion> {

    private static final Pattern FORMAT = Pattern.compile("^v?(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?(?:[-+].*)?$");
    private static final Comparator<ClientVersion> ORDER = Comparator
            .comparingInt(ClientVersion::major)
            .thenComparingInt(ClientVersion::minor)
            .thenComparingInt(ClientVersion::patch);

    /** @throws IllegalArgumentException if the text is not a version */
    public static ClientVersion parse(String text) {
        Matcher matcher = FORMAT.matcher(text == null ? "" : text.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Not a version: " + text);
        }
        return new ClientVersion(number(matcher.group(1)), number(matcher.group(2)), number(matcher.group(3)));
    }

    private static int number(String digits) {
        return digits == null ? 0 : Integer.parseInt(digits);
    }

    @Override
    public int compareTo(ClientVersion other) {
        return ORDER.compare(this, other);
    }
}
