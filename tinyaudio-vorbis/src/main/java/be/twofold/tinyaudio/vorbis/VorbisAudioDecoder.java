package be.twofold.tinyaudio.vorbis;

import be.twofold.tinyaudio.core.AudioInfo;
import be.twofold.tinyaudio.core.BlockAudioDecoder;
import be.twofold.tinyaudio.core.Packet;
import be.twofold.tinyaudio.core.PacketSource;
import be.twofold.tinyaudio.core.internal.PcmUtils;
import be.twofold.tinyaudio.vorbis.internal.Identification;
import be.twofold.tinyaudio.vorbis.internal.Setup;
import be.twofold.tinyaudio.vorbis.internal.VorbisDecoder;

import java.io.IOException;
import java.util.Objects;

public final class VorbisAudioDecoder extends BlockAudioDecoder {
    private final PacketSource packets;
    private final Identification identification;
    private final VorbisDecoder decoder;
    private final float[][] pcmFloat;

    private long produced;

    VorbisAudioDecoder(PacketSource packets, Identification identification, Setup setup) {
        super(identification.audioChannels(), Math.max(identification.blockSize0(), identification.blockSize1()));
        this.packets = Objects.requireNonNull(packets);
        this.identification = Objects.requireNonNull(identification);
        this.decoder = new VorbisDecoder(identification, setup);
        this.pcmFloat = new float[identification.audioChannels()][Math.max(identification.blockSize0(),
            identification.blockSize1())];
    }

    @Override
    public AudioInfo info() {
        return new AudioInfo(
            "ogg", "vorbis", "ogg",
            identification.audioSampleRate(),
            identification.audioChannels(),
            -1 // vorbis has it at the end of the stream
        );
    }

    @Override
    protected int decodeBlock(short[][] pcm) throws IOException {
        Packet packet = packets.next();
        if (packet == null) {
            return -1;
        }

        byte[] data = packet.getData();
        int frames = decoder.decodeBlock(data, 0, data.length, pcmFloat);

        if (packet.isFinished() && packet.getPosition() >= 0) {
            frames = (int) Math.max(0, Math.min(frames, packet.getPosition() - produced));
        }
        produced += frames;

        for (int ch = 0; ch < identification.audioChannels(); ch++) {
            PcmUtils.floatToPcm(pcmFloat[ch], 0, 1, pcm[ch], 0, frames);
        }
        return frames;
    }

    @Override
    public void close() throws IOException {
        packets.close();
    }
}
