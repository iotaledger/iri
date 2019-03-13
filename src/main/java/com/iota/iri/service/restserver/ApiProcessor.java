package com.iota.iri.service.restserver;

import java.net.InetAddress;

import com.iota.iri.service.dto.AbstractResponse;

public interface ApiProcessor {
    
    AbstractResponse processFunction(String request, InetAddress inetAddress);
}
