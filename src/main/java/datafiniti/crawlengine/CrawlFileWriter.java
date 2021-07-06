package datafiniti.crawlengine;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;

import datafiniti.utils.GsonUtils;

/**
 * crawls a list of urls and writes ones that contain expected
 * {@link ScrapedData} schema to file
 * 
 * @author markn
 *
 */
public class CrawlFileWriter {

	protected static Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());
	final Gson gson = GsonUtils.gson;
	
	class RespondPipeFactory implements ThreadFactory {
		public Thread newThread(Runnable r) {
			return new Thread(r, "ResponseAppendPipe");
		}
	}
	ExecutorService executor = Executors.newSingleThreadExecutor(new RespondPipeFactory());

	/**
	 * crawls the list of urls and writes gathered responses to files based on crawl
	 * request uuid
	 * 
	 * @param urlList
	 * @param fileName
	 */
	public void crawlAndWriteList(JsonElement request) {
		long startTime = System.currentTimeMillis();

		String crawlUUID = UUID.randomUUID().toString();
		File crawlDir = new File(crawlUUID); // TODO: change to S3 later
		crawlDir.mkdir();
		File requestFile = new File(crawlUUID + File.separator + "request.json"); //TODO: move to mongo?
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(requestFile))) {
			writer.write(request.toString());
		} catch (IOException e) {
			logger.error("IOException writing request file. request = {}", request.toString(), e);
		}
		
		File scriptFile = new File("scripts" + File.separator + request.getAsJsonObject().get("script").getAsString());
		String scriptContents = null;
		try {
			scriptContents=Files.readString(scriptFile.toPath());
		} catch (IOException e) {
			logger.error("exception reading scriptfile",e);
		}
		String fileName = crawlUUID + File.separator + "script.js";
		File scriptFileCopy = new File(fileName);
		
		try {
			Files.writeString(scriptFileCopy.toPath(), scriptContents, StandardCharsets.UTF_8);
		} catch (IOException e) {
			logger.error("exception writing copy of script file to crawl directory", e);
		}
		List<String> urlList = gson.fromJson(request.getAsJsonObject().get("urlList"),new TypeToken<List<String>>() {}.getType()) ;
		try (InputStream is = gatherResponses(gson, urlList,request.getAsJsonObject().get("script").getAsString() )) {
			File file = new File(crawlUUID+File.separator+"parsedResults.jsonl");
			java.nio.file.Files.copy(is, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e1) {
			logger.error(this.getClass() + " io exception reading responses for crawlUUID {} request {}", crawlUUID, request, e1);
			System.exit(1);
		}
		logger.info(this.getClass() + " : crawlAndWriteList total runtime millis "
				+ (System.currentTimeMillis() - startTime));
		
	}

	/**
	 * gathers any responses matching expected {@link ScrapedData} schema from list
	 * of urls in to a piped input/output stream in separate threads
	 * 
	 * @param gson
	 * @param urlArray                  the list of urls to crawl
	 * @param sb                        the string buffer to gather responses in to
	 * @param threadCount
	 * @param crawlResponseThreadNumber
	 */
	private InputStream gatherResponses(final Gson gson, final List<String> urlArray, String scriptFileName) {
		final PipedInputStream pipedInputStream = new PipedInputStream();
		final PipedOutputStream pipedOutputStream = new PipedOutputStream();
		/* Connect pipe */
		logger.debug("gathering responses");
		try {
			pipedInputStream.connect(pipedOutputStream);
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		pipeResponses(gson, urlArray, pipedOutputStream, scriptFileName);
		return pipedInputStream;
	}

	/**
	 * creates a new thread from {@link ResponseAppendPipe} runnable to pipe
	 * responses to input stream
	 * 
	 * @param gson
	 * @param urlArray
	 * @param threadCount
	 * @param crawlResponseThreadNumber
	 * @param pipedOutputStream
	 */
	private void pipeResponses(final Gson gson, final List<String> urlArray,
			final PipedOutputStream pipedOutputStream, String scriptFileName) {
		ResponseAppendPipe rap = new ResponseAppendPipe(pipedOutputStream, urlArray, scriptFileName);
		executor.submit(rap);
		

	}
	
}
