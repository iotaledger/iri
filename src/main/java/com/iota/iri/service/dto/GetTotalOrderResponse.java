package com.iota.iri.service.dto;

import java.util.*;

import com.google.gson.Gson;
import com.iota.iri.model.Hash;
import com.iota.iri.utils.Converter;

/**
 * This class represents the core API error for accessing a command which is limited by this Node.
 */
public class GetTotalOrderResponse extends AbstractResponse {

    private String totalOrder;

    public static AbstractResponse create(List<Hash> order) {
        GetTotalOrderResponse res = new GetTotalOrderResponse();
        Gson gson = new Gson();
        
        List<String> orders = new ArrayList<>();
        for(Hash h : order) {
            String tryte = Converter.trytes(h.trits());
            orders.add(tryte);
        }
        res.totalOrder = gson.toJson(orders);
        return res;
    }

    /**
     * Gets the error
     *
     * @return The error.
     */
    public String getDAG() {
        return totalOrder;
    }
}
