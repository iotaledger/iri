package com.iota.iri.service.restserver;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.jboss.resteasy.plugins.server.undertow.UndertowJaxrsServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.conf.APIConfig;
import com.iota.iri.service.dto.AbstractResponse;
import com.iota.iri.utils.MapIdentityManager;

import io.undertow.Undertow;
import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.AuthenticationMode;
import io.undertow.security.handlers.AuthenticationCallHandler;
import io.undertow.security.handlers.AuthenticationConstraintHandler;
import io.undertow.security.handlers.AuthenticationMechanismsHandler;
import io.undertow.security.handlers.SecurityInitialHandler;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.impl.BasicAuthenticationMechanism;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.servlet.api.DeploymentInfo;

public class RestEasy extends Application implements RestConnector {
    
    private static final Logger log = LoggerFactory.getLogger(RestEasy.class);
    
    private UndertowJaxrsServer server;
    private APIConfig configuration;

    private DeploymentInfo info;
    
    public RestEasy(APIConfig configuration) {
        this.configuration = configuration;
    }
    
    @Override
    public void init(Function<String, AbstractResponse> processFunction) {
        log.debug("Binding JSON-REST API Undertow server on {}:{}", configuration.getApiHost(), configuration.getPort());

        Undertow.Builder builder = Undertow.builder()
                .addHttpListener(configuration.getPort(), configuration.getApiHost());
        
        server = new UndertowJaxrsServer().start(builder);
        info = server.undertowDeployment(Root.class);
        info.setDisplayName("Iota Realm");
        info.setDeploymentName("Iota Realm");
        info.setContextPath("/");
        
        info.addSecurityWrapper(new HandlerWrapper() {
            @Override
            public HttpHandler wrap(HttpHandler toWrap) {
                String credentials = configuration.getRemoteAuth();
                if (credentials == null || credentials.isEmpty()) {
                    return toWrap;
                }

                final Map<String, char[]> users = new HashMap<>(2);
                users.put(credentials.split(":")[0], credentials.split(":")[1].toCharArray());

                IdentityManager identityManager = new MapIdentityManager(users);
                HttpHandler handler = toWrap;
                handler = new AuthenticationCallHandler(handler);
                handler = new AuthenticationConstraintHandler(handler);
                final List<AuthenticationMechanism> mechanisms =
                        Collections.singletonList(new BasicAuthenticationMechanism("Iota Realm"));

                handler = new AuthenticationMechanismsHandler(handler, mechanisms);
                handler = new SecurityInitialHandler(AuthenticationMode.PRO_ACTIVE, identityManager, handler);
                return handler;
            }
        });
    }

    @Override
    public void start() {
        server.deploy(info);
    }
    
    @Override
    public void stop() {
        server.stop();
    }
}
