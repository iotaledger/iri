package com.iota.iri.service.dto;

import com.iota.iri.IXI;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

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

    private String getdefaultContentType() {
        return "application/json";
    }

    public String getResponseContentType() {
        Map<String, Object> responseMapper = getResponseMapper();
        String fieldObj = (String)responseMapper.get("contentType");
        String fieldValue = StringUtils.isBlank(fieldObj) ? getdefaultContentType() : fieldObj;
        return fieldValue;
    }

    private Map<String, Object> getResponseMapper(){
        return (Map<String, Object>)ixi;
    }

    public String getContent() {
        Map<String, Object> responseMapper = getResponseMapper();
        String fieldObj = (String)responseMapper.get("content");
        String fieldValue = StringUtils.isBlank(fieldObj) ? null : fieldObj;
        return fieldValue;
	}
}
