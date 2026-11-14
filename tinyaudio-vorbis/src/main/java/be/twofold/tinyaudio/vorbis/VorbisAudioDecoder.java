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

    private long granulePosition;
    private boolean synced;

    VorbisAudioDecoder(PacketSource packets, Identification identification, Setup setup) {
        super(identification.channels(), identification.blockSize1());
        this.packets = Objects.requireNonNull(packets);
        this.identification = Objects.requireNonNull(identification);
        this.decoder = new VorbisDecoder(identification, setup);
        this.pcmFloat = new float[identification.channels()][identification.blockSize1()];
    }

    @Override
    public AudioInfo info() {
        return new AudioInfo(
            "ogg", "vorbis", "ogg",
            identification.sampleRate(),
            identification.channels(),
            -1 // vorbis has it at the end of the stream
        );
    }

    // TODO: Decode float for all?
    int decodeFloat(float[][] pcm) throws IOException {
        Packet packet = packets.next();
        if (packet == null) {
            return -1;
        }

        byte[] data = packet.getData();
        int frames = decoder.decodeBlock(data, 0, data.length, pcm);

        granulePosition += frames;
        long position = packet.getPosition();
        if (position == -1) {
            return frames;
        }

        int extra = (int) Math.max(0, Math.min(granulePosition - position, frames));
        granulePosition = position;
        if (packet.isFinished()) {
            return frames - extra;
        }
        if (synced) {
            return frames;
        }
        synced = true;
        for (int ch = 0; ch < identification.channels(); ch++) {
            System.arraycopy(pcm[ch], extra, pcm[ch], 0, frames - extra);
        }
        return frames - extra;
    }

    @Override
    protected int decodeBlock(short[][] pcm) throws IOException {
        int frames = decodeFloat(pcmFloat);
        for (int ch = 0; ch < identification.channels(); ch++) {
            PcmUtils.floatToPcm(pcmFloat[ch], 0, 1, pcm[ch], 0, Math.max(frames, 0));
        }
        return frames;
    }

    @Override
    public void close() throws IOException {
        packets.close();
    }
}
