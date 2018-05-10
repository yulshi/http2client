package com.yulong.http2.client;

import java.io.IOException;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.Test;

import com.yulong.http2.client.Connection.StartBy;
import com.yulong.http2.client.common.SettingsRegistry;
import com.yulong.http2.client.frame.DataFrame;
import com.yulong.http2.client.frame.HeadersFrame;
import com.yulong.http2.client.frame.SettingsFrame;
import com.yulong.http2.client.message.Http2Headers;

/**
 * Unit test for simple App.
 */
public class AppTest {

	@Test
	public void test() {

		Http2Client client = new Http2Client();

		byte[] cf = client.createConnectionPreface(new SettingsFrame(new TreeMap<SettingsRegistry, Integer>() {
			{
				put(SettingsRegistry.INITIAL_WINDOW_SIZE, 512);
			}
		}));

		try (Connection conn = client.openConnection(StartBy.alpn, "localhost", 8443, cf)) {
			//try (Connection conn = client.openConnection(StartBy.prior_knowledge, "localhost", 8080)) {
			//try (Connection conn = client.openConnection(StartBy.upgrade, "localhost", 8080)) {

			//			conn.settings(new SettingsFrame(new TreeMap<SettingsRegistry, Integer>() {
			//				{
			//					put(SettingsRegistry.INITIAL_WINDOW_SIZE, 512);
			//				}
			//			}));

			// Send a GET request on a new stream:
			try (Stream stream = conn.newStream()) {

				Http2Headers headers = new Http2Headers("GET", "/testweb/echo?msg=hello", "localhost", "https");
				headers.add("test-header1", "header1-value");
				byte[] headerBlock = conn.encode(headers);

				stream.headers(new HeadersFrame(stream.getId(), true, true, headerBlock));
				//System.out.println(stream.latestReceivedFrame());

				//conn.send(new WindowUpdateFrame(stream.getId(), 8));
				//System.out.println(stream.latestReceivedFrame());

				//conn.send(new WindowUpdateFrame(stream.getId(), 8));
				//System.out.println(stream.latestReceivedFrame());

				System.out.println(stream.getResponseFuture().get(2L, TimeUnit.SECONDS));

			} catch (InterruptedException | ExecutionException | TimeoutException e) {
				e.printStackTrace();
			}

			// Send a POST request on a new stream:
			try (Stream stream = conn.newStream()) {

				Http2Headers headers = new Http2Headers("POST", "/testweb/echo", "localhost", "http");
				stream.headers(new HeadersFrame(stream.getId(), false, true, conn.encode(headers)));

				stream.data(new DataFrame(stream.getId(), true, "Hello World!".getBytes()));

				System.out.println(stream.getResponseFuture().get(2L, TimeUnit.SECONDS));

			} catch (InterruptedException | ExecutionException | TimeoutException e) {
				e.printStackTrace();
			}

			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {
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

	}

}
