package com.iota.iri.service.dto;

import java.util.Map;

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
        Object fieldObj = getResponseMapper().get("contentType");
        String fieldValue = fieldObj == null || fieldObj.equals("") ? getdefaultContentType() : fieldObj.toString();
        return fieldValue;
    }

    private Map<String, Object> getResponseMapper(){
        return (Map<String, Object>)ixi;
    }

    public String getContent() {
        Object fieldObj = getResponseMapper().get("content");
        String fieldValue = fieldObj == null || fieldObj.equals("") ? null : fieldObj.toString();
        return fieldValue;
	}
}
