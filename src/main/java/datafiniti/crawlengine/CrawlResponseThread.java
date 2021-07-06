package datafiniti.crawlengine;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.message.BasicHeader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.MarkerManager.Log4jMarker;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.openjdk.nashorn.api.scripting.NashornScriptEngine;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import datafiniti.utils.FluentExecutorUtil;
import datafiniti.utils.GsonUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * gets a single response and writes to stream if valid
 * 
 * @author markn
 *
 */
public class CrawlResponseThread implements Runnable {
	private static final int MAX_REQUEST_COUNT = 200;
	protected static Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());
	static ConcurrentMap<String, Integer> crawledUrlsInfoMap = new ConcurrentHashMap<String, Integer>();
	protected static ScriptEngine engine = new ScriptEngineManager().getEngineByName("graal.js");
	class CrawlResponseFactory implements ThreadFactory {
		public Thread newThread(Runnable r) {
			return new Thread(r,
					"CrawlResponseThread-" + crawlResponseThreadNumber.getAndIncrement());
		}
	}
	ExecutorService recurseExecutor = Executors.newCachedThreadPool(new CrawlResponseFactory());
	String url;
	Gson gson = GsonUtils.gson;
	BufferedWriter bw;
	CountDownLatch cdl;
	String scriptFileName;
	AtomicInteger crawlResponseThreadNumber;
	int recursionLevel;

	private static String removeAnchor(String href) {
		int index = href.indexOf("#");
		if (index == -1) {
			return href;
		}
		return (href.substring(0, index));
	}

	private URI getUrlWithoutParameters(String url) throws URISyntaxException {
		URI uri = new URI(url);
		logger.debug("url {}", url);
		return new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), null, // Ignore the query part of the input
																					// url
				uri.getFragment());
	}

	public CrawlResponseThread(String url, BufferedWriter bw, CountDownLatch cdl, String scriptFileName,
			AtomicInteger crawlResponseThreadNumber, int recursionLevel) {

		this.url = removeAnchor(url);
		this.bw = bw;
		this.cdl = cdl;
		this.scriptFileName = scriptFileName;
		this.crawlResponseThreadNumber = crawlResponseThreadNumber;
		this.recursionLevel = recursionLevel;
	}

	@Override
	public void run() {
		try {

			logger.trace("CrawlFileWriter: appendResponse: Thread " + Thread.currentThread().getName() + " Running");
			String response = null;
			long requestTimeStart = System.currentTimeMillis();
			try {
				URI thisUri = getUrlWithoutParameters(this.url);
				if (crawledUrlsInfoMap.put(thisUri.toString(),
						crawledUrlsInfoMap.getOrDefault(thisUri.toString(), 1)) == null) {

					response = getResponse(thisUri.toString());
				} else {
					crawledUrlsInfoMap.compute(thisUri.toString(), (key, value) -> value++);
				}
			} catch (Exception e) {
				// we expect a lot of these errors so move to trace
				logger.debug("CrawlFileWriter: appendResponse: Error getting data for {}", url, e);
				return;
			}

			if (response != null) {

				File scriptFile = new File("scripts" + File.separator + scriptFileName);

				Map<String, String> options = new HashMap<>();
				options.put("js.commonjs-require", "true");
				options.put("js.commonjs-require-cwd", ".");
				Context cx = Context.newBuilder("js").allowExperimentalOptions(true).allowIO(true).options(options)
						.build();
				String scriptContents=Files.readString(scriptFile.toPath());
				Value parseResponseFunction = cx.eval("js", scriptContents);
				
				JsonObject metaData = new JsonObject();
				metaData.addProperty("requestedUrl", url);
				metaData.addProperty("recursionLevel", recursionLevel);
				String parsedResponse = parseResponseFunction.execute(response, metaData.toString()).asString();
				JsonElement parsedElement = null;
				try {
					parsedElement = gson.fromJson(parsedResponse, JsonElement.class);
				} catch (JsonSyntaxException e) {
					// we expect these but log to trace
					logger.trace("expected json syntax exception ", e);
				}
				if (crawlResponseThreadNumber.get() < MAX_REQUEST_COUNT) {

					parsedElement = recurseUrls(parsedElement);
				} else {
					logger.debug("max request count hit finishing up");
				}
				if (parsedElement != null) {
					try { // quick and dirty to not include a parsed response in file but still traverse
							// recurseUrls
						if (parsedElement.getAsJsonObject().get("command").getAsJsonObject().get("exclude")
								.getAsBoolean()) {
							return;
						}
					} catch (Exception e) {
						// ignore
					}
					parsedResponse = parsedElement.toString();
				}
				logger.trace("parsedResponse {}", parsedResponse);
				try {
					synchronized (bw) {
						bw.write(parsedResponse);
						bw.newLine();
						bw.flush();
					}
				} catch (IOException e) {
					logger.error("CrawlFileWriter: appendResponse: io exception writing to buffer, url = {}", url, e);
				}
			}
		} catch (Exception e) {
			logger.error("error in crawl response processing", e);
		} finally {
			cdl.countDown();
			logger.debug("cdl latch = {}", cdl.getCount());
		}

	}

	private JsonElement recurseUrls(JsonElement parsedElement) {
		logger.trace("parsedElement {}", parsedElement);
		if (parsedElement != null && parsedElement.isJsonObject()
				&& parsedElement.getAsJsonObject().get("recurseUrlList") != null
				&& parsedElement.getAsJsonObject().get("recurseUrlList").isJsonArray()
				&& crawlResponseThreadNumber.get() < MAX_REQUEST_COUNT) {
			logger.debug("threadcount {}", crawlResponseThreadNumber.get());
			JsonArray urlArray = parsedElement.getAsJsonObject().get("recurseUrlList").getAsJsonArray();

			logger.debug("urlArray size {}", urlArray.size());
			if (urlArray.size() > 0) {
				try {

					CountDownLatch latch = new CountDownLatch(urlArray.size());
					logger.debug("latch count " + latch.getCount());
					for (JsonElement url : urlArray) {
						boolean crawlThreadCrawled = false;
						try {
							if (crawlResponseThreadNumber.get() >= MAX_REQUEST_COUNT) {
								continue;
							}
							
							
							final String fUrl = url.getAsString();

							URI thisUri = getUrlWithoutParameters(this.url);
							URI uri = thisUri.resolve(getUrlWithoutParameters(removeAnchor(fUrl)));
							logger.debug("furl {}, url {}, thisUri {}, uri {}", fUrl, url, thisUri, uri);
							logger.trace("parsedElement {}", parsedElement);

							if (crawledUrlsInfoMap.get(uri.toString()) == null) {
								try {
									CrawlResponseThread crt = new CrawlResponseThread(uri.toString(), bw, latch,
											scriptFileName, crawlResponseThreadNumber, recursionLevel + 1);
									crawlThreadCrawled = true;
									recurseExecutor.submit(crt);
								} catch (Exception e) {
									// latch already counted down in finally
									logger.error("error recursing response thread", e);
								}

							}
						} catch (Exception e) {
							logger.error("error recursing responses {}", e);

						} finally {
							if (!crawlThreadCrawled) {
								latch.countDown();
								logger.debug("finally latch count {}", latch.getCount());
							}
						}
					}
					try {
						logger.debug("latch count " + latch.getCount());
						latch.await();
					} catch (InterruptedException e) {
						logger.error("latch await interupted", e);
					}
				} finally {

				}
			}
		}
		return parsedElement;
	}

	/**
	 * gets body content of response as string for given url
	 * 
	 * @param url the url to get response from
	 * @return
	 * @throws ClientProtocolException
	 * @throws IOException
	 * @throws HttpResponseException
	 */
	private String getResponse(final String url) throws ClientProtocolException, IOException, HttpResponseException {
		String response;
		Executor executor = FluentExecutorUtil.getFluentExecutor();
		Header userAgent = new BasicHeader("user-agent",
				"Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/39.0.2171.71 Safari/537.36");

		Response executorResponse = executor.execute(Request.Get(url).setHeader(userAgent));
		HttpResponse httpResponse = executorResponse.returnResponse();
		String statusGroup = (httpResponse.getStatusLine().getStatusCode() + "").substring(0, 1);
		response = new BasicResponseHandler().handleResponse(httpResponse);
		executorResponse.discardContent();
		return response;
	}

}
