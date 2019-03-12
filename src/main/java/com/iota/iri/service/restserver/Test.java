package com.iota.iri.service.restserver;

import java.io.IOException;
import java.io.InputStream;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.iota.iri.service.dto.AbstractResponse;

@Path("")
public class Test extends ApiCall {
    
    @GET
    @Path("/ping")
    @Produces(MediaType.TEXT_PLAIN)
    public String ping() {
        return "pong";
    }
    
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response apiCall(String request) throws JsonParseException, JsonMappingException, IOException {
        AbstractResponse response = pre(request);
        if (response == null) {
           response = process();
        }

        return Response.ok(post(response)).build();
    }

    private AbstractResponse process() {
        return AbstractResponse.createEmptyResponse();
    }
}
