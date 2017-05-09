package com.iota.iri;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.hash.Curl;
import com.iota.iri.model.Hash;
import com.iota.iri.model.Transaction;
import com.iota.iri.utils.Converter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.iota.iri.controllers.TransactionViewModel.*;

/**
 * Created by paul on 4/17/17.
 */
public class TransactionValidator {
    private static final Logger log = LoggerFactory.getLogger(TransactionValidator.class);
    private static int MIN_WEIGHT_MAGNITUDE = 18;
    public static void init(int weight) {
        MIN_WEIGHT_MAGNITUDE = weight;
    }

    private static void runValidation(TransactionViewModel transactionViewModel) {
        for (int i = VALUE_TRINARY_OFFSET + VALUE_USABLE_TRINARY_SIZE; i < VALUE_TRINARY_OFFSET + VALUE_TRINARY_SIZE; i++) {
            if (transactionViewModel.trits()[i] != 0) {
                log.error("Transaction trytes: "+Converter.trytes(transactionViewModel.trits()));
                throw new RuntimeException("Invalid transaction value");
            }
        }

        int weightMagnitude = transactionViewModel.getHash().trailingZeros();
        if(weightMagnitude < MIN_WEIGHT_MAGNITUDE) {
            log.error("Hash found: {}", transactionViewModel.getHash());
            log.error("Transaction trytes: "+Converter.trytes(transactionViewModel.trits()));
            throw new RuntimeException("Invalid transaction hash");
        }
    }

    public static TransactionViewModel validate(final int[] trits) {
        TransactionViewModel transactionViewModel = new TransactionViewModel(trits, Hash.calculate(trits, 0, trits.length, new Curl()));
        runValidation(transactionViewModel);
        return transactionViewModel;
    }
    public static TransactionViewModel validate(final byte[] bytes) {
        return validate(bytes, new Curl());

    }

    public static TransactionViewModel validate(final byte[] bytes, Curl curl) {
        TransactionViewModel transactionViewModel = new TransactionViewModel(bytes, Hash.calculate(bytes, TransactionViewModel.TRINARY_SIZE, curl));
        runValidation(transactionViewModel);
        return transactionViewModel;
    }
}
