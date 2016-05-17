package uk.gov.hmrc.flume.interceptor;

import org.apache.flume.Context;
import org.apache.flume.Event;
import org.apache.flume.event.SimpleEvent;
import org.apache.flume.interceptor.Interceptor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class JsonFieldExtractorInterceptorTest {

    @Mock
    private Context configContext;

    @Test
    public void ensureInvalidJsonEventsAreDiscarded() {
        Event event = interceptor().intercept(toEvent("{ foo }"));
        assert(event == null);
    }

    @Test
    public void ensureCorrectJsonFieldExtracted() {
        String eventJson = "{ \"one\" : \"abc\", \"two\" : \"def\" }";

        when(configContext.getString("propertyName")).thenReturn("one");
        Event event = interceptor().intercept(toEvent(eventJson));

        String newBody = new String(event.getBody());

        assert(newBody.equals("abc"));
    }

    @Test
    public void ensureJsonArrayValuesAreDiscarded() {
        String eventJson = "{ \"one\" : [ \"a\" ] }";

        when(configContext.getString("propertyName")).thenReturn("one");
        Event event = interceptor().intercept(toEvent(eventJson));

        assert(event == null);
    }

    @Test
    public void ensureJsonObjectValuesAreDiscarded() {
        String eventJson = "{ \"one\" : { \"a\" } }";

        when(configContext.getString("propertyName")).thenReturn("one");
        Event event = interceptor().intercept(toEvent(eventJson));

        assert(event == null);
    }

    @Test
    public void ensureOnlyTopLevelJsonPropertiesAreReturned() {
        String eventJson = "{ \"one\" : { \"two\" : \"abc\" } }";

        when(configContext.getString("propertyName")).thenReturn("two");
        Event event = interceptor().intercept(toEvent(eventJson));

        assert(event == null);
    }

    public Interceptor interceptor() {
        JsonFieldExtractorInterceptor.Builder builder =
                new JsonFieldExtractorInterceptor.Builder();
        builder.configure(configContext);
        return builder.build();
    }

    public Event toEvent(String body) {
        Event event = new SimpleEvent();
        event.setBody(body.getBytes());
        return event;
    }
}
