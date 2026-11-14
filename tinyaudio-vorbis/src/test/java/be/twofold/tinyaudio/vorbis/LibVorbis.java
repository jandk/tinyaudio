package be.twofold.tinyaudio.vorbis;

import com.sun.jna.*;
import com.sun.jna.ptr.PointerByReference;

import java.net.URISyntaxException;
import java.nio.file.Path;

public interface LibVorbis extends Library {
    static LibVorbis load() {
        try {
            Path path = Path.of(LibVorbis.class.getResource("/libvorbis-0.dll").toURI());
            return Native.load(path.toString(), LibVorbis.class);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    int vorbis_block_clear(Pointer vb);

    int vorbis_block_init(Pointer vd, Pointer vb);

    int vorbis_synthesis(Pointer vb, OggPacket op);

    int vorbis_synthesis_blockin(Pointer vd, Pointer vb);

    int vorbis_synthesis_headerin(Pointer vi, Pointer vc, OggPacket op);

    int vorbis_synthesis_init(Pointer vd, Pointer vi);

    int vorbis_synthesis_pcmout(Pointer vd, PointerByReference pcm);

    int vorbis_synthesis_read(Pointer vd, int samples);

    void vorbis_comment_clear(Pointer vc);

    void vorbis_comment_init(Pointer vc);

    void vorbis_dsp_clear(Pointer vd);

    void vorbis_info_clear(Pointer vi);

    void vorbis_info_init(Pointer vi);

    @Structure.FieldOrder({"packet", "bytes", "bos", "eos", "granulePos", "packetNo"})
    class OggPacket extends Structure {
        public Pointer packet;
        public NativeLong bytes;
        public NativeLong bos;
        public NativeLong eos;
        public long granulePos;
        public long packetNo;
    }
}
