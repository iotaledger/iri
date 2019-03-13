package com.iota.iri.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.IOException;

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
import com.iota.iri.service.restserver.RestEasy;

public class ApiHandlerTest {
    @Rule 
     public MockitoRule mockitoRule = MockitoJUnit.rule();
     
     @Mock
     private APIConfig apiconfig;

    
    private RestEasy server;
    
    @Before
    public void setup() {
        Mockito.when(apiconfig.getPort()).thenReturn(TestPortProvider.getPort());
        Mockito.when(apiconfig.getApiHost()).thenReturn(TestPortProvider.getHost());
        //Mockito.when(apiconfig.getRemoteAuth()).thenReturn("user:pass");

                
        this.server = new RestEasy(apiconfig);
        this.server.init((String param) -> {
            System.out.println("called!");
            return null;
        });
        this.server.start();
    }
    
    @After
    public void tearDown() {
        //this.server.stop();
    }
    
    @Test
    public void test() {
        Client client = ClientBuilder.newClient();
        String val = client.target(TestPortProvider.generateURL("/ping"))
                           .request().get(String.class);
        assertEquals("pong", val);
    }
    
    @Test
    public void test2() {
        Client client = ClientBuilder.newClient();
        String val = client.target(TestPortProvider.generateURL("/ping"))
                           .request().get(String.class);
        String val2 = client.target(TestPortProvider.generateURL("/ping"))
                .request().get(String.class);
        
        String val3 = client.target(TestPortProvider.generateURL("/ping"))
                .request().get(String.class);
        System.out.println(val);
    }
    
    @Test
    public void nodeInfo() {
        Client client = ClientBuilder.newClient();
        String jsonString = "{\"command\": \"getNodeInfo\"}";
        Response val = client.target(TestPortProvider.generateURL("/"))
                .request().post(Entity.entity(jsonString, MediaType.APPLICATION_JSON));
        System.out.println(val.getEntity());
    }
}
