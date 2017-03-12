package com.iota.iri.model;

import com.iota.iri.service.tangle.annotations.Model;
import com.iota.iri.service.tangle.annotations.ModelIndex;

/**
 * Created by paul on 3/11/17 for iri-testnet.
 */
@Model
public class TransactionToRequest {
    @ModelIndex byte[] hash;
}
