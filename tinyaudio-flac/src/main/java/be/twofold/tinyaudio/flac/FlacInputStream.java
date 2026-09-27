package be.twofold.tinyaudio.flac;

import be.twofold.tinyaudio.flac.metadata.Metadata;
import be.twofold.tinyaudio.flac.metadata.StreamInfo;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public final class FlacInputStream extends FilterInputStream {
    private final Metadata metadata;

    private FlacInputStream(InputStream in, Metadata metadata) {
        super(Objects.requireNonNull(in));
        this.metadata = Objects.requireNonNull(metadata);
    }

    public static FlacInputStream open(InputStream in) throws IOException {
        Metadata metadata = Metadata.read(in);
        return new FlacInputStream(in, metadata);
    }

    public static StreamInfo readStreamInfo(InputStream in) throws IOException {
        return Metadata.readStreamInfo(in);
    }

    public Metadata metadata() {
        return metadata;
    }

    public StreamInfo streamInfo() {
        return metadata.getStreamInfo();
    }
}
