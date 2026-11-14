package be.twofold.tinyaudio.ogg;

import be.twofold.tinyaudio.core.Packet;
import be.twofold.tinyaudio.core.PacketSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class OggPacketReader implements PacketSource {
    private final List<byte[]> chunks = new ArrayList<>();
    private final OggPageReader pages;
    private Integer serialNumber;
    private OggPage page;
    private int segment;
    private int sequence;
    private boolean gap;
    private boolean finished;

    public OggPacketReader(OggPageReader pages) {
        this.pages = Objects.requireNonNull(pages);
    }

    public OggPacketReader(OggPageReader pages, int serialNumber) {
        this(pages);
        this.serialNumber = serialNumber;
    }

    public Integer serialNumber() {
        return serialNumber;
    }

    @Override
    public Packet next() throws IOException {
        if (finished) {
            return null;
        }
        chunks.clear();
        while (true) {
            if (page == null || segment == page.segmentCount()) {
                if (!nextPage()) {
                    return null;
                }
            }

            int end = page.packetEnd(segment);
            int offset = page.segmentOffset(segment);
            int length = page.packetLength(segment);
            segment = end;

            // Packet continues on the next page, grab the data
            if (!page.endsPacket(end - 1)) {
                chunks.add(Arrays.copyOfRange(page.payload(), offset, offset + length));
                continue;
            }

            // Packet ends here, so just copy the data
            byte[] data = chunks.isEmpty()
                ? Arrays.copyOfRange(page.payload(), offset, offset + length)
                : combine(page.payload(), offset, length);

            boolean last = end == page.lastPacketEnd();
            finished = last && page.isEndOfStream();
            Packet packet = new Packet(
                data,
                last ? page.granulePosition() : -1,
                finished,
                gap
            );
            gap = false;
            return packet;
        }
    }

    @Override
    public void close() throws IOException {
        pages.close();
    }

    private boolean nextPage() throws IOException {
        while (true) {
            // Try to read a page, if not, we're done
            page = pages.next();
            if (page == null) {
                return false;
            }

            // If we don't have a serial number yet, grab the first one we can find
            if (serialNumber == null) {
                if (!page.isBeginOfStream()) {
                    continue;
                }
                serialNumber = page.bitstreamSerialNumber();
            }

            // Serial number mismatch, gap this page
            if (page.bitstreamSerialNumber() != serialNumber) {
                continue;
            }

            // Sequence gap means pages were lost. Codecs need to know.
            boolean sequenceGap = page.pageSequenceNumber() != sequence;
            sequence = page.pageSequenceNumber() + 1;
            gap |= sequenceGap;
            segment = 0;

            // Partial package is trash if sequence gap or not continuation
            if (sequenceGap || !page.isContinuation()) {
                chunks.clear();
            }

            // Skip tail if we have nothing to resume
            if (page.isContinuation() && chunks.isEmpty()) {
                segment = page.packetEnd(0);
            }

            if (segment < page.segmentCount()) {
                return true;
            }
        }
    }

    private byte[] combine(byte[] payload, int off, int len) {
        int total = len;
        for (byte[] chunk : chunks) {
            total += chunk.length;
        }

        byte[] result = new byte[total];
        int position = 0;
        for (byte[] chunk : chunks) {
            System.arraycopy(chunk, 0, result, position, chunk.length);
            position += chunk.length;
        }
        System.arraycopy(payload, off, result, position, len);
        return result;
    }
}
