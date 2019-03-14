package com.iota.iri.service.restserver.resteasy;

import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.core.Application;

public class RootPath extends Application {

    @Override
    public Set<Class<?>> getClasses(){
       HashSet<Class<?>> classes = new HashSet<Class<?>>();
       classes.add(ApiPath.class);
       return classes;
    }
}
