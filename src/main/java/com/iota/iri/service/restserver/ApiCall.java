package com.iota.iri.service.restserver;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.iota.iri.service.dto.AbstractResponse;
import com.iota.iri.service.dto.ErrorResponse;

public abstract class ApiCall {
    
    private static final Gson gson = new GsonBuilder().create();
    
    private long start;

    protected Map<String, Object> request;
    
    public AbstractResponse pre(String requestString) {
        start = System.currentTimeMillis();
        
        try {
            request = gson.fromJson(requestString, Map.class);
        } catch(JsonSyntaxException jsonSyntaxException) {
            return ErrorResponse.create("Invalid JSON syntax: " + jsonSyntaxException.getMessage());
        }
        if (request == null) {
            return ErrorResponse.create("Invalid request payload: '" + requestString + "'");
        }

        // Did the requester ask for a command?
        if (getCommand() == null) {
            return ErrorResponse.create("COMMAND parameter has not been specified in the request.");
        }
        return null;
    }
    
    public AbstractResponse post(AbstractResponse response) {
        response.setDuration((int) (System.currentTimeMillis() - start));
        
        return response;
    }
    
    public String getCommand() {
        return request.get("command").toString();
    }
}
