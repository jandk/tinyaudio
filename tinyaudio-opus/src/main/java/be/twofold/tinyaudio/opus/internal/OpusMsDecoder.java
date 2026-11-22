package be.twofold.tinyaudio.opus.internal;

import be.twofold.tinyaudio.core.AudioFormatException;
import be.twofold.tinyaudio.core.UnsupportedFormatException;
import be.twofold.tinyaudio.opus.celt.CeltMdct;

import java.util.Arrays;

public final class OpusMsDecoder {
    private final float[][] buf = new float[2][5760];
    private final int[] frames = new int[49];
    private final int[] size = new int[48];
    private final int Fs = 48000;

    private final int channels;
    private final int nb_coupled_streams;
    private final int nb_streams;
    private final int[] mapping;
    private final OpusDecoder[] decoders;

    public OpusMsDecoder(int channels, int nb_streams, int nb_coupled_streams, int[] mapping) {
        this.channels = channels;
        this.nb_streams = nb_streams;
        this.nb_coupled_streams = nb_coupled_streams;
        this.mapping = mapping;

        CeltMdct mdct = new CeltMdct();
        this.decoders = new OpusDecoder[nb_streams];
        for (int s = 0; s < nb_streams; s++) {
            decoders[s] = new OpusDecoder(s < nb_coupled_streams ? 2 : 1, mdct);
        }
    }

    public int opus_multistream_decode_native(byte[] data, int dataOff, int len, float[][] pcm, int pcmOff, int frame_size) throws AudioFormatException {
        if (frame_size <= 0) {
            throw new AudioFormatException("OPUS_BAD_ARG");
        }
        // buf holds at most 120 ms per stream
        frame_size = Math.min(frame_size, Fs / 25 * 3);
        if (len < 0) {
            throw new AudioFormatException("OPUS_BAD_ARG");
        }
        if (len < 2 * nb_streams - 1) {
            throw new AudioFormatException("OPUS_INVALID_PACKET");
        }
        int samples = opus_multistream_packet_validate(data, dataOff, len);
        if (samples > frame_size) {
            throw new AudioFormatException("OPUS_BUFFER_TOO_SMALL");
        }
        // Validation guarantees every stream fits and yields the same number of samples
        for (int s = 0; s < nb_streams; s++) {
            OpusDecoder dec = decoders[s];
            dec.opus_decode_native(data, dataOff, len, buf, 0, frame_size, s != nb_streams - 1);
            int packet_offset = dec.packet_offset();
            dataOff += packet_offset;
            len -= packet_offset;
            if (s < nb_coupled_streams) {
                // Copy "left" audio to the channel(s) where it belongs
                for (int chan = get_left_channel(s, -1); chan != -1; chan = get_left_channel(s, chan)) {
                    System.arraycopy(buf[0], 0, pcm[chan], pcmOff, samples);
                }
                // Copy "right" audio to the channel(s) where it belongs
                for (int chan = get_right_channel(s, -1); chan != -1; chan = get_right_channel(s, chan)) {
                    System.arraycopy(buf[1], 0, pcm[chan], pcmOff, samples);
                }
            } else {
                // Copy audio to the channel(s) where it belongs
                for (int chan = get_mono_channel(s, -1); chan != -1; chan = get_mono_channel(s, chan)) {
                    System.arraycopy(buf[0], 0, pcm[chan], pcmOff, samples);
                }
            }
        }
        // Handle muted channels
        for (int c = 0; c < channels; c++) {
            if (mapping[c] == 255) {
                Arrays.fill(pcm[c], pcmOff, pcmOff + samples, 0.0f);
            }
        }
        return samples;
    }

    /**
     * Parse every stream of a multistream packet before decoding any of them, so
     * a bad packet leaves no stream half-decoded. Returns the samples per stream.
     */
    private int opus_multistream_packet_validate(byte[] data, int dataOff, int len) throws AudioFormatException {
        int samples = 0;
        for (int s = 0; s < nb_streams; s++) {
            OpusDecoder decoder = decoders[s];
            if (len <= 0) {
                throw new AudioFormatException("OPUS_INVALID_PACKET");
            }

            // Only CELT packets can be decoded
            if ((data[dataOff] & 0x80) == 0) {
                throw new UnsupportedFormatException("SILK and Hybrid are not supported");
            }
            int count = decoder.opus_packet_parse_impl(data, dataOff, len, s != nb_streams - 1, frames, size);
            int packet_offset = decoder.packet_offset();
            int tmp_samples = count * decoder.opus_packet_get_samples_per_frame(data[dataOff], Fs);
            if (s != 0 && samples != tmp_samples) {
                throw new AudioFormatException("OPUS_INVALID_PACKET");
            }
            samples = tmp_samples;
            dataOff += packet_offset;
            len -= packet_offset;
        }
        return samples;
    }

    private int get_left_channel(int stream_id, int prev) {
        for (int i = (prev < 0) ? 0 : prev + 1; i < channels; i++) {
            if (mapping[i] == stream_id * 2) {
                return i;
            }
        }
        return -1;
    }

    private int get_right_channel(int stream_id, int prev) {
        for (int i = (prev < 0) ? 0 : prev + 1; i < channels; i++) {
            if (mapping[i] == stream_id * 2 + 1) {
                return i;
            }
        }
        return -1;
    }

    private int get_mono_channel(int stream_id, int prev) {
        for (int i = (prev < 0) ? 0 : prev + 1; i < channels; i++) {
            if (mapping[i] == stream_id + nb_coupled_streams) {
                return i;
            }
        }
        return -1;
    }

    public int rng() {
        int rng = 0;
        for (OpusDecoder decoder : decoders) {
            rng ^= decoder.rng();
        }
        return rng;
    }
}
