/**
 * 
 */
package datafiniti.crawlengine;

import java.util.ArrayList;


import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

/**
 * @author markn
 *
 */
public class CrawlEngine {
	
	
	public CrawlEngine(JsonElement request) {
		CrawlFileWriter crawlFileWriter = new CrawlFileWriter();
		crawlFileWriter.crawlAndWriteList(request);
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		JsonObject request = new JsonObject();
		JsonArray urlList = new JsonArray();
		urlList.add("https://datafiniti.co/");
		request.add("urlList", urlList);
		request.addProperty("script", "returnUseCases.js");
		CrawlEngine cE = new CrawlEngine(request);
		
	}

}
