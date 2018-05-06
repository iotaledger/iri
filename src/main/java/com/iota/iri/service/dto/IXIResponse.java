package com.iota.iri.service.dto;

import java.util.Map;
import org.apache.commons.lang3.StringUtils;

/**
 * Created by paul on 2/10/17.
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
