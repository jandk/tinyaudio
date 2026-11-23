package be.twofold.tinyaudio.core.internal;

import be.twofold.tinyaudio.core.AudioDecoder;

import java.io.IOException;

public abstract class BlockAudioDecoder implements AudioDecoder {
    private final float[][] pcmFloat;
    private int position;
    private int available;

    protected BlockAudioDecoder(int channels, int blockSize) {
        pcmFloat = new float[channels][blockSize];
    }

    /**
     * Decodes one block into pcm, starting at index 0. Returns the frame count, or -1 at EOF.
     */
    protected abstract int decodeBlock(float[][] pcm) throws IOException;

    @Override
    public int read(short[][] pcm, int off, int len) throws IOException {
        if (len == 0) {
            return 0;
        }
        if (!fill()) {
            return -1;
        }

        int count = Math.min(len, available - position);
        for (int ch = 0; ch < pcmFloat.length; ch++) {
            float[] src = pcmFloat[ch];
            short[] dst = pcm[ch];
            for (int i = 0; i < count; i++) {
                dst[off + i] = toShort(src[position + i]);
            }
        }
        position += count;
        return count;
    }

    @Override
    public int read(float[][] pcm, int off, int len) throws IOException {
        if (len == 0) {
            return 0;
        }
        if (!fill()) {
            return -1;
        }

        int count = Math.min(len, available - position);
        for (int ch = 0; ch < pcmFloat.length; ch++) {
            System.arraycopy(pcmFloat[ch], position, pcm[ch], off, count);
        }
        position += count;
        return count;
    }

    protected short toShort(float sample) {
        return (short) Math.rint(MathUtils.clamp(sample * 32768.0f, -32768.0f, +32767.0f));
    }

    private boolean fill() throws IOException {
        while (position == available) {
            int frames = decodeBlock(pcmFloat);
            if (frames < 0) {
                return false;
            }
            position = 0;
            available = frames;
        }
        return true;
    }
}
