package devp0tion.mechanics.wrench;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/** Reads a locale file of the mod ({@code [section]} headers, {@code key=value} lines, {@code //} comments). */
final class LangFile {

    private final Map<String, String> entries = new HashMap<>();

    private LangFile() {
    }

    /** The mod's locale file, e.g. {@code kr}; the tests run in the project directory. */
    static LangFile load(String language) {
        Path path = Paths.get("src", "main", "resources", "locale", language + ".lang");
        LangFile file = new LangFile();
        try {
            String section = "";
            for (String raw : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("//")) {
                    continue;
                }
                if (line.startsWith("[") && line.endsWith("]")) {
                    section = line.substring(1, line.length() - 1);
                    continue;
                }
                int equals = line.indexOf('=');
                if (equals > 0) {
                    file.entries.put(section + "." + line.substring(0, equals), line.substring(equals + 1));
                }
            }
        } catch (IOException e) {
            throw new AssertionError("cannot read " + path.toAbsolutePath() + ": " + e);
        }
        return file;
    }

    /** The text of a key, or {@code null}. */
    String get(String section, String key) {
        return entries.get(section + "." + key);
    }

    /** The text of a {@code [ui]} key; fails when it is missing. */
    String ui(String key) {
        String text = get("ui", key);
        if (text == null) {
            throw new AssertionError("missing [ui] " + key);
        }
        return text;
    }

}
