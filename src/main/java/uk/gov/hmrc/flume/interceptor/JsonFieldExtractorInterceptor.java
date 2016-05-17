package uk.gov.hmrc.flume.interceptor;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.flume.Context;
import org.apache.flume.Event;
import org.apache.flume.interceptor.Interceptor;

import org.apache.log4j.Logger;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;

/**
 * Extracts a single field from a JSON event body. The original event body is
 * discarded, and replaced with the extracted value.
 *
 * Events with invalid JSON formatting, or where the extracted value is not a
 * simple string value field, are discarded.
 */
public class JsonFieldExtractorInterceptor implements Interceptor {

    private static final Logger LOG = Logger.getLogger(JsonFieldExtractorInterceptor.class);

    private String propertyName;
    private JsonFactory jsonFactory = new JsonFactory();

    private JsonFieldExtractorInterceptor(String propertyName) {
        this.propertyName = propertyName;
    }

    @Override
    public void initialize() {
    }

    @Override
    public Event intercept(Event event) {

        JsonParser parser = null;
        try {
            parser = jsonFactory.createJsonParser(event.getBody());

            // Read past the top level START_OBJECT token
            parser.nextToken();
            if (parser.getCurrentToken() == JsonToken.START_OBJECT) {
                parser.nextToken();
            }

            JsonToken currentToken = parser.getCurrentToken();
            while (currentToken != JsonToken.END_OBJECT) {

                // Match a top level property name
                if (currentToken == JsonToken.FIELD_NAME) {
                    String fieldName = parser.getCurrentName();
                    if (propertyName.equals(fieldName)) {
                        JsonToken value = parser.nextToken();

                        // Extract the next value as a string
                        if (value == JsonToken.VALUE_STRING) {
                            event.setBody(parser.getText().getBytes());
                            return event;
                        } else {
                            LOG.warn("Discarding event with non-string property value");
                            return null;
                        }
                    }

                // Skip over all array and object values
                } else if (currentToken == JsonToken.START_ARRAY
                        || currentToken == JsonToken.START_OBJECT) {
                    parser.skipChildren();
                }

                currentToken = parser.nextToken();
            }

        } catch (JsonParseException|UnsupportedEncodingException e) {
            LOG.warn("Discarding event with invalid JSON formatting", e);
        } catch (IOException e) {
            LOG.warn("Problem reading the event contents", e);
        } finally {
            try {
                parser.close();
            } catch (IOException e) {
            }
        }

        return null;
    }

    @Override
    public List<Event> intercept(List<Event> events) {
        return events.stream()
                .map(this::intercept)
                .collect(Collectors.toList());
    }

    @Override
    public void close() {
    }

    /**
     * Used by Flume to create the Interceptor instance.
     */
    public static class Builder implements Interceptor.Builder {

        private String propertyName;

        @Override
        public Interceptor build() {
            return new JsonFieldExtractorInterceptor(propertyName);
        }

        @Override
        public void configure(Context context) {
            propertyName = context.getString("propertyName");
        }
    }
}
