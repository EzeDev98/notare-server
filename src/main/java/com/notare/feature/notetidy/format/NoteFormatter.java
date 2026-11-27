package com.notare.feature.notetidy.format;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Cleans up a note's layout without changing its words. */
@Component
public class NoteFormatter {

    private static final Pattern STAR_BULLET = Pattern.compile("^\\* (.*)$");

    private static final Pattern ORDERED_ITEM = Pattern.compile("^(\\d{1,9})\\. (.*)$");

    public String format(String text) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }

        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        List<String> out = new ArrayList<>(lines.length);
        boolean previousBlank = false;
        Long nextNumber = null;

        for (String raw : lines) {
            String line = raw.stripTrailing();

            if (line.isEmpty()) {
                nextNumber = null;
                if (!previousBlank) {
                    out.add("");
                }
                previousBlank = true;
                continue;
            }
            previousBlank = false;

            Matcher star = STAR_BULLET.matcher(line);
            if (star.matches()) {
                line = "- " + star.group(1);
            }

            Matcher ordered = ORDERED_ITEM.matcher(line);
            if (ordered.matches()) {
                if (nextNumber == null) {
                    nextNumber = Long.parseLong(ordered.group(1));
                }
                line = nextNumber + ". " + ordered.group(2);
                nextNumber++;
            } else {
                nextNumber = null;
            }

            out.add(line);
        }

        while (!out.isEmpty() && out.get(0).isEmpty()) {
            out.remove(0);
        }
        while (!out.isEmpty() && out.get(out.size() - 1).isEmpty()) {
            out.remove(out.size() - 1);
        }
        return String.join("\n", out);
    }
}
