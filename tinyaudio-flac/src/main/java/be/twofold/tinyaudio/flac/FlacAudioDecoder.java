package be.twofold.tinyaudio.flac;

import be.twofold.tinyaudio.core.AudioInfo;
import be.twofold.tinyaudio.core.UnsupportedFormatException;
import be.twofold.tinyaudio.core.internal.BlockAudioDecoder;
import be.twofold.tinyaudio.core.internal.Packet;
import be.twofold.tinyaudio.core.internal.PacketSource;
import be.twofold.tinyaudio.flac.internal.FlacDecoder;
import be.twofold.tinyaudio.flac.metadata.StreamInfo;

import java.io.IOException;
import java.util.Objects;

final class FlacAudioDecoder extends BlockAudioDecoder {
    private final FlacDecoder decoder;
    private final PacketSource packets;
    private final StreamInfo streamInfo;
    private final String container;
    private final int[][] pcmInt;

    FlacAudioDecoder(PacketSource packets, StreamInfo streamInfo, String container) throws UnsupportedFormatException {
        super(streamInfo.channels(), streamInfo.maxBlockSizeOrDefault());

        this.decoder = new FlacDecoder(streamInfo);
        this.packets = Objects.requireNonNull(packets);
        this.streamInfo = Objects.requireNonNull(streamInfo);
        this.container = Objects.requireNonNull(container);
        this.pcmInt = new int[streamInfo.channels()][streamInfo.maxBlockSizeOrDefault()];
    }

    @Override
    public AudioInfo info() {
        long total = streamInfo.totalSamples();
        return new AudioInfo(
            container, "flac", "ogg".equals(container) ? "oga" : "flac",
            streamInfo.sampleRate(),
            streamInfo.channels(),
            total == 0 ? -1 : total
        );
    }

    @Override
    protected int decodeBlock(float[][] pcm) throws IOException {
        Packet packet = packets.next();
        if (packet == null) {
            return -1;
        }
        byte[] data = packet.data();
        int samples = decoder.decodeFrame(data, 0, data.length, pcmInt, packet.finished());

        // It's a power of two, so it's exact.
        float factor = 1.0f / (1 << (streamInfo.bitsPerSample() - 1));
        for (int ch = 0; ch < pcm.length; ch++) {
            int[] src = pcmInt[ch];
            float[] dst = pcm[ch];
            for (int i = 0; i < samples; i++) {
                dst[i] = src[i] * factor;
            }
        }
        return samples;
    }

    @Override
    public void close() throws IOException {
        packets.close();
    }
}
