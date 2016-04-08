# Flume Http Sink

HTTP Sink Implementation for Apache Flume.

Behaviour of this sink is that it will take events from the channel, and
send those events to a remote service using an HTTP POST request. The event
content is sent as the POST body.

Error handling behaviour of this sink depends on the HTTP response returned
by the target server. Any transient error will be retried forever, and any
invalid events will be discarded.

Status code 200 (OK) responses result in successful consumption of the event.

Status code 503 (UNAVAILABLE) responses result in a backoff signal being
returned by the sink, and the event is not consumed from the channel.

Status codes in the range of 400-499 result in the event being consumed from
the channel and discarded, as they are considered to be corrupt events that
can never be successfully sent to the HTTP endpoint. An error log message is
created for each of these failed events.

Any malformed HTTP response returned by the server where the status code is
not readable will result in a backoff signal and the event is not consumed
from the channel.

Any other HTTP response will result in an error log being created, and the
event being consumed from the channel.


### Configuration Options

 Name            | Default          | Description
:----------------|:-----------------|:-----------------
endpoint         | no default       | the fully qualified URL endpoint to POST to
connectTimeout   | 5000ms           | the socket connection timeout
requestTimeout   | 5000ms           | the maximum request processing time
contentTypeHeader| text/plain       | the HTTP Content-Type header
acceptHeader     | text/plain       | the HTTP Accept header value


### Configuration Example
An example flume-conf.properties section for this sink :
```
agent.sinks.httpSink.type = uk.gov.hmrc.flume.sink.HttpSink
agent.sinks.httpSink.channel = fileChannel
agent.sinks.httpSink.endpoint = http://localhost:8080/someuri
agent.sinks.httpSink.connectTimeout = 2000
agent.sinks.httpSink.requestTimeout = 2000
agent.sinks.httpSink.acceptHeader = application/json
agent.sinks.httpSink.contentTypeHeader = application/json
```


### Installation
This project is built using SBT so that it works with the rest of the HMRC
build and release tooling. We're aware that this is a little bit odd.

```
git clone git@github.com:hmrc/flume-http-sink.git
cd flume-http-sink
sbt clean package
cp target/flume-http-sink-{version}.jar {flume_home}/lib/flume-http-sink-{version}.jar
```


License
=======
Apache License, Version 2.0
http://www.apache.org/licenses/LICENSE-2.0

