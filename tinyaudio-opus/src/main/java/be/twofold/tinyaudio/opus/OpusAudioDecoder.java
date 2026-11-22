package be.twofold.tinyaudio.opus;

import be.twofold.tinyaudio.core.AudioInfo;
import be.twofold.tinyaudio.core.internal.*;
import be.twofold.tinyaudio.ogg.internal.OggTrim;
import be.twofold.tinyaudio.opus.internal.OpusMsDecoder;

import java.io.IOException;
import java.util.Objects;

final class OpusAudioDecoder extends BlockAudioDecoder {
    private static final int MAX_BLOCK_SIZE = 5760; // 120ms at 48kHz
    private final PacketSource packets;
    private final OpusIdentification identification;
    private final OpusMsDecoder decoder;
    private final float[][] pcmFloat;
    private final float[] softclip_mem;
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
        this.softclip_mem = new float[identification.channels()];
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

        softClip(start, count);
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

    // opus_pcm_soft_clip_impl
    private void softClip(int off, int len) {
        if (len < 1) {
            return;
        }
        int end = off + len;

        for (int c = 0; c < pcmFloat.length; c++) {
            float[] x = pcmFloat[c];

            // Clamp to [-2, +2], this is where the soft clipping works
            for (int i = off; i < end; i++) {
                x[i] = MathUtils.clamp(x[i], -2.0f, +2.0f);
            }

            // Continue from the previous block until we reach a zero crossing.
            float a = softclip_mem[c];
            for (int i = off; i < end && x[i] * a < 0; i++) {
                x[i] = x[i] + a * x[i] * x[i];
            }

            float x0 = x[off];
            int curr = off;
            while (curr < end) {
                // Find the next clipping sample
                int i = curr;
                while (i < end && Math.abs(x[i]) <= 1.0f) {
                    i++;
                }
                if (i == end) {
                    a = 0.0f;
                    break;
                }

                // Find the zero crossings before and after clipping
                int start = i;
                while (start > off && x[i] * x[(start - 1)] >= 0.0f) {
                    start--;
                }
                int stop = i;
                int peak = i;
                float maxval = Math.abs(x[i]);
                while (stop < end && x[i] * x[stop] >= 0.0f) {
                    if (Math.abs(x[stop]) > maxval) {
                        maxval = Math.abs(x[stop]);
                        peak = stop;
                    }
                    stop++;
                }

                // Detect the special case where we clip before the first zero crossing
                boolean special = start == off && x[i] * x[off] >= 0.0f;

                // a such that maxval + a * maxval^2 = 1, boosted by 2^-22 to stay within +/-1 under -ffast-math
                a = (maxval - 1.0f) / (maxval * maxval);
                a += a * 2.4e-7f;
                if (x[i] > 0) {
                    a = -a;
                }
                for (int j = start; j < stop; j++) {
                    x[j] = x[j] + a * x[j] * x[j];
                }

                // Add a linear ramp from the first sample to the signal peak.
                // This avoids a discontinuity at the beginning of the frame.
                if (special && peak - off >= 2) {
                    float offset = x0 - x[off];
                    float delta = offset / (peak - off);
                    for (int j = curr; j < peak; j++) {
                        offset -= delta;
                        x[j] = MathUtils.clamp(x[j] + offset, -1.0f, +1.0f);
                    }
                }
                curr = stop;
            }
            softclip_mem[c] = a;
        }
    }
}
