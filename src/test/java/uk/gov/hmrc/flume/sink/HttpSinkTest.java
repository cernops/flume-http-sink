package uk.gov.hmrc.flume.sink;

import static org.mockito.Mockito.*;

import org.apache.flume.*;
import org.apache.flume.Sink.Status;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class HttpSinkTest {

    private static final Integer DEFAULT_REQUEST_TIMEOUT = 5000;
    private static final Integer DEFAULT_CONNECT_TIMEOUT = 5000;
    private static final String DEFAULT_ACCEPT_HEADER = "text/plain";
    private static final String DEFAULT_CONTENT_TYPE_HEADER = "text/plain";

    @Mock
    private Context configContext;

    @Mock
    private Channel channel;

    @Mock
    private Transaction transaction;

    @Mock
    private Event event;

    @Test
    public void ensureAllConfigurationOptionsRead() {
        whenDefaultStringConfig();
        when(configContext.getInteger(eq("connectTimeout"), Mockito.anyInt())).thenReturn(1000);
        when(configContext.getInteger(eq("requestTimeout"), Mockito.anyInt())).thenReturn(1000);

        new HttpSink().configure(configContext);

        verify(configContext).getString("endpoint", "");
        verify(configContext).getInteger(eq("connectTimeout"), Mockito.anyInt());
        verify(configContext).getInteger(eq("requestTimeout"), Mockito.anyInt());
        verify(configContext).getString(eq("acceptHeader"), Mockito.anyString());
        verify(configContext).getString(eq("contentTypeHeader"), Mockito.anyString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void ensureExceptionIfEndpointUrlEmpty() {
        when(configContext.getString("endpoint", "")).thenReturn("");
        new HttpSink().configure(configContext);
    }

    @Test(expected = IllegalArgumentException.class)
    public void ensureExceptionIfEndpointUrlInvalid() {
        when(configContext.getString("endpoint", "")).thenReturn("invalid url");
        new HttpSink().configure(configContext);
    }

    @Test(expected = IllegalArgumentException.class)
    public void ensureExceptionIfConnectTimeoutNegative() {
        whenDefaultStringConfig();
        when(configContext.getInteger("connectTimeout", 1000)).thenReturn(-1000);
        when(configContext.getInteger(eq("requestTimeout"), Mockito.anyInt())).thenReturn(1000);
        new HttpSink().configure(configContext);
    }

    @Test
    public void ensureDefaultConnectTimeoutCorrect() {
        whenDefaultStringConfig();
        when(configContext.getInteger("connectTimeout", DEFAULT_CONNECT_TIMEOUT)).thenReturn(1000);
        when(configContext.getInteger(eq("requestTimeout"), Mockito.anyInt())).thenReturn(1000);
        new HttpSink().configure(configContext);
        verify(configContext).getInteger("connectTimeout", DEFAULT_CONNECT_TIMEOUT);
    }

    @Test(expected = IllegalArgumentException.class)
    public void ensureExceptionIfRequestTimeoutNegative() {
        whenDefaultStringConfig();
        when(configContext.getInteger("requestTimeout", 1000)).thenReturn(-1000);
        when(configContext.getInteger(eq("connectTimeout"), Mockito.anyInt())).thenReturn(1000);
        new HttpSink().configure(configContext);
    }

    @Test
    public void ensureDefaultRequestTimeoutCorrect() {
        whenDefaultStringConfig();
        when(configContext.getInteger("requestTimeout", DEFAULT_REQUEST_TIMEOUT)).thenReturn(1000);
        when(configContext.getInteger(eq("connectTimeout"), Mockito.anyInt())).thenReturn(1000);
        new HttpSink().configure(configContext);
        verify(configContext).getInteger("requestTimeout", DEFAULT_REQUEST_TIMEOUT);
    }

    @Test
    public void ensureDefaultAcceptHeaderCorrect() {
        whenDefaultTimeouts();
        whenDefaultStringConfig();
        new HttpSink().configure(configContext);
        verify(configContext).getString("acceptHeader", DEFAULT_ACCEPT_HEADER);
    }

    @Test
    public void ensureDefaultContentTypeHeaderCorrect() {
        whenDefaultTimeouts();
        whenDefaultStringConfig();
        new HttpSink().configure(configContext);
        verify(configContext).getString("contentTypeHeader", DEFAULT_CONTENT_TYPE_HEADER);
    }

    @Test
    public void ensureBackoffOnNullEvent() throws Exception {
        when(channel.getTransaction()).thenReturn(transaction);
        when(channel.take()).thenReturn(null);
        executeWithMockedChannel(true);
    }

    @Test
    public void ensureBackoffOnEmptyEvent() throws Exception {
        when(channel.getTransaction()).thenReturn(transaction);
        when(channel.take()).thenReturn(event);
        when(event.getBody()).thenReturn(new byte[] {});
        executeWithMockedChannel(true);
    }

    private void executeWithMockedChannel(boolean commit) throws EventDeliveryException {
        Context context = new Context();
        context.put("endpoint", "http://localhost:8080/endpoint");

        HttpSink httpSink = new HttpSink();
        httpSink.configure(context);
        httpSink.setChannel(channel);
        Status status = httpSink.process();

        assert(status == Status.BACKOFF);

        verify(transaction).begin();
        if (commit) {
            verify(transaction).commit();
        } else {
            verify(transaction).rollback();
        }
        verify(transaction).close();
    }

    private void whenDefaultStringConfig() {
        when(configContext.getString("endpoint", "")).thenReturn("http://test.abc/");
        when(configContext.getString("acceptHeader", "")).thenReturn("test/accept");
        when(configContext.getString("contentTypeHeader", "")).thenReturn("test/content");
    }

    private void whenDefaultTimeouts() {
        when(configContext.getInteger(eq("requestTimeout"), Mockito.anyInt())).thenReturn(1000);
        when(configContext.getInteger(eq("connectTimeout"), Mockito.anyInt())).thenReturn(1000);
    }
}
