package com.iota.iri.zmq;

import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Persistable;

public interface MessageQueueProvider {
    void publish(String message, Object... objects);
    boolean update(Persistable model, Indexable index, String item);
    void shutdown();
}
