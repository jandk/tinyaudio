package be.twofold.tinyflac.metadata;

public sealed interface MetadataBlock
    permits Application, CueSheet, Padding, Picture, SeekTable, StreamInfo, Unknown, VorbisComment {
}
