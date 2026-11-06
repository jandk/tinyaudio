package be.twofold.tinyaudio.core;

import javax.sound.sampled.*;
import java.io.File;
import java.io.OutputStream;
import java.util.Objects;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Usage: java tinyflac.jar <path>");
            return;
        }
        String path = args[0];

        AudioFileFormat fileFormat = AudioSystem.getAudioFileFormat(new File(path));
        AudioFormat format = fileFormat.getFormat();
        int frames = fileFormat.getFrameLength();
        System.out.println("File:     " + path);
        System.out.println("Codec:    " + fileFormat.getType());
        System.out.println("Format:   " + format);
        System.out.println("Duration: " + (frames == AudioSystem.NOT_SPECIFIED
            ? "unknown"
            : String.format("%.1f s (%d frames)", frames / format.getSampleRate(), frames)));

        try (AudioInputStream raw = AudioSystem.getAudioInputStream(new File(path));
             AudioInputStream pcm = AudioSystem.getAudioInputStream(AudioFormat.Encoding.PCM_SIGNED, raw);
             SourceDataLine line = AudioSystem.getSourceDataLine(pcm.getFormat())) {

            line.open(pcm.getFormat());
            line.start();
            pcm.transferTo(new LineOutputStream(line));
            line.drain();
        }
    }

    private static final class LineOutputStream extends OutputStream {
        private final SourceDataLine line;

        public LineOutputStream(SourceDataLine line) {
            this.line = Objects.requireNonNull(line);
        }

        @Override
        public void write(int b) {
            write(new byte[]{(byte) b}, 0, 1);
        }

        @Override
        public void write(byte[] b, int off, int len) {
            line.write(b, off, len);
        }
    }
}
