package com.iota.iri.utils;

import java.nio.ByteBuffer;

import org.junit.Assert;
import org.junit.Test;

public class TestSerializer {

	@Test
	public void testSerEndian() {
		final long[] ltestvec = {0L, 1L, Long.MAX_VALUE, 123456789L};
		final int[] itestvec = {0, 1, Integer.MAX_VALUE, 123456789};
		
		for(long l : ltestvec)
			Assert.assertArrayEquals(Serializer.serialize(l), bbSerialize(l));
		
		for(int i : itestvec)
			Assert.assertArrayEquals(Serializer.serialize(i), bbSerialize(i));
		
	}
	
	// reference for original bytebuffer code
    public static byte[] bbSerialize(Long value) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(value);
        return buffer.array();
    }
    public static byte[] bbSerialize(int integer) {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
        buffer.putInt(integer);
        return buffer.array();
    }

}
