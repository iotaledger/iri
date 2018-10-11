package com.iota.iri.conf.deserializers;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;

/**
 * Deserialize string and trims all leading and trailing whitespaces from string.
 */
public class CustomStringDeserializer  extends StdDeserializer<String> {

    /**
     * Default constructor
     */
    public CustomStringDeserializer() {
        super(String.class);
    }

    @Override
    public String deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
        return jsonParser.getValueAsString().trim();
    }
}
