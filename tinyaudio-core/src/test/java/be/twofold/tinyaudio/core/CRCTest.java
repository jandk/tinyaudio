package be.twofold.tinyaudio.core;

import org.junit.jupiter.api.Test;

import java.util.zip.Checksum;

import static org.assertj.core.api.Assertions.assertThat;

class CRCTest {
    @Test
    void testOggCrc32() {
        Checksum checksum = CRC.OGG_PAGE.get();
        checksum.update("123456789".getBytes());
        assertThat(checksum.getValue()).isEqualTo(0x89A1_897FL);
    }
}
