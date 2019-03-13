package com.iota.iri.service.restserver;

public interface RestConnector {

    void init(ApiProcessor processFunction);

    void start();

    void stop();
}
