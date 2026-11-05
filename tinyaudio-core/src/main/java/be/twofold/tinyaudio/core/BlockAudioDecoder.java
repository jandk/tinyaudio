package be.twofold.tinyaudio.core;

import java.io.IOException;

public abstract class BlockAudioDecoder implements AudioDecoder {
    private final short[][] buffer;   // new short[channels][blockSize]
    private int position;
    private int available;

    protected BlockAudioDecoder(int channels, int blockSize) {
        buffer = new short[channels][blockSize];
    }

    /**
     * Decodes one block into pcm. Returns the frame count, or -1 at EOF.
     */
    protected abstract int decodeBlock(short[][] pcm) throws IOException;

    @Override
    public int read(short[][] pcm, int offset, int length) throws IOException {
        if (length == 0) {
            return 0;
        }
        while (position == available) {
            int frames = decodeBlock(buffer);
            if (frames < 0) {
                return -1;
            }
            position = 0;
            available = frames;
        }

        int count = Math.min(length, available - position);
        for (int ch = 0; ch < buffer.length; ch++) {
            System.arraycopy(buffer[ch], position, pcm[ch], offset, count);
        }
        position += count;
        return count;
    }
}
