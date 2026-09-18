package be.twofold.tinyogg;

import org.junit.jupiter.api.Test;

import java.util.zip.Checksum;

import static org.assertj.core.api.Assertions.assertThat;

class OggCrc32Test {
    @Test
    void testBasic() {
        Checksum checksum = new OggCrc32();
        checksum.update("123456789".getBytes());
        assertThat(checksum.getValue()).isEqualTo(0x89A1_897FL);
    }
}
