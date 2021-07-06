package datafiniti.utils;

import java.io.IOException;
import java.lang.invoke.MethodHandles;

import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.protocol.HttpContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RetryHandler implements HttpRequestRetryHandler {
	protected static Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());
	public static Integer HTTP_RETRY_COUNT = 1;

	public boolean retryRequest(IOException exception, int executionCount, HttpContext context) {
		logger.error("retrying request", exception);
		if (executionCount >= HTTP_RETRY_COUNT) {
			return false; // Do not retry if over max retry count
		}
		HttpClientContext clientContext = HttpClientContext.adapt(context);
		org.apache.http.HttpRequest request = clientContext.getRequest();
		logger.debug("retrying url {}", request.getRequestLine().getUri());
		boolean idempotent = !(request instanceof HttpEntityEnclosingRequest);
		if (idempotent) {
			return true; // Retry if the request is considered idempotent
		}
		return false;
	}
}
