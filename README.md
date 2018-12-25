# HTTP/2 Client Utility
An utility written in Java that help you communicate with a ready HTTP/2 server.

## Build and Install
```
mvn clean install -Dmaven.test.skip=true
```
## Usage
### High Leve API
```java
try (Client client = newClient()) {

  Http2Request request = client.newRequestBuilder()
      .post("/url/path/to/resource")
      .entity(new Params()
          .setTextParameter("name", "Alice")
          .setTextParameter("age", "30")
      .build();

  Http2Response response = client.send(request);
  System.out.println(response.getContent());

} catch (Exception e) {
  e.printStackTrace(System.out);
}
```
### Low Level API
```java
ConnectionFactory cf = new ConnectionFactory();

try (Connection conn = cf.create(StartBy.alpn, host, port)) {

  try (Stream stream = connection.newStream()) {

    byte[] data = ...

    // Send HEADERS frame with POST method:
    Http2Headers http2Headers = new Http2Headers(conn, "POST", path);
    http2Headers.add("Content-Type", "application/x-www-form-urlencoded;charset=utf-8");
    http2Headers.add(""Content-Length", data.length);

    // Send HEADERS frame:
    byte[] headerBlock = http2Headers.toHeaderBlock();
    HeadersFrame headersFrame = new HeadersFrame(stream.getId(), false, true, headerBlock);
    stream.headers(headersFrame);

    // Send DATA frame:
    DataFrame dataFrame = new DataFrame(stream.getId(), true, data);
    stream.data(dataFrame);

    // Check stream response:
    Http2Response response = stream.getResponse();
		System.out.println(response.getContent());

    } catch (IOException | ConnectionException e) {
      e.printStackTrace(System.out);
    }
  }
}
```
