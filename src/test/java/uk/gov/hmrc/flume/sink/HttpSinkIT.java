package uk.gov.hmrc.flume.sink;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;

import com.github.tomakehurst.wiremock.global.RequestDelaySpec;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.RequestListener;
import com.github.tomakehurst.wiremock.http.Response;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.FileWriter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Runs a set of tests against a correctly configured external running Flume
 * instance.
 */
@Category(IntegrationTest.class)
public class HttpSinkIT {

    private static int RESPONSE_TIMEOUT = 2000;
    private static int CONNECT_TIMEOUT = 2000;

    @Rule
    public WireMockRule service = new WireMockRule(wireMockConfig().port(8080));

    @Test
    public void ensureSuccessfulMessageDelivery() throws Exception {
        final CountDownLatch gate = new CountDownLatch(1);

        service.addMockServiceRequestListener(new RequestListener() {
            public void requestReceived(Request request, Response response) {
                gate.countDown();
            }
        });

        service.stubFor(post(urlEqualTo("/datastream"))
                .withRequestBody(equalToJson(event("SUCCESS")))
                .willReturn(aResponse().withStatus(200)));

        appendToFile(event("SUCCESS"));

        gate.await(10, TimeUnit.SECONDS);
        service.verify(1, postRequestedFor(urlEqualTo("/datastream"))
                .withRequestBody(equalToJson(event("SUCCESS"))));

        // wait till flume reads any responses before shutting down
        new CountDownLatch(1).await(200, TimeUnit.MILLISECONDS);
    }

    @Test
    public void ensureAuditEventsResentOn503Failure() throws Exception {
        final CountDownLatch gate = new CountDownLatch(2);

        service.addMockServiceRequestListener(new RequestListener() {
            public void requestReceived(Request request, Response response) {
                gate.countDown();
            }
        });

        String errorScenario = "Error Scenario";

        service.stubFor(post(urlEqualTo("/datastream"))
                .inScenario(errorScenario)
                .whenScenarioStateIs(STARTED)
                .withRequestBody(equalToJson(event("TRANSIENT_ERROR")))
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("Error Sent"));

        service.stubFor(post(urlEqualTo("/datastream"))
                .inScenario(errorScenario)
                .whenScenarioStateIs("Error Sent")
                .withRequestBody(equalToJson(event("TRANSIENT_ERROR")))
                .willReturn(aResponse().withStatus(200)));

        appendToFile(event("TRANSIENT_ERROR"));

        gate.await(20, TimeUnit.SECONDS);
        service.verify(2, postRequestedFor(urlEqualTo("/datastream"))
                .withRequestBody(equalToJson(event("TRANSIENT_ERROR"))));

        // wait till flume reads any responses before shutting down
        new CountDownLatch(1).await(200, TimeUnit.MILLISECONDS);
    }

    @Test
    public void ensureAuditEventsResentOnNetworkFailure() throws Exception {
        final CountDownLatch gate = new CountDownLatch(2);

        service.addMockServiceRequestListener(new RequestListener() {
            public void requestReceived(Request request, Response response) {
                gate.countDown();
            }
        });

        String errorScenario = "Error Scenario";

        service.stubFor(post(urlEqualTo("/datastream"))
                .inScenario(errorScenario)
                .whenScenarioStateIs(STARTED)
                .withRequestBody(equalToJson(event("NETWORK_ERROR")))
                .willReturn(aResponse().withFault(Fault.RANDOM_DATA_THEN_CLOSE))
                .willSetStateTo("Error Sent"));

        service.stubFor(post(urlEqualTo("/datastream"))
                .inScenario(errorScenario)
                .whenScenarioStateIs("Error Sent")
                .withRequestBody(equalToJson(event("NETWORK_ERROR")))
                .willReturn(aResponse().withStatus(200)));

        appendToFile(event("NETWORK_ERROR"));

        gate.await(10, TimeUnit.SECONDS);
        service.verify(2, postRequestedFor(urlEqualTo("/datastream"))
                .withRequestBody(equalToJson(event("NETWORK_ERROR"))));

        // wait till flume reads any responses before shutting down
        new CountDownLatch(1).await(200, TimeUnit.MILLISECONDS);
    }

    @Test
    public void ensureAuditEventsResentOnConnectionTimeout() throws Exception {
        final CountDownLatch gate = new CountDownLatch(2);

        service.addMockServiceRequestListener(new RequestListener() {
            public void requestReceived(Request request, Response response) {
                gate.countDown();
                service.addSocketAcceptDelay(new RequestDelaySpec(0));
            }
        });

        service.addSocketAcceptDelay(new RequestDelaySpec(CONNECT_TIMEOUT));
        service.stubFor(post(urlEqualTo("/datastream"))
                .withRequestBody(equalToJson(event("SLOW_SOCKET")))
                .willReturn(aResponse().withStatus(200)));

        appendToFile(event("SLOW_SOCKET"));

        gate.await(10, TimeUnit.SECONDS);
        service.verify(2, postRequestedFor(urlEqualTo("/datastream"))
                .withRequestBody(equalToJson(event("SLOW_SOCKET"))));

        // wait till flume reads any responses before shutting down
        new CountDownLatch(1).await(200, TimeUnit.MILLISECONDS);
    }

    @Test
    public void ensureAuditEventsResentOnRequestTimeout() throws Exception {
        final CountDownLatch gate = new CountDownLatch(2);

        service.addMockServiceRequestListener(new RequestListener() {
            public void requestReceived(Request request, Response response) {
                gate.countDown();
            }
        });

        String errorScenario = "Error Scenario";

        service.stubFor(post(urlEqualTo("/datastream"))
                .inScenario(errorScenario)
                .whenScenarioStateIs(STARTED)
                .withRequestBody(equalToJson(event("SLOW_RESPONSE")))
                .willReturn(aResponse().withFixedDelay(RESPONSE_TIMEOUT).withStatus(200))
                .willSetStateTo("Slow Response Sent"));

        service.stubFor(post(urlEqualTo("/datastream"))
                .inScenario(errorScenario)
                .whenScenarioStateIs("Slow Response Sent")
                .withRequestBody(equalToJson(event("SLOW_RESPONSE")))
                .willReturn(aResponse().withStatus(200)));

        appendToFile(event("SLOW_RESPONSE"));

        gate.await(10, TimeUnit.SECONDS);
        service.verify(2, postRequestedFor(urlEqualTo("/datastream"))
                .withRequestBody(equalToJson(event("SLOW_RESPONSE"))));

        // wait till flume reads any responses before shutting down
        new CountDownLatch(1).await(200, TimeUnit.MILLISECONDS);
    }

    public void appendToFile(String line) throws Exception {
        FileWriter fileWriter = new FileWriter(
                "/home/wheels/dev/apache-flume-1.7.0-SNAPSHOT-bin/conf/auditEvents.log", true);
        fileWriter.append(line).append("\n");
        fileWriter.flush();
        fileWriter.close();
    }

    public String event(String id) {
        return "{'id':'" + id + "'}";
    }
}
