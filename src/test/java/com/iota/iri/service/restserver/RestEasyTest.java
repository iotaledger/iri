package com.iota.iri.service.restserver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.net.InetAddress;
import java.util.Base64;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.jboss.resteasy.test.TestPortProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import com.iota.iri.conf.APIConfig;
import com.iota.iri.service.dto.ErrorResponse;
import com.iota.iri.service.dto.GetNodeInfoResponse;
import com.iota.iri.service.restserver.resteasy.RestEasy;

public class RestEasyTest {
    
    private static final String USER_PASS = "user:pass";
    
    @Rule 
    public MockitoRule mockitoRule = MockitoJUnit.rule();
     
    @Mock
    private APIConfig apiconfig;

    
    private RestEasy server;
    
    @Before
    public void setUp() {
        Mockito.when(apiconfig.getPort()).thenReturn(TestPortProvider.getPort());
        Mockito.when(apiconfig.getApiHost()).thenReturn(TestPortProvider.getHost());
        Mockito.when(apiconfig.getMaxBodyLength()).thenReturn(Integer.MAX_VALUE);
    }
    
    @After
    public void tearDown() {
        this.server.stop();
    }
    
    @Test
    public void nodeInfoMissingApiVersion() {
        this.server = new RestEasy(apiconfig);
        this.server.init((String param, InetAddress address) -> {
            return GetNodeInfoResponse.createEmptyResponse();
        });
        this.server.start();
        
        Client client = ClientBuilder.newClient();
        String jsonString = "{\"command\": \"getNodeInfo\"}";
        Response val = client.target(TestPortProvider.generateURL("/"))
                .request()
                .post(Entity.entity(jsonString, MediaType.APPLICATION_JSON));
        ErrorResponse response = val.readEntity(ErrorResponse.class);
        assertEquals("API version should be required in the header", "Invalid API Version", response.getError());
    }
    
    @Test
    public void nodeInfoValid() {
        this.server = new RestEasy(apiconfig);
        this.server.init((String param, InetAddress address) -> {
            return GetNodeInfoResponse.createEmptyResponse();
        });
        this.server.start();
        
        Client client = ClientBuilder.newClient();
        String jsonString = "{\"command\": \"getNodeInfo\"}";
        Response val = client.target(TestPortProvider.generateURL("/"))
                .request()
                .header("X-IOTA-API-Version", "1")
                .post(Entity.entity(jsonString, MediaType.APPLICATION_JSON));
        
        GetNodeInfoResponse response = val.readEntity(GetNodeInfoResponse.class);
        assertNotNull("Response should not be parseable as a GetNodeInfoResponse", response);
    }
    
    @Test
    public void notAllowed() {
        Mockito.when(apiconfig.getRemoteAuth()).thenReturn(USER_PASS);
        
        this.server = new RestEasy(apiconfig);
        this.server.init((String param, InetAddress address) -> {
            return GetNodeInfoResponse.createEmptyResponse();
        });
        this.server.start();
        
        Client client = ClientBuilder.newClient();
        String jsonString = "{\"command\": \"getNodeInfo\"}";
        Response val = client.target(TestPortProvider.generateURL("/"))
                .request()
                .header("X-IOTA-API-Version", "1")
                .post(Entity.entity(jsonString, MediaType.APPLICATION_JSON));
        
        assertEquals("Request should be denied due to lack of authentication", 
                Response.Status.UNAUTHORIZED, val.getStatusInfo());
    }
    
    @Test
    public void allowed() {
        Mockito.when(apiconfig.getRemoteAuth()).thenReturn(USER_PASS);
        
        this.server = new RestEasy(apiconfig);
        this.server.init((String param, InetAddress address) -> {
            return GetNodeInfoResponse.createEmptyResponse();
        });
        this.server.start();
        
        Client client = ClientBuilder.newClient();
        String jsonString = "{\"command\": \"getNodeInfo\"}";
        
        String encoded = Base64.getEncoder().encodeToString(USER_PASS.getBytes());
        
        Response val = client.target(TestPortProvider.generateURL("/"))
                .request()
                .header("X-IOTA-API-Version", "1")
                .header("Authorization", "Basic " + encoded)
                .post(Entity.entity(jsonString, MediaType.APPLICATION_JSON));
        
        assertEquals("Request should be accepted as we authenticated", Response.Status.OK, val.getStatusInfo());
    }
}
