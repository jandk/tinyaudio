package be.twofold.tinyaudio.core;

import be.twofold.tinyaudio.core.internal.DecoderDetector;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Decodes an audio stream to 16-bit PCM.
 *
 * <p>Use {@link #open(Path)} or {@link #open(InputStream)} to get a decoder. The format is detected from the data,
 * not the file name, by asking each {@link AudioDecoderProvider} in turn. Providers are found with
 * {@link java.util.ServiceLoader}, so a format is supported as soon as its module is on the class or module path.
 *
 * <p>A decoder owns its stream, and closing the decoder closes the stream. Decoders are not thread-safe.
 */
public interface AudioDecoder extends Closeable {

    /**
     * Returns the properties of the stream. These are read from the header when the decoder is opened.
     *
     * @return the stream properties
     */
    AudioInfo info();

    /**
     * Reads up to {@code len} frames into planar buffers, one array per channel.
     *
     * <p>Samples are signed 16-bit. Higher bit depths are truncated, lower ones are shifted up. Channels are in the
     * codec's order: WAVE order for FLAC, Vorbis order for Vorbis and Opus.
     *
     * <p>At least one frame is read, unless {@code len} is zero or the stream has ended. Fewer frames than requested
     * may be returned, even before the end.
     *
     * @param pcm the destination, with at least {@link AudioInfo#channels()} arrays
     * @param off the offset in each array to start writing at
     * @param len the maximum number of frames to read
     * @return the number of frames read, or {@code -1} at the end of the stream
     * @throws UnsupportedFormatException if the stream uses a feature that is not supported, like SILK in Opus
     * @throws AudioFormatException       if the data is invalid
     * @throws IOException                if reading fails
     */
    int read(short[][] pcm, int off, int len) throws IOException;

    /**
     * Opens a decoder for a file. The decoder closes the file when closed.
     *
     * @param path the file to decode
     * @return a decoder, positioned at the first frame
     * @throws UnsupportedFormatException if no decoder recognizes the file, or it uses a feature that is not
     *                                    supported, like 32-bit FLAC
     * @throws AudioFormatException       if the data is invalid
     * @throws IOException                if reading fails
     */
    static AudioDecoder open(Path path) throws IOException {
        InputStream in = Files.newInputStream(path);
        try {
            return open(in);
        } catch (IOException | RuntimeException e) {
            in.close();
            throw e;
        }
    }

    /**
     * Opens a decoder for a stream, starting at its current position.
     *
     * <p>On success the decoder owns the stream, and closes it when closed. On failure the stream is left open, but
     * its position is undefined, since detection reads ahead.
     *
     * @param in the stream to decode, which is wrapped in a {@link BufferedInputStream} if it does not support mark
     * @return a decoder, positioned at the first frame
     * @throws UnsupportedFormatException if no decoder recognizes the stream, or it uses a feature that is not
     *                                    supported, like 32-bit FLAC
     * @throws AudioFormatException       if the data is invalid
     * @throws IOException                if reading fails
     */
    static AudioDecoder open(InputStream in) throws IOException {
        if (!in.markSupported()) {
            in = new BufferedInputStream(in);
        }
        in.mark(DecoderDetector.MARK_LIMIT);
        return DecoderDetector.detect(in)
            .orElseThrow(() -> new UnsupportedFormatException("No decoder found for stream"));
    }
}
