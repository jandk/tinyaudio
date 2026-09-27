package be.twofold.tinyaudio.ogg;

import be.twofold.tinyaudio.core.Packet;

import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

public final class OggCodecs {
    private OggCodecs() {
    }

    private static final List<OggCodec> CODECS = ServiceLoader
        .load(OggCodec.class).stream()
        .map(ServiceLoader.Provider::get)
        .collect(Collectors.toList());

    public static Optional<OggCodec> findCodec(Packet first) {
        return CODECS.stream()
            .filter(codec -> codec.identifies(first.getData()))
            .findFirst();
    }
}
