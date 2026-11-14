package be.twofold.tinyaudio.opus;

import be.twofold.tinyaudio.core.AudioInfo;
import be.twofold.tinyaudio.core.BlockAudioDecoder;
import be.twofold.tinyaudio.core.Packet;
import be.twofold.tinyaudio.core.PacketSource;
import be.twofold.tinyaudio.core.internal.PcmUtils;
import be.twofold.tinyaudio.ogg.internal.OggTrim;
import be.twofold.tinyaudio.opus.internal.OpusMsDecoder;

import java.io.IOException;
import java.util.Objects;

public final class OpusAudioDecoder extends BlockAudioDecoder {
    private static final int MAX_BLOCK_SIZE = 5760; // 120ms at 48kHz
    private final PacketSource packets;
    private final OpusIdentification identification;
    private final OpusMsDecoder decoder;
    private final float[][] pcmFloat;
    private final OggTrim trim = new OggTrim();

    private int skip;
    int rangeFinal;

    OpusAudioDecoder(PacketSource packets, OpusIdentification identification) {
        super(identification.channels(), MAX_BLOCK_SIZE);
        this.packets = Objects.requireNonNull(packets);
        this.identification = Objects.requireNonNull(identification);
        this.decoder = new OpusMsDecoder(
            identification.channels(),
            identification.streamCount(),
            identification.coupledCount(),
            identification.mapping()
        );
        this.pcmFloat = new float[identification.channels()][MAX_BLOCK_SIZE];
        this.skip = identification.preSkip();
    }

    @Override
    public AudioInfo info() {
        return new AudioInfo(
            "ogg", "opus", "opus",
            48000, // Fixed
            identification.channels(),
            -1 // opus has it at the end of the stream
        );
    }

    @Override
    protected int decodeBlock(short[][] pcm) throws IOException {
        Packet packet = packets.next();
        if (packet == null) {
            return -1;
        }

        int samples = decoder.opus_multistream_decode_native(
            packet.data(), 0, packet.data().length,
            pcmFloat, 0, MAX_BLOCK_SIZE
        );

        trim.update(packet, samples);
        int start = trim.start();
        int count = trim.count();
        int skipped = Math.min(skip, count);
        skip -= skipped;
        start += skipped;
        count -= skipped;

        for (int c = 0; c < identification.channels(); c++) {
            PcmUtils.floatToPcm(pcmFloat[c], start, 1, pcm[c], 0, count);
        }

        this.rangeFinal = decoder.rng();
        return count;
    }

    @Override
    public void close() throws IOException {
        packets.close();
    }
}
