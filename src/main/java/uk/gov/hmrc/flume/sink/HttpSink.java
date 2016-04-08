package uk.gov.hmrc.flume.sink;

import org.apache.flume.*;
import org.apache.flume.conf.Configurable;
import org.apache.flume.sink.AbstractSink;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * HTTP Sink Implementation for Apache Flume.
 *
 * Behaviour of this sink is that it will take events from the channel, and
 * send those events to a remote service using an HTTP POST request. The event
 * content is sent as the POST body.
 *
 * Configurable options are :
 * endpoint - the fully qualified URL endpoint to POST to (required, no default)
 * connectTimeout - the socket connection timeout (default 5000ms)
 * requestTimeout - the maximum request processing time (default 5000ms)
 * contentTypeHeader - the HTTP Content-Type header (default text/plain)
 * acceptHeader - the HTTP Accept header value (default text/plain)
 *
 * Error handling behaviour of this sink depends on the HTTP response returned
 * by the target server. Any transient error will be retried forever, and any
 * invalid events will be discarded.
 *
 * Status code 200 (OK) responses result in successful consumption of the event.
 *
 * Status code 503 (UNAVAILABLE) responses result in a backoff signal being
 * returned by the sink, and the event is not consumed from the channel.
 *
 * Status codes in the range of 400-499 result in the event being consumed from
 * the channel and discarded, as they are considered to be corrupt events that
 * can never be successfully sent to the HTTP endpoint. An error log message is
 * created for each of these failed events.
 *
 * Any malformed HTTP response returned by the server where the status code is
 * not readable will result in a backoff signal and the event is not consumed
 * from the channel.
 *
 * Any other HTTP response will result in an error log being created, and the
 * event being consumed from the channel.
 */
public class HttpSink extends AbstractSink implements Configurable {

	private static final Logger LOG = Logger.getLogger(HttpSink.class);

	private static final int DEFAULT_CONNECT_TIMEOUT = 5000;
	private static final int DEFAULT_REQUEST_TIMEOUT = 5000;
	private static final String DEFAULT_CONTENT_TYPE = "text/plain";
	private static final String DEFAULT_ACCEPT_HEADER = "text/plain";

	private URL endpointUrl;
	private HttpURLConnection httpClient;
	private int connectTimeout = DEFAULT_CONNECT_TIMEOUT;
	private int requestTimeout = DEFAULT_REQUEST_TIMEOUT;
	private String contentTypeHeader = DEFAULT_CONTENT_TYPE;
	private String acceptHeader = DEFAULT_ACCEPT_HEADER;

	public void configure(Context context) {
		String configuredEndpoint = context.getString("endpoint", "");
		LOG.info("Read endpoint URL from configuration : " + configuredEndpoint);

		try {
			endpointUrl = new URL(configuredEndpoint);
		} catch (MalformedURLException e) {
			throw new IllegalArgumentException("Endpoint URL invalid", e);
		}

		String configuredConnectTimeout = context.getString("connectTimeout", "");
		LOG.info("Read connect timeout from configuration : " + configuredConnectTimeout);

		if (!configuredConnectTimeout.isEmpty()) {
			connectTimeout = Integer.parseInt(configuredConnectTimeout);
		}

		String configuredRequestTimeout = context.getString("requestTimeout", "");
		LOG.info("Read request timeout from configuration : " + configuredRequestTimeout);

		if (!configuredRequestTimeout.isEmpty()) {
			requestTimeout = Integer.parseInt(configuredRequestTimeout);
		}

		String configuredAcceptHeader = context.getString("acceptHeader", "");
		LOG.info("Read Accept header value from configuration : " + configuredAcceptHeader);

		if (!configuredAcceptHeader.isEmpty()) {
			acceptHeader = configuredAcceptHeader;
		}

		String configuredContentTypeHeader = context.getString("contentTypeHeader", "");
		LOG.info("Read Content-Type header value from configuration : " + configuredContentTypeHeader);

		if (!configuredContentTypeHeader.isEmpty()) {
			contentTypeHeader = configuredContentTypeHeader;
		}
	}

	@Override
	public void start() {
		LOG.info("Starting HttpSink");
	}

	@Override
	public void stop() {
		LOG.info("Stopping HttpSink");
	}

	public Status process() throws EventDeliveryException {
		Status status = null;

		Channel ch = getChannel();
		Transaction txn = ch.getTransaction();
		txn.begin();
		try {
			Event event = ch.take();
			if (event != null && event.getBody().length > 0) {

				LOG.debug("Sending request : " + new String(event.getBody()));

				try {
					httpClient = (HttpURLConnection) endpointUrl.openConnection();
					httpClient.setRequestMethod("POST");
					httpClient.setRequestProperty("Content-Type", contentTypeHeader);
					httpClient.setRequestProperty("Accept", acceptHeader);
					httpClient.setConnectTimeout(connectTimeout);
					httpClient.setReadTimeout(requestTimeout);
					httpClient.setDoOutput(true);
					httpClient.setDoInput(true);
					httpClient.connect();

					OutputStream outputStream = httpClient.getOutputStream();
					outputStream.write(event.getBody());
					outputStream.flush();
					outputStream.close();

					int statusCode = httpClient.getResponseCode();
					LOG.debug("Got status code : " + statusCode);

					if (statusCode == HttpURLConnection.HTTP_OK) {
						txn.commit();
						status = Status.READY;

						LOG.debug("Successful write, event consumed");

					} else if (statusCode == HttpURLConnection.HTTP_UNAVAILABLE) {
						txn.rollback();
						status = Status.BACKOFF;

						LOG.debug("Service Unavailable (503), retrying");

					} else if (statusCode >= 400 && statusCode < 500) {
						txn.commit();
						status = Status.READY;

						LOG.error(String.format("Bad request, returned status code %s, event consumed", statusCode));

					} else if (statusCode == -1) {
						txn.rollback();
						status = Status.BACKOFF;

						LOG.debug("Malformed response returned from server, retrying");

					} else {
						txn.commit();
						status = Status.READY;

						LOG.error(String.format("Unexpected status code %s returned for event", statusCode));
					}

				} catch (IOException e) {
					txn.rollback();

					LOG.error("Error opening connection", e);

					status = Status.BACKOFF;

				} finally {
					httpClient.disconnect();

					LOG.debug("Connection Closed");
				}

			} else {
				txn.commit();
				status = Status.BACKOFF;

				LOG.debug("Processed empty event.");
			}

		} catch (Throwable t) {
			txn.rollback();

			LOG.error("Error sending HTTP request, retrying", t);

			status = Status.BACKOFF;

			// re-throw all Errors
			if (t instanceof Error) {
				throw (Error)t;
			}

		} finally {
			txn.close();
		}

		return status;
	}
}
