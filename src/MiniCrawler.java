import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;


public class MiniCrawler {
	
	/**
	 * @param start_page
	 * @param crawl_depth
	 * @param link_amount
	 */
	public static void test_config(String start_page, int crawl_depth, int link_amount) {
		System.out.println("Crawling starts at '" + start_page + "'");
		System.out.println("Following links until depth of " + crawl_depth);
		System.out.println("Gathering " + link_amount + " links per page");

		//load the document
		Document article = null;
		try {
			article = load_doc_from_url(start_page);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		//let's have a look at the beginning of the document text
		System.out.println(article.text().substring(0, 220));

		System.out.println(get_article_title(article));
		
		System.out.println(get_links(article, link_amount));
	}
	
	/**
	 * Load a url and return the parsed html document
	 * 
	 * @param url	an absolute url giving the base location of a page 
	 * @return a Jsoup Document representing the website behind the url
	 * @throws IOException
	 */
	public static Document load_doc_from_url(String url) throws IOException {
		return Jsoup.connect(url).get();
	}
	
	/**
	 * find the h1 headline which is the title of the article
	 * 
	 * @param article
	 * @return title string of the article
	 */
	public static String get_article_title(Document article) {
		return article.select("h1").first().text();
	}
	
	/**
	 * Gathers the first N links in the article and returns them
	 * 
	 * @param article the object storing the parsed html of the Wikipedia page 
	 * @param link_amount number of links that should be retrieved from the Wikipedia page
	 * @return	HashSet containing urls
	 */
	public static HashSet<String> get_links(Document article, int link_amount) {
		HashSet<String> links = new HashSet<String>();
		
		Element content = article.getElementById("mw-content-text");
		Elements linklist = content.getElementsByTag("a");
		for (int i = 0; i < link_amount; i++) {
			links.add("https://en.wikipedia.org"+linklist.get(i).attr("href"));
		}
		return links;
	}
	
	/**
	 * crawls the urls in the queue, adds the found links to the article_link_dict and adds them to the queue.
	 * Repeats this process until either the queue is empty or the maximum crawl depth is reached.
	 * 
	 * @param queue	
	 * @param crawl_depth
	 * @param link_amount
	 * @param article_link_dict
	 * @throws IOException 
	 */
	public static HashMap<String,HashSet<String>> crawl_wp(HashSet<String> queue, int crawl_depth, int max_crawl_depth, int link_amount,
		HashMap<String, HashSet<String>> article_link_dict) throws IOException {
		crawl_depth++;
		//break if max crawl depth is reached
		if (crawl_depth>max_crawl_depth) {
			return new HashMap<String,HashSet<String>>();
		}
		
		System.out.println("in crawl depth "+crawl_depth);
		
		HashSet<String> newQueue = new HashSet<String>();
				
		for (Iterator iterator = queue.iterator(); iterator.hasNext();) {
			String url = (String) iterator.next();
			System.out.println("crawling article "+url+"...");
			Document article = load_doc_from_url(url);
			String title = get_article_title(article);
			HashSet<String> links = get_links(article, link_amount);
			article_link_dict.put(title, links);
			newQueue.addAll(links);
		}
		//recursively call method
		article_link_dict.putAll(crawl_wp(newQueue, crawl_depth, max_crawl_depth, link_amount, article_link_dict));
		return article_link_dict;
	}

	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException{
		String base_uri = "https://en.wikipedia.org/wiki/";
		//The page where we start crawling
		String start_page = base_uri + "Information_extraction";
		//how deep should we follow the links?
		int crawl_depth = 3;
		//how many links per page should we find?
		int link_amount = 3;
		
		//test if your program works
		//test_config(start_page, crawl_depth, link_amount);
				
		//Storage for the results. Article titles are the key, a HashSet containing the links is the value
		HashMap<String,HashSet<String>> article_link_dict = new HashMap<String,HashSet<String>>();

		//define and fill initial queue
		HashSet<String> queue = new HashSet<String>();
		queue.add(start_page);
		
		//fill the article_link_dict by starting the crawl with the initialized queue
		HashMap<String,HashSet<String>> result = crawl_wp(queue, 0, crawl_depth, link_amount, article_link_dict);
		System.out.println(result);
	}
}
