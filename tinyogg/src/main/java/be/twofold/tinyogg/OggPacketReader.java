package be.twofold.tinyogg;

import java.io.IOException;
import java.util.Objects;

public final class OggPacketReader {
    private final OggPageReader pages;
    private final int serialNumber;
    private OggPage page;
    private int segment;
    private int expectedSequence;
    private boolean discard;
    private long packetNumber;

    public OggPacketReader(OggPageReader pages, int serialNumber) {
        this.pages = Objects.requireNonNull(pages);
        this.serialNumber = serialNumber;
    }

    public boolean next(OggPacket out) throws IOException {
        out.reset();
        while (true) {
            if (page == null || segment == page.segmentCount()) {
                if (!nextPage(out)) {
                    return false;
                }
            }

            int start = segment;
            int end = page.packetEnd(start);
            if (!discard) {
                out.append(page.payload(), page.segmentOffset(start), page.packetLength(start));
            }
            segment = end;
            if (!page.endsPacket(end - 1)) {
                continue; // Packet continues on the next page
            }
            if (discard) {
                discard = false; // Tail of a packet whose head we never saw
                continue;
            }

            boolean last = end - 1 == page.lastPacketEnd();
            out.complete(
                last ? page.granulePosition() : -1,
                packetNumber++,
                start == 0 && page.isBeginOfStream(),
                last && page.isEndOfStream()
            );
            return true;
        }
    }

    private boolean nextPage(OggPacket out) throws IOException {
        while (true) {
            page = pages.next();
            if (page == null) {
                return false;
            }
            if (page.bitstreamSerialNumber() != serialNumber) {
                continue;
            }

            boolean gap = page.pageSequenceNumber() != expectedSequence;
            expectedSequence = page.pageSequenceNumber() + 1;
            segment = 0;

            if (!page.isContinuation()) {
                // Any partial packet we had is garbage
                out.reset();
                discard = false;
            } else if (gap || out.length() == 0) {
                // Partial across a gap is garbage, and this page starts with a headless tail
                out.reset();
                discard = true;
            }
            if (page.segmentCount() > 0) {
                return true;
            }
        }
    }
}
