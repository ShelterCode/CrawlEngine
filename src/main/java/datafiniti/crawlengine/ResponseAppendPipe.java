package datafiniti.crawlengine;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PipedOutputStream;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;

import datafiniti.utils.GsonUtils;

/**
 * Runnable thread to gather and pipe responses to main thread for writing to
 * file
 * 
 * @author markn
 *
 */
public class ResponseAppendPipe implements Runnable {
	protected static Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());
	PipedOutputStream pipedOutputStream;
	AtomicInteger crawlResponseThreadNumber = new AtomicInteger();
	List<String> urlArray;
	// Stats stats; TODO: add stats later
	Gson gson = GsonUtils.gson;
	String scriptFileName;

	public ResponseAppendPipe(PipedOutputStream pipedOutputStream, List<String> urlArray, String scriptFileName) {
		this.pipedOutputStream = pipedOutputStream;
		this.urlArray = urlArray;
		this.scriptFileName = scriptFileName;
		// this.stats = stats;
	}

	@Override
	public void run() {
		// jsonl is line delimited so use buffered writer
		logger.debug("running pipe");
		try (BufferedWriter bw = new BufferedWriter(
				new OutputStreamWriter(new BufferedOutputStream(pipedOutputStream)))) {
			class CrawlResponseFactory implements ThreadFactory {
				public Thread newThread(Runnable r) {
					return new Thread(r, "CrawlResponseThread-" + crawlResponseThreadNumber.getAndIncrement());
				}
			}
			if (urlArray.size() > 0) {
				ExecutorService responseExecutor = Executors.newCachedThreadPool(new CrawlResponseFactory());
				logger.debug("executor pool instantiated");
				CountDownLatch latch = new CountDownLatch(urlArray.size());
				logger.debug("latch count " + latch.getCount());
				for (String url : urlArray) {
					final String fUrl = url;
					appendResponse(fUrl, bw, responseExecutor, latch, scriptFileName, crawlResponseThreadNumber);

				}
				try {
					logger.debug("latch count " + latch.getCount());
					latch.await();
				} catch (InterruptedException e) {
					logger.error("latch await interupted", e);
				}
				logger.trace("countdown threads {}", latch.getCount());
				responseExecutor.shutdown();
				
			}
			// String statsString = gson.toJson(stats);
			// logger.info(statsString);
			bw.write(gson.toJson(CrawlResponseThread.crawledUrlsInfoMap));
		} catch (IOException e2) {
			logger.error(this.getClass() + " io exception writing responses to piped streams", e2);
		}

	}

	/**
	 * takes the list item from url list and creates a new
	 * {@link CrawlResponseThread} to request the url and append response to
	 * buffered writer
	 * 
	 * @param url
	 * @param crawlResponseThreadNumber
	 * @param bw
	 * @return
	 */
	private void appendResponse(final String url, final BufferedWriter bw, ExecutorService executor, CountDownLatch cdl,
			String scriptFileName, AtomicInteger crawlResponseThreadNumber) {
		logger.debug("append response called");
		CrawlResponseThread crt = new CrawlResponseThread(url, bw, cdl, scriptFileName, crawlResponseThreadNumber, 0);
		executor.submit(crt);
	}

}
