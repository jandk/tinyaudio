package be.twofold.tinyflac.spi;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.spi.FormatConversionProvider;

public final class FlacFormatConversionProvider extends FormatConversionProvider {

    @Override
    public AudioFormat.Encoding[] getSourceEncodings() {
        return new AudioFormat.Encoding[]{FlacAudioFileReader.ENCODING};
    }

    @Override
    public AudioFormat.Encoding[] getTargetEncodings() {
        return new AudioFormat.Encoding[]{AudioFormat.Encoding.PCM_SIGNED};
    }

    @Override
    public AudioFormat.Encoding[] getTargetEncodings(AudioFormat sourceFormat) {
        if (sourceFormat.getEncoding().equals(FlacAudioFileReader.ENCODING)) {
            return new AudioFormat.Encoding[]{AudioFormat.Encoding.PCM_SIGNED};
        }
        return new AudioFormat.Encoding[0];
    }

    @Override
    public AudioFormat[] getTargetFormats(AudioFormat.Encoding targetEncoding, AudioFormat sourceFormat) {
        if (!isConversionSupported(targetEncoding, sourceFormat)) {
            return new AudioFormat[0];
        }

        if (targetEncoding.equals(AudioFormat.Encoding.PCM_SIGNED)) {
            AudioFormat pcmFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                sourceFormat.getSampleRate(),
                sourceFormat.getSampleSizeInBits(),
                sourceFormat.getChannels(),
                (sourceFormat.getSampleSizeInBits() + 7) / 8 * sourceFormat.getChannels(),
                sourceFormat.getSampleRate(),
                false
            );

            return new AudioFormat[]{pcmFormat};
        }
        return new AudioFormat[0];
    }

    @Override
    public AudioInputStream getAudioInputStream(AudioFormat.Encoding targetEncoding, AudioInputStream sourceStream) {
        AudioFormat sourceFormat = sourceStream.getFormat();
        AudioFormat[] targetFormats = getTargetFormats(targetEncoding, sourceFormat);

        if (targetFormats.length == 0) {
            throw new IllegalArgumentException("Cannot convert " + sourceFormat + " to " + targetEncoding);
        }

        return getAudioInputStream(targetFormats[0], sourceStream);
    }

    @Override
    public AudioInputStream getAudioInputStream(AudioFormat targetFormat, AudioInputStream sourceStream) {
        AudioFormat sourceFormat = sourceStream.getFormat();
        if (sourceFormat.matches(targetFormat)) {
            return sourceStream;
        }

        if (!isConversionSupported(targetFormat, sourceFormat)) {
            throw new IllegalArgumentException("Unsupported conversion: " + targetFormat + " to " + sourceFormat);
        }

        if (sourceFormat.getEncoding().equals(FlacAudioFileReader.ENCODING) && targetFormat.getEncoding().equals(AudioFormat.Encoding.PCM_SIGNED)) {
            return new FlacAudioInputStream(sourceStream, targetFormat);
        }

        throw new IllegalArgumentException("Unsupported conversion: " + targetFormat + " to " + sourceFormat);
    }
}
