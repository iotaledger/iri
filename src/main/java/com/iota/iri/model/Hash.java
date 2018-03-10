package com.iota.iri.model;

import com.iota.iri.hash.Curl;
import com.iota.iri.hash.Sponge;
import com.iota.iri.hash.SpongeFactory;
import com.iota.iri.storage.Indexable;
import com.iota.iri.utils.Converter;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;
 

public class Hash implements Serializable, Indexable {

    public static final int SIZE_IN_TRITS = 243;
    public static final int SIZE_IN_BYTES = 49;

    public static final Hash NULL_HASH = new Hash(new int[Curl.HASH_LENGTH]);

    private byte[] bytes;
    private int[] trits;
    private int hashCode;
	
    public final Object sync = new Object();

    
    // constructors' bill

    public Hash(){}

    public Hash(final byte[] bytes, final int offset, final int size) {
        fullRead(bytes, offset, size);
    }
    
    public Hash(final int[] trits, final int offset) {
        fullRead(trits, offset);
    }
    
    public Hash(final byte[] bytes) {
        fullRead(bytes, 0, SIZE_IN_BYTES);
    }

    public Hash(final int[] trits) {
        fullRead(trits, 0);
    }

    public Hash(final String trytes) {
        int[] trits = new int[SIZE_IN_TRITS];
        Converter.trits(trytes,trits, 0);
        fullRead(trits, 0);
    }
	
	private void fullRead(final byte[] bytes, final int offset, final int size){
		synchronized (sync) {
			this.bytes = new byte[SIZE_IN_BYTES];
			System.arraycopy(bytes, offset, this.bytes, 0, size - offset > bytes.length ? bytes.length-offset: size);
			this.trits = new int[Curl.HASH_LENGTH];
                        Converter.getTrits(this.bytes, this.trits);
			this.hashCode = Arrays.hashCode(this.bytes);
		}
	}
	
	private void fullRead(final int[] trits, final int offset){
		synchronized (sync) {
			this.trits = new int[SIZE_IN_TRITS];
			System.arraycopy(trits, offset, this.trits, 0, SIZE_IN_TRITS);
			this.bytes = new byte[SIZE_IN_BYTES];
                        Converter.bytes(this.trits, 0, this.bytes, 0, this.trits.length);  
			this.hashCode = Arrays.hashCode(this.bytes);
		}
	}
      

    //
    /*
    public static Hash calculate(byte[] bytes) {
        return calculate(bytes, SIZE_IN_TRITS, new Curl());
    }
    */
    public static Hash calculate(SpongeFactory.Mode mode, int[] trits) {
        return calculate(trits, 0, trits.length, SpongeFactory.create(mode));
    }

    public static Hash calculate(byte[] bytes, int tritsLength, final Sponge curl) {
        int[] trits = new int[tritsLength];
        Converter.getTrits(bytes, trits);
        return calculate(trits, 0, tritsLength, curl);
    }
    public static Hash calculate(final int[] tritsToCalculate, int offset, int length, final Sponge curl) {
        int[] hashTrits = new int[SIZE_IN_TRITS];
        curl.reset();
        curl.absorb(tritsToCalculate, offset, length);
        curl.squeeze(hashTrits, 0, SIZE_IN_TRITS);
        return new Hash(hashTrits);
    }

    public int trailingZeros() {
		synchronized (sync) {
			int index, zeros;
			index = SIZE_IN_TRITS;
			zeros = 0;
			while(index-- > 0 && this.trits[index] == 0) {
				zeros++;
			}
			return zeros;
		}
    }

  

    @Override
    public boolean equals(Object o) {
		synchronized (sync) {
			if (this == o) {
				return true;
			}
			if (o == null || getClass() != o.getClass()) {
				return false;
			}
			Hash hash = (Hash) o;
			return Arrays.equals(this.bytes, hash.bytes());
		}
     }

    @Override
    public int hashCode() {  
		synchronized (sync) {    
			return this.hashCode;
		}
    }

    @Override
    public String toString() {
		synchronized (sync) {
			return Converter.trytes(this.trits);
		}
    }
    
    public byte[] bytes() { 
		synchronized (sync) {    
			return this.bytes;
		}
    }
	
    public int[] trits() {
		synchronized (sync) {
			return this.trits;
		}
    }

    @Override
    public void read(byte[] bytes) {
         fullRead(bytes, 0, SIZE_IN_BYTES);
    }

    @Override
    public Indexable incremented() {
        return null;
    }

    @Override
    public Indexable decremented() {
        return null;
    }

    @Override
    public int compareTo(Indexable indexable) {
        Hash hash = new Hash(indexable.bytes());
        if (this.equals(hash)) {
            return 0;
        }
        long diff = Converter.longValue(hash.trits(), 0, SIZE_IN_TRITS) - Converter.longValue(trits(), 0, SIZE_IN_TRITS);
        if (Math.abs(diff) > Integer.MAX_VALUE) {
            return diff > 0L ? Integer.MAX_VALUE : Integer.MIN_VALUE + 1;
        }
        return (int) diff;
    }
}
