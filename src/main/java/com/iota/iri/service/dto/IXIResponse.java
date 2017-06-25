package com.iota.iri.service.dto;

/**
 * Created by paul on 2/10/17.
 */
public class IXIResponse extends AbstractResponse {
    private Object ixi;

    @SuppressWarnings("unused") // used in the API
    public static IXIResponse create(Object myixi) {
        IXIResponse ixiResponse = new IXIResponse();
        ixiResponse.ixi = myixi;
        return ixiResponse;
    }

    @SuppressWarnings("unused") // used in the API
    public Object getResponse() {
        return ixi;
    }
}
