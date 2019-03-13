package com.iota.iri.service.restserver;

import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.core.Application;

public class Root extends Application {
    
    public Root(ApiProcessor processFunction) {
        
    }

    @Override
    public Set<Class<?>> getClasses(){
       HashSet<Class<?>> classes = new HashSet<Class<?>>();
       classes.add(Test.class);
       return classes;
    }
}
