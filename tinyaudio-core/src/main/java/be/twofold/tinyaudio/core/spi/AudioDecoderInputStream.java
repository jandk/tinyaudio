package be.twofold.tinyaudio.core.spi;

import be.twofold.tinyaudio.core.AudioDecoder;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.Objects;

public final class AudioDecoderInputStream extends InputStream {
    private static final VarHandle VH_SHORT_LE = MethodHandles
        .byteArrayViewVarHandle(short[].class, ByteOrder.LITTLE_ENDIAN);
    private static final int BUFFER_FRAMES = 4 * 1024;

    private final AudioDecoder decoder;
    private final short[][] pcm;

    public AudioDecoderInputStream(AudioDecoder decoder) {
        this.decoder = Objects.requireNonNull(decoder);
        this.pcm = new short[decoder.info().channels()][BUFFER_FRAMES];
    }

    @Override
    public int read() throws IOException {
        throw new IOException("Cannot read single bytes from a frame-based stream");
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        Objects.checkFromIndexSize(off, len, b.length);
        if (len == 0) {
            return 0;
        }

        int frameSize = pcm.length * Short.BYTES;
        if (len < frameSize) {
            throw new IOException("Read smaller than one frame: " + len);
        }

        int frames = decoder.read(pcm, 0, Math.min(len / frameSize, BUFFER_FRAMES));
        if (frames < 0) {
            return -1;
        }

        for (int ch = 0; ch < pcm.length; ch++) {
            short[] samples = pcm[ch];
            for (int i = 0, o = off + ch * Short.BYTES; i < frames; i++, o += frameSize) {
                VH_SHORT_LE.set(b, o, samples[i]);
            }
        }
        return frames * frameSize;
    }

    @Override
    public void close() throws IOException {
        decoder.close();
    }
}
