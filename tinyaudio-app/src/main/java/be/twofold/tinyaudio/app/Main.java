package be.twofold.tinyaudio.app;

import javax.sound.sampled.*;
import java.io.File;

public class Main {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: java tinyaudio-app.jar <path>...");
            return;
        }

        for (String arg : args) {
            try {
                play(arg);
            } catch (Exception e) {
                System.err.println("Failed to play " + arg);
                e.printStackTrace();
            }
        }
    }

    private static void play(String path) throws Exception {
        File file = new File(path);
        AudioFileFormat fileFormat = AudioSystem.getAudioFileFormat(file);
        AudioFormat format = fileFormat.getFormat();
        int frames = fileFormat.getFrameLength();
        System.out.println("File:      " + file);
        System.out.println("Container: " + fileFormat.getType());
        System.out.println("Codec:     " + fileFormat.getProperty("codec"));
        System.out.println("Format:    " + format);
        System.out.println("Duration:  " + (frames == AudioSystem.NOT_SPECIFIED
            ? "unknown"
            : String.format("%.1f s (%d frames)", frames / format.getSampleRate(), frames)));

        try (AudioInputStream raw = AudioSystem.getAudioInputStream(file);
             AudioInputStream pcm = AudioSystem.getAudioInputStream(AudioFormat.Encoding.PCM_SIGNED, raw);
             SourceDataLine line = AudioSystem.getSourceDataLine(pcm.getFormat())) {

            line.open(pcm.getFormat());
            line.start();
            pcm.transferTo(new LineOutputStream(line));
            line.drain();
        }
    }
}
