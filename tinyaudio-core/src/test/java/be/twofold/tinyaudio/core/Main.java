package be.twofold.tinyaudio.core;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
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
