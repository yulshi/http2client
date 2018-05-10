# HTTP/2 Client Utility
An utility written in Java that help you communicate with a ready HTTP/2 server.

## Build and Install
```
mvn clean install
```
## Usage
```java
// Create a HTTP/2 client instance:
Http2Client client = new Http2Client();

// Configure HTTP/2 connection properties through SETTINGS frame:
byte[] cf = client.createConnectionPreface(new SettingsFrame(new TreeMap<SettingsRegistry, Integer>() {
  {
  put(SettingsRegistry.INITIAL_WINDOW_SIZE, 512);
  }
}));

// Initiate a HTTP/2 connection:
try (Connection conn = client.openConnection(StartBy.alpn, "localhost", 8443, cf)) {

  // Open a stream:
  try (Stream stream = conn.newStream()) {

  // Construct a HTTP/2 headers block:
  Http2Headers headers = new Http2Headers("GET", "/testweb/echo?msg=hello", "localhost", "https");
  headers.add("test-header1", "header1-value");
  byte[] headerBlock = conn.encode(headers);

  // Send a HTTP/2 HEADERS frame:
  stream.headers(new HeadersFrame(stream.getId(), true, true, headerBlock));

  // Print out the HTTP/2 response:
  System.out.println(stream.getResponseFuture().get(2L, TimeUnit.SECONDS));

  } catch (InterruptedException | ExecutionException | TimeoutException e) {
  e.printStackTrace();
  }
  
} catch (IOException e) {
  System.out.println("Close connection failed: " + e);
} catch (Http2StartingException e) {
  System.out.println(e.getMessage());
  e.printStackTrace(System.out);
} catch (ConnectionException e) {
  System.out.println(e.GetError().name() + ":" + e.getMessage());
  e.printStackTrace(System.out);
}
```
