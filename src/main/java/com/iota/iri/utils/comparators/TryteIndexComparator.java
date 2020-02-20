package com.iota.iri.utils.comparators;

import com.iota.iri.utils.Converter;

import java.util.Comparator;

import static com.iota.iri.controllers.TransactionViewModel.CURRENT_INDEX_TRINARY_OFFSET;
import static com.iota.iri.controllers.TransactionViewModel.CURRENT_INDEX_TRINARY_SIZE;
import static com.iota.iri.controllers.TransactionViewModel.TRYTES_SIZE;

/**
 * A comparator for tytes based on current index.
 */
public class TryteIndexComparator implements Comparator<String> {

    @Override
    public int compare(String t1, String t2) {
        return Long.compare(getCurrentIndex(t1), getCurrentIndex(t2));
    }
    
	private long getCurrentIndex(String tryte) {
        byte[] txTrits = Converter.allocateTritsForTrytes(TRYTES_SIZE);
		Converter.trits(tryte, txTrits, 0);
        return Converter.longValue(txTrits, CURRENT_INDEX_TRINARY_OFFSET, CURRENT_INDEX_TRINARY_SIZE);
    }
}
