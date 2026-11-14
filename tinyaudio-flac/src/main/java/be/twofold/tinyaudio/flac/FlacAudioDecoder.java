package be.twofold.tinyaudio.flac;

import be.twofold.tinyaudio.core.AudioInfo;
import be.twofold.tinyaudio.core.BlockAudioDecoder;
import be.twofold.tinyaudio.core.Packet;
import be.twofold.tinyaudio.core.PacketSource;
import be.twofold.tinyaudio.flac.internal.FlacDecoder;
import be.twofold.tinyaudio.flac.metadata.StreamInfo;

import java.io.IOException;
import java.util.Objects;

public final class FlacAudioDecoder extends BlockAudioDecoder {
    private final FlacDecoder decoder;
    private final PacketSource packets;
    private final StreamInfo streamInfo;
    private final String container;
    private final int[][] pcmInt;


    FlacAudioDecoder(PacketSource packets, StreamInfo streamInfo, String container) {
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
    protected int decodeBlock(short[][] pcm) throws IOException {
        int samples = decodeInt(pcmInt);

        int shift = streamInfo.bitsPerSample() - 16;
        for (int ch = 0; ch < pcm.length; ch++) {
            int[] src = pcmInt[ch];
            short[] dst = pcm[ch];
            if (shift >= 0) {
                for (int i = 0; i < samples; i++) {
                    dst[i] = (short) (src[i] >> shift);
                }
            } else {
                for (int i = 0; i < samples; i++) {
                    dst[i] = (short) (src[i] << -shift);
                }
            }
        }

        return samples;
    }

    int decodeInt(int[][] pcm) throws IOException {
        Packet packet = packets.next();
        if (packet == null) {
            return -1;
        }
        byte[] data = packet.getData();
        return decoder.decodeFrame(data, 0, data.length, packet.isFinished(), pcm);
    }

    @Override
    public void close() throws IOException {
        packets.close();
    }

}
