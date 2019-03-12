package com.iota.iri.service;

import static io.undertow.Handlers.path;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Application;

import org.jboss.resteasy.plugins.server.undertow.UndertowJaxrsServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.conf.APIConfig;
import com.iota.iri.service.restserver.Root;
import com.iota.iri.utils.MapIdentityManager;

import io.undertow.Undertow;
import io.undertow.Undertow.Builder;
import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.AuthenticationMechanismFactory;
import io.undertow.security.api.AuthenticationMode;
import io.undertow.security.handlers.AuthenticationCallHandler;
import io.undertow.security.handlers.AuthenticationConstraintHandler;
import io.undertow.security.handlers.AuthenticationMechanismsHandler;
import io.undertow.security.handlers.SecurityInitialHandler;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.impl.BasicAuthenticationMechanism;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import io.undertow.util.MimeMappings;
import io.undertow.util.StatusCodes;

public class ApiHandler extends Application {
    
    private static final Logger log = LoggerFactory.getLogger(ApiHandler.class);
    
    private UndertowJaxrsServer server;
    private APIConfig configuration;
    
    public ApiHandler(APIConfig configuration) {
        this.configuration = configuration;
    }
    
    public void init() throws IOException {
        final int apiPort = configuration.getPort();
        final String apiHost = configuration.getApiHost();

        log.debug("Binding JSON-REST API Undertow server on {}:{}", apiHost, apiPort);

        Undertow.Builder builder = Undertow.builder()
                .addHttpListener(apiPort, apiHost);
        
        server = new UndertowJaxrsServer().start(builder);
        DeploymentInfo info = server.undertowDeployment(Root.class);
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
        
        server.deploy(info);
    }
    
    public void stop() {
        server.stop();
    }
    
    private void handleRequest(final HttpServerExchange exchange) throws Exception {
        HttpString requestMethod = exchange.getRequestMethod();
        if (Methods.OPTIONS.equals(requestMethod)) {
            String allowedMethods = "GET,HEAD,POST,PUT,DELETE,TRACE,OPTIONS,CONNECT,PATCH";
            //return list of allowed methods in response headers
            exchange.setStatusCode(StatusCodes.OK);
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, MimeMappings.DEFAULT_MIME_MAPPINGS.get("txt"));
            exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, 0);
            exchange.getResponseHeaders().put(Headers.ALLOW, allowedMethods);
            exchange.getResponseHeaders().put(new HttpString("Access-Control-Allow-Origin"), "*");
            exchange.getResponseHeaders().put(new HttpString("Access-Control-Allow-Headers"), "User-Agent, Origin, X-Requested-With, Content-Type, Accept, X-IOTA-API-Version");
            exchange.getResponseSender().close();
            return;
        }

        if (exchange.isInIoThread()) {
            //exchange.dispatch(this);
            return;
        }
        
        //processRequest(exchange);
    }
}
