package be.twofold.tinyvorbis;

import be.twofold.tinyaudio.ogg.OggCodec;

public final class VorbisOggCodec implements OggCodec {
    private static final byte[] MAGIC = {0x01, 0x76, 0x6F, 0x72, 0x62, 0x69, 0x73};

    @Override
    public String name() {
        return "Vorbis";
    }

    @Override
    public boolean identifies(byte[] packet) {
        return OggCodec.startsWith(packet, MAGIC);
    }
}
