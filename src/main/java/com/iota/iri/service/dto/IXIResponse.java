package com.iota.iri.service.dto;

import com.iota.iri.IXI;

/**
 * <p>
 *     When a command is not recognized by the default API, we try to process it as an IXI module.
 *     IXI stands for Iota eXtension Interface. See {@link IXI} for more information.
 * </p>
 * <p>
 *     The response will contain the reply that the IXI module gave. 
 *     This could be empty, depending on the module.
 * </p>
 * 
 * An example module can be found here: <a href="https://github.com/iotaledger/Snapshot.ixi">Snapshot.ixi</a>
 * 
 */
public class IXIResponse extends AbstractResponse {
    private Object ixi;

    public static IXIResponse create(Object myixi) {
        IXIResponse ixiResponse = new IXIResponse();
        ixiResponse.ixi = myixi;
        return ixiResponse;
    }

    public Object getResponse() {
        return ixi;
    }
}
