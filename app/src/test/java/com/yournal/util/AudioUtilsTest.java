package com.yournal.util;

import org.junit.Test;
import static org.junit.Assert.*;

public class AudioUtilsTest {

    @Test
    public void testResampleTo16k_NoResampleNeeded() {
        byte[] input = new byte[320]; // 160 samples
        for (int i = 0; i < input.length; i++) {
            input[i] = (byte) i;
        }
        
        byte[] output = AudioUtils.resampleTo16k(input, 16000);
        
        assertArrayEquals(input, output);
    }

    @Test
    public void testResampleTo16k_Decimation() {
        // 32000 to 16000 (2:1 ratio)
        byte[] input = new byte[640]; // 320 samples
        for (int i = 0; i < 320; i++) {
            short sample = (short) (i * 100);
            input[i * 2] = (byte) (sample & 0xFF);
            input[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
        }
        
        byte[] output = AudioUtils.resampleTo16k(input, 32000);
        
        assertEquals(320, output.length); // 160 samples
        
        // Every even sample from input should roughly be in output
        for (int i = 0; i < 160; i++) {
            short expected = (short) (i * 2 * 100);
            short actual = (short) ((output[i * 2] & 0xFF) | (output[i * 2 + 1] << 8));
            // Linear interpolation might be slightly off depending on floats, 
            // but for exact integer ratios it should be close.
            assertTrue(Math.abs(expected - actual) < 5);
        }
    }
}
