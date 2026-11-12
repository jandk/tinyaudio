package be.twofold.tinyaudio.opus;

import be.twofold.tinyaudio.core.AudioInfo;
import be.twofold.tinyaudio.core.BlockAudioDecoder;
import be.twofold.tinyaudio.core.Packet;
import be.twofold.tinyaudio.core.PacketSource;
import be.twofold.tinyaudio.opus.internal.OpusDecoder;

import java.io.IOException;
import java.util.Objects;

public final class OpusAudioDecoder extends BlockAudioDecoder {
    private static final int MAX_BLOCK_SIZE = 5760; // 120ms at 48kHz
    private final PacketSource packets;
    private final OpusIdentification identification;
    private final OpusDecoder decoder;
    private final float[] pcmFloat;

    private int skip;
    int rangeFinal;

    OpusAudioDecoder(PacketSource packets, OpusIdentification identification) {
        super(identification.channels(), MAX_BLOCK_SIZE);
        this.packets = Objects.requireNonNull(packets);
        this.identification = Objects.requireNonNull(identification);
        this.decoder = new OpusDecoder(identification.channels());
        this.pcmFloat = new float[identification.channels() * MAX_BLOCK_SIZE];
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

        int samples = decoder.opus_decode_native(
            packet.getData(), 0, packet.getData().length,
            pcmFloat, 0, MAX_BLOCK_SIZE, false);

        int start = Math.min(skip, samples);
        skip -= start;

        int channels = identification.channels();
        for (int c = 0; c < channels; c++) {
            short[] channel = pcm[c];
            for (int s = start, i = start * channels + c; s < samples; s++, i += channels) {
                channel[s - start] = floatToPcm(pcmFloat[i]);
            }
        }

        this.rangeFinal = decoder.rng();
        return samples - start;
    }

    private short floatToPcm(float f) {
        float x = f * 32768.0f; // CELT_SIG_SCALE
        x = Math.max(x, -32768.0f);
        x = Math.min(x, 32767.0f);
        return (short) Math.rint(x);
    }

    @Override
    public void close() throws IOException {
        packets.close();
    }
}
