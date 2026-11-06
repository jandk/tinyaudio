package be.twofold.tinyaudio.core.internal;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class VorbisComment {
    private final String vendor;
    private final Map<String, List<String>> comments;

    public VorbisComment(String vendor, Map<String, List<String>> comments) {
        this.vendor = Objects.requireNonNull(vendor);
        comments = new LinkedHashMap<>(comments);
        comments.replaceAll((key, value) -> List.copyOf(value));
        this.comments = Collections.unmodifiableMap(comments);
    }

    public static VorbisComment read(ByteBuffer buffer) throws IOException {
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        String vendor = IOUtils.getString(buffer, buffer.getInt(), StandardCharsets.UTF_8);

        int count = buffer.getInt();
        Map<String, List<String>> comments = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            String tag = IOUtils.getString(buffer, buffer.getInt(), StandardCharsets.UTF_8);
            int index = tag.indexOf('=');
            if (index < 0) {
                throw new IOException("Invalid vorbis tag: " + tag);
            }
            String name = validateName(tag.substring(0, index)).toUpperCase(Locale.ROOT);
            String value = tag.substring(index + 1);
            comments.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
        }
        return new VorbisComment(vendor, comments);
    }

    private static String validateName(String name) throws IOException {
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < 0x20 || c > 0x7E || c == '=') {
                throw new IOException("Invalid vorbis tag name: " + name);
            }
        }
        return name;
    }

    public String vendor() {
        return vendor;
    }

    public Map<String, List<String>> comments() {
        return comments;
    }
}
