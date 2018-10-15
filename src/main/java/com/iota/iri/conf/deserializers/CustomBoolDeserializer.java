package com.iota.iri.conf.deserializers;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;

/**
 * Deserialize boolean type.
 */
public class CustomBoolDeserializer extends StdDeserializer<Boolean>{

    /**
     * Default constructor
     */
    public CustomBoolDeserializer() {
        super(Boolean.class);
    }

    @Override
    public Boolean deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
        JsonToken jsonToken = parser.getCurrentToken();
        if (jsonToken == JsonToken.VALUE_TRUE) {
            return true;
        }
        if (jsonToken == JsonToken.VALUE_FALSE) {
            return false;
        }
        if (jsonToken == JsonToken.VALUE_NULL) {
            return parseNull(ctxt);
        }
        if (jsonToken == JsonToken.VALUE_STRING) {
            String text = parser.getText().trim();
            if (StringUtils.isEmpty(text)) {
                return parseNull(ctxt);
            }
            return Boolean.valueOf(text);
        }
        return false;
    }

    private Boolean parseNull(DeserializationContext ctxt) throws IOException {
        _verifyNullForPrimitive(ctxt);
        return false;
    }
}
