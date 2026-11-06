package be.twofold.tinyaudio.flac.metadata;

import be.twofold.tinyaudio.core.internal.VorbisComment;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

public final class CommentBlock implements MetadataBlock {
    private final VorbisComment comment;

    private CommentBlock(VorbisComment comment) {
        this.comment = Objects.requireNonNull(comment);
    }

    public static CommentBlock read(ByteBuffer buffer) throws IOException {
        VorbisComment comment = VorbisComment.read(buffer);
        return new CommentBlock(comment);
    }

    public VorbisComment comment() {
        return comment;
    }
}
