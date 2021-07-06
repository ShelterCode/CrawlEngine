package datafiniti.utils;

import org.apache.http.HttpResponse;
import org.apache.http.client.ServiceUnavailableRetryStrategy;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.fluent.Executor;
import org.apache.http.config.SocketConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.protocol.HttpContext;

public class FluentExecutorUtil {
	private static final int REQUEST_TIMEOUT = 15000;

	/**
	 * sets up a fluent executor with timeouts
	 * 
	 * @return
	 */
	public static Executor getFluentExecutor() {
		RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(REQUEST_TIMEOUT)
				.setSocketTimeout(REQUEST_TIMEOUT).build();
		SocketConfig socketConfig = SocketConfig.custom().setSoTimeout(REQUEST_TIMEOUT).build();
		RetryHandler retryHandler = new RetryHandler();
		CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(requestConfig)
				.setRetryHandler(retryHandler)
				.setServiceUnavailableRetryStrategy(new ServiceUnavailableRetryStrategy() {
					int waitPeriod = 100;

					@Override
					public boolean retryRequest(HttpResponse response, int executionCount, HttpContext context) {
						waitPeriod *= 2;
						return executionCount <= RetryHandler.HTTP_RETRY_COUNT
								&& response.getStatusLine().getStatusCode() >= 500; // important!
					}

					@Override
					public long getRetryInterval() {
						return waitPeriod;
					}
				}).setDefaultSocketConfig(socketConfig).build();

		Executor executor = Executor.newInstance(client);
		return executor;
	}
}
