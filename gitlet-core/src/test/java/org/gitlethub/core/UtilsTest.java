package org.gitlethub.core;

import org.gitlethub.core.utils.HashUtil;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class UtilsTest {

    /**
     * Test for {@link HashUtil}
     */
    @Test
    public void testSha1() {
        String sampleString = "Hello World!";
        byte[] sampleBytes = {0, 1, 2, 15, 16, 127, -1, -128};
        String sampleHash = "15c2fddbdad3c03dfd3a0d00c8e26ba20e5b2c85";
        assertEquals(sampleHash, HashUtil.sha1(sampleBytes, sampleString));

        String empty = "";
        String emptyHash = "da39a3ee5e6b4b0d3255bfef95601890afd80709";
        assertEquals(emptyHash, HashUtil.sha1(empty));
    }
}
