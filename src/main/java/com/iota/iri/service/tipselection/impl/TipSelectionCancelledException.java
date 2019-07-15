package com.iota.iri.service.tipselection.impl;

/**
 * Thrown when an ongoing tip-selection is cancelled.
 */
public class TipSelectionCancelledException extends Exception {

    /**
     * Creates a new {@link TipSelectionCancelledException}
     * 
     * @param msg the specific message.
     */
    public TipSelectionCancelledException(String msg) {
        super(msg);
    }
}
