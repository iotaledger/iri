package com.iota.iri.service.restserver;

import java.util.function.Function;

import com.iota.iri.service.dto.AbstractResponse;

public interface RestConnector {

    void init(Function<String, AbstractResponse> processFunction);

    void start();

    void stop();
}
