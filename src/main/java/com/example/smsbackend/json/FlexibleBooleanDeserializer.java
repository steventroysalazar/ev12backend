package com.example.smsbackend.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.util.Locale;

public class FlexibleBooleanDeserializer extends JsonDeserializer<Boolean> {

    @Override
    public Boolean deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonToken token = parser.currentToken();

        if (token == JsonToken.VALUE_TRUE) {
            return true;
        }

        if (token == JsonToken.VALUE_FALSE) {
            return false;
        }

        if (token == JsonToken.VALUE_NUMBER_INT) {
            int value = parser.getIntValue();
            if (value == 1) {
                return true;
            }
            if (value == 0) {
                return false;
            }
            throw context.weirdNumberException(value, Boolean.class, "Expected 0 or 1 for boolean value");
        }

        if (token == JsonToken.VALUE_STRING) {
            String value = parser.getText();
            if (value == null) {
                return null;
            }

            String normalized = value.trim().toLowerCase(Locale.ROOT);
            if (normalized.isEmpty()) {
                return null;
            }

            return switch (normalized) {
                case "true", "1", "yes", "y", "on" -> true;
                case "false", "0", "no", "n", "off" -> false;
                default -> throw context.weirdStringException(value, Boolean.class, "Expected true/false or 1/0");
            };
        }

        if (token == JsonToken.VALUE_NULL) {
            return null;
        }

        throw context.handleUnexpectedToken(Boolean.class, parser);
    }
}

