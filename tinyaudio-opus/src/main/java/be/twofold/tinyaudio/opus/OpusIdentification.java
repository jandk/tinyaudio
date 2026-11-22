package be.twofold.tinyaudio.opus;

import be.twofold.tinyaudio.core.AudioFormatException;
import be.twofold.tinyaudio.core.internal.IOUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

final class OpusIdentification {
    private static final int BYTES = 19;

    private final int channels;
    private final int preSkip;
    private final long inputSampleRate;
    private final short outputGain;
    private final int mappingFamily;
    private final int streamCount;
    private final int coupledCount;
    private final int[] mapping;

    private OpusIdentification(
        int channels,
        int preSkip,
        long inputSampleRate,
        short outputGain,
        int mappingFamily,
        int streamCount,
        int coupledCount,
        int[] mapping
    ) {
        this.channels = channels;
        this.preSkip = preSkip;
        this.inputSampleRate = inputSampleRate;
        this.outputGain = outputGain;
        this.mappingFamily = mappingFamily;
        this.streamCount = streamCount;
        this.coupledCount = coupledCount;
        this.mapping = mapping;
    }

    static OpusIdentification read(ByteBuffer buffer) throws AudioFormatException {
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        if (buffer.remaining() < BYTES || !IOUtils.matches(buffer, OpusHeader.MAGIC)) {
            throw new AudioFormatException("Invalid OpusHead packet");
        }
        int version = Byte.toUnsignedInt(buffer.get());
        if (version >> 4 != 0) {
            throw new AudioFormatException("Unsupported Opus version: " + version);
        }

        int channels = Byte.toUnsignedInt(buffer.get());
        int preSkip = Short.toUnsignedInt(buffer.getShort());
        long inputSampleRate = Integer.toUnsignedLong(buffer.getInt());
        short outputGain = buffer.getShort();
        int mappingFamily = Byte.toUnsignedInt(buffer.get());

        switch (mappingFamily) {
            case 0:
                checkChannels(mappingFamily, channels, 2);
                break;
            case 1:
                checkChannels(mappingFamily, channels, 8);
                break;
            case 255:
                checkChannels(mappingFamily, channels, 255);
                break;
            default:
                throw new AudioFormatException("Unsupported mapping family: " + mappingFamily);
        }

        // Family 0 has no mapping table, so describe it as one stream with an identity mapping
        int streamCount = 1;
        int coupledCount = channels - 1;
        int[] mapping = channels == 1 ? new int[]{0} : new int[]{0, 1};
        if (mappingFamily != 0) {
            if (buffer.remaining() < 2 + channels) {
                throw new AudioFormatException("Truncated channel mapping table");
            }
            streamCount = Byte.toUnsignedInt(buffer.get());
            coupledCount = Byte.toUnsignedInt(buffer.get());
            byte[] rawMapping = IOUtils.getBytes(buffer, channels);

            mapping = new int[channels];
            for (int i = 0; i < mapping.length; i++) {
                mapping[i] = Byte.toUnsignedInt(rawMapping[i]);
            }
        }

        int streams = streamCount + coupledCount;
        if (streamCount == 0 || coupledCount > streamCount || streams > 255) {
            throw new AudioFormatException("Invalid stream counts: " + streamCount + "/" + coupledCount);
        }
        for (int channel = 0; channel < channels; channel++) {
            int index = mapping[channel];
            if (index != 255 && index >= streams) { // 255 = silent channel
                throw new AudioFormatException("Invalid channel mapping index: " + index);
            }
        }

        return new OpusIdentification(
            channels,
            preSkip,
            inputSampleRate,
            outputGain,
            mappingFamily,
            streamCount,
            coupledCount,
            mapping
        );
    }

    private static void checkChannels(int mappingFamily, int channels, int max) throws AudioFormatException {
        if (channels < 1 || channels > max) {
            throw new AudioFormatException("Mapping family " + mappingFamily + " requires 1 to " + max + " channels, got " + channels);
        }
    }

    int channels() {
        return channels;
    }

    int preSkip() {
        return preSkip;
    }

    long inputSampleRate() {
        return inputSampleRate;
    }

    float outputGain() {
        return outputGain * (1.0f / 256.0f);
    }

    float outputGainLinear() {
        return (float) Math.pow(10.0, outputGain / (20.0 * 256));
    }

    int mappingFamily() {
        return mappingFamily;
    }

    int streamCount() {
        return streamCount;
    }

    int coupledCount() {
        return coupledCount;
    }

    int[] mapping() {
        return mapping;
    }

    @Override
    public String toString() {
        return "OpusIdentification{" +
            "channels=" + channels + ", " +
            "preSkip=" + preSkip + ", " +
            "inputSampleRate=" + inputSampleRate + ", " +
            "outputGain=" + outputGain + ", " +
            "mappingFamily=" + mappingFamily + ", " +
            "streamCount=" + streamCount + ", " +
            "coupledCount=" + coupledCount + ", " +
            "mapping=" + Arrays.toString(mapping) +
            "}";
    }
}
