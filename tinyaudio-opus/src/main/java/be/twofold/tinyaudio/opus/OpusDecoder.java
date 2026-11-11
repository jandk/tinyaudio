package be.twofold.tinyaudio.opus;

import be.twofold.tinyaudio.core.AudioDecoder;
import be.twofold.tinyaudio.core.AudioInfo;
import be.twofold.tinyaudio.core.PacketSource;
import be.twofold.tinyaudio.opus.celt.CeltDecoder;
import be.twofold.tinyaudio.opus.celt.CeltMode;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

public final class OpusDecoder implements AudioDecoder {
    private static final int OPUS_BANDWIDTH_NARROWBAND = 1101;    // < 4kHz bandpass @hideinitializer
    private static final int OPUS_BANDWIDTH_MEDIUMBAND = 1102;    // < 6kHz bandpass @hideinitializer
    private static final int OPUS_BANDWIDTH_WIDEBAND = 1103;      // < 8kHz bandpass @hideinitializer
    private static final int OPUS_BANDWIDTH_SUPERWIDEBAND = 1104; // <12kHz bandpass @hideinitializer
    private static final int OPUS_BANDWIDTH_FULLBAND = 1105;      // <20kHz bandpass @hideinitializer

    private final PacketSource packets;
    private final OpusIdentification identification;

    private final int[] frames = new int[49];
    private final int[] size = new int[48];

    private final CeltDecoder celt_dec;
    private final int channels;
    private final int Fs = 48000;

    /* Everything beyond this point gets cleared on a reset */
    private int stream_channels;
    private int bandwidth;
    private int frame_size;
    int rangeFinal;


    OpusDecoder(PacketSource packets, OpusIdentification identification) {
        this.packets = Objects.requireNonNull(packets);
        this.identification = Objects.requireNonNull(identification);

        this.celt_dec = new CeltDecoder(CeltMode.mode48000_960_120, identification.channels());
        this.channels = identification.channels();
    }

    @Override
    public AudioInfo info() {
        return new AudioInfo(
            "ogg", "opus", "opus",
            48000, // Fixed
            identification.channels(),
            -1 // vorbis has it at the end of the stream
        );
    }

    @Override
    public int read(short[][] pcm, int offset, int length) throws IOException {
        return 0;
    }

    @Override
    public void close() throws IOException {
        packets.close();
    }

    private int opus_decode_frame(byte[] data, int dataOff, int len, float[] pcm, int pcmOff) {
        /* A frame of 0 or 1 bytes (1 or 2 including the ToC) is lost: output silence */
        if (len <= 1) {
            Arrays.fill(pcm, pcmOff, pcmOff + frame_size * channels, 0);
            rangeFinal = 0;
            return frame_size;
        }

        int endband = 21;
        switch (bandwidth) {
            case OPUS_BANDWIDTH_NARROWBAND:
                endband = 13;
                break;
            case OPUS_BANDWIDTH_MEDIUMBAND:
            case OPUS_BANDWIDTH_WIDEBAND:
                endband = 17;
                break;
            case OPUS_BANDWIDTH_SUPERWIDEBAND:
                endband = 19;
                break;
            case OPUS_BANDWIDTH_FULLBAND:
                endband = 21;
                break;
        }

        celt_dec.celt_decode_with_ec(data, dataOff, len, pcm, pcmOff, frame_size, stream_channels, endband);
        rangeFinal = celt_dec.rng();
        return frame_size;
    }

    private int opus_packet_parse_impl(byte[] data, int dataOff, int len, boolean self_delimited, int[] frames, int[] size) throws OpusException {
        if (len == 0) {
            throw new OpusException("OPUS_INVALID_PACKET");
        }

        byte toc = data[dataOff++];
        len--;
        int count;
        boolean cbr = false;
        int last_size = len;
        int pad = 0;
        switch (toc & 0x3) {
            case 0: { // One frame
                count = 1;
                break;
            }
            case 1: { // Two CBR frames
                count = 2;
                cbr = true;
                if (!self_delimited) {
                    if ((len & 0x1) != 0) {
                        throw new OpusException("OPUS_INVALID_PACKET");
                    }
                    size[0] = last_size = len / 2;
                }
                break;
            }
            case 2: { // Two VBR frames
                count = 2;
                size[0] = parse_size(data, dataOff, len);
                int bytes = (size[0] < 252) ? 1 : 2;
                len -= bytes;
                if ((size[0] < 0) || (size[0] > len)) {
                    throw new OpusException("OPUS_INVALID_PACKET");
                }
                dataOff += bytes;
                last_size = len - size[0];
                break;
            }
            // case 3:
            default: { // Multiple CBR/VBR frames (from 0 to 120 ms)
                if (len < 1) {
                    throw new OpusException("OPUS_INVALID_PACKET");
                }
                /* Number of frames encoded in bits 0 to 5 */
                byte ch = data[dataOff++];
                count = ch & 0x3F;
                int framesize = opus_packet_get_samples_per_frame(toc, 48000);
                if ((count == 0) || ((framesize * count) > 5760)) {
                    throw new OpusException("OPUS_INVALID_PACKET");
                }
                len--;
                /* Padding flag is bit 6 */
                if ((ch & 0x40) != 0) {
                    int p, tmp;
                    do {
                        if (len <= 0) {
                            throw new OpusException("OPUS_INVALID_PACKET");
                        }
                        p = Byte.toUnsignedInt(data[dataOff++]);
                        len--;
                        tmp = (p == 255) ? 254 : p;
                        len -= tmp;
                        pad += tmp;
                    } while (p == 255);
                }
                if (len < 0) {
                    throw new OpusException("OPUS_INVALID_PACKET");
                }
                /* VBR flag is bit 7 */
                cbr = (ch & 0x80) == 0;
                if (!cbr) {
                    /* VBR case */
                    last_size = len;
                    for (int i = 0; i < (count - 1); i++) {
                        size[i] = parse_size(data, dataOff, len);
                        int bytes = (size[i] < 252) ? 1 : 2;
                        len -= bytes;
                        if ((size[i] < 0) || (size[i] > len)) {
                            throw new OpusException("OPUS_INVALID_PACKET");
                        }
                        dataOff += bytes;
                        last_size -= bytes + size[i];
                    }
                    if (last_size < 0) {
                        throw new OpusException("OPUS_INVALID_PACKET");
                    }
                } else if (!self_delimited) {
                    /* CBR case */
                    last_size = len / count;
                    if ((last_size * count) != len) {
                        throw new OpusException("OPUS_INVALID_PACKET");
                    }
                    for (int i = 0; i < (count - 1); i++) {
                        size[i] = last_size;
                    }
                }
                break;
            }
        }

        /* Self-delimited framing has an extra size for the last frame. */
        if (self_delimited) {
            size[count - 1] = parse_size(data, dataOff, len);
            int bytes = (size[count - 1] < 252) ? 1 : 2;
            len -= bytes;
            if ((size[count - 1] < 0) || (size[count - 1] > len)) {
                throw new OpusException("OPUS_INVALID_PACKET");
            }
            dataOff += bytes;
            /* For CBR packets, apply the size to all the frames. */
            if (cbr) {
                if ((size[count - 1] * count) > len) {
                    throw new OpusException("OPUS_INVALID_PACKET");
                }
                for (int i = 0; i < (count - 1); i++) {
                    size[i] = size[count - 1];
                }
            } else if ((bytes + size[count - 1]) > last_size) {
                throw new OpusException("OPUS_INVALID_PACKET");
            }
        } else {
            if (last_size > 1275) {
                throw new OpusException("OPUS_INVALID_PACKET");
            }
            size[count - 1] = last_size;
        }

        for (int i = 0; i < count; i++) {
            frames[i] = dataOff;
            dataOff += size[i];
        }
        frames[count] = dataOff + pad;
        return count;
    }

    int opus_decode_native(byte[] data, int dataOff, int len, float[] pcm, int pcmOff, int frame_size, boolean self_delimited) throws OpusException {
        byte toc = data[dataOff];
        if ((toc & 0x80) == 0) {
            throw new OpusException("OPUS_INVALID_PACKET");
        }

        int packet_frame_size = opus_packet_get_samples_per_frame(toc, Fs);
        int count = opus_packet_parse_impl(data, dataOff, len, self_delimited, frames, size);
        if (count * packet_frame_size > frame_size) {
            throw new OpusException("OPUS_BUFFER_TOO_SMALL");
        }

        /* The packet is valid: only now update the decoder state */
        bandwidth = opus_packet_get_bandwidth(toc);
        this.frame_size = packet_frame_size;
        this.stream_channels = opus_packet_get_nb_channels(toc);

        int nb_samples = 0;
        for (int i = 0; i < count; i++) {
            int ret = opus_decode_frame(data, frames[i], size[i], pcm, pcmOff);
            pcmOff += ret * channels;
            nb_samples += ret;
        }
        return nb_samples;
    }

    private int opus_packet_get_bandwidth(byte toc) {
        int bandwidth = OPUS_BANDWIDTH_MEDIUMBAND + ((toc >> 5) & 0x3);
        return bandwidth == OPUS_BANDWIDTH_MEDIUMBAND ? OPUS_BANDWIDTH_NARROWBAND : bandwidth;
    }

    private int opus_packet_get_samples_per_frame(byte toc, int Fs) {
        return (Fs << ((toc >> 3) & 0x3)) / 400;
    }

    private int opus_packet_get_nb_channels(byte toc) {
        return (toc & 0x4) != 0 ? 2 : 1;
    }

    private int parse_size(byte[] data, int dataOff, int len) {
        if (len < 1) {
            return -1;
        }
        int d0 = Byte.toUnsignedInt(data[dataOff]);
        if (d0 < 252) {
            return d0;
        }
        if (len < 2) {
            return -1;
        }
        return (4 * Byte.toUnsignedInt(data[dataOff + 1])) + d0;
    }
}
