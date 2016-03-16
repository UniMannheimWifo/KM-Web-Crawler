import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class Search {

	private static String BASE_URI = "https://en.wikipedia.org/wiki/";
	// The page where we start crawling
	private static String START_PAGE = BASE_URI + "Rose_Cheruiyot";
	// how deep should we follow the links?
	private static int MAX_CRAWL_DEPTH = 4;
	// how many links per page should we find?
	private static int LINK_AMOUNT = 5;

	/**
	 * Load a url and return the parsed html document
	 * 
	 * @param url
	 *            an absolute url giving the base location of a page
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
	 * @param article
	 *            the object storing the parsed html of the Wikipedia page
	 * @param link_amount
	 *            number of links that should be retrieved from the Wikipedia
	 *            page
	 * @return HashSet containing urls
	 */
	public static HashSet<String> get_links(Document article, int link_amount) {
		HashSet<String> links = new HashSet<String>();

		Element content = article.getElementById("mw-content-text");
		Elements linklist = content.getElementsByTag("a");
		for (int i = 0; i < link_amount; i++) {
			links.add("https://en.wikipedia.org" + linklist.get(i).attr("href"));
		}
		return links;
	}

	/**
	 * crawls the urls in the queue, adds the found links to the
	 * article_link_dict and adds them to the queue. Repeats this process until
	 * either the queue is empty or the maximum crawl depth is reached.
	 * 
	 * @param queue
	 * @param crawl_depth
	 * @param link_amount
	 * @param w
	 * @throws IOException
	 */
	public static HashMap<String, HashSet<String>> crawl_wp(HashSet<String> queue, int crawl_depth, int max_crawl_depth,
			int link_amount, IndexWriter w) throws IOException {
		crawl_depth++;
		// break if max crawl depth is reached
		if (crawl_depth > max_crawl_depth) {
			return null;
		}

		System.out.println("in crawl depth " + crawl_depth);

		HashSet<String> newQueue = new HashSet<String>();

		for (Iterator iterator = queue.iterator(); iterator.hasNext();) {
			String url = (String) iterator.next();
			System.out.println("crawling article " + url + "...");
			try {
				Document article = load_doc_from_url(url);
				String title = get_article_title(article);
				HashSet<String> links = get_links(article, link_amount);
				addDoc(w, title, url);
				newQueue.addAll(links);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		// recursively call method
		return crawl_wp(newQueue, crawl_depth, max_crawl_depth, link_amount, w);
	}

	private static void addDoc(IndexWriter w, String title, String link) throws IOException {
		org.apache.lucene.document.Document doc = new org.apache.lucene.document.Document();
		doc.add(new TextField("title", title, Field.Store.YES));

		// use a string field for isbn because we don't want it tokenized
		doc.add(new TextField("link", link, Field.Store.YES));
		w.addDocument(doc);
	}

	private static void print(ScoreDoc[] hits, IndexSearcher searcher) throws IOException {
		// 4. display results
		System.out.println("Found " + hits.length + " hits.");
		for (int i = 0; i < hits.length; ++i) {
			int docId = hits[i].doc;
			org.apache.lucene.document.Document d = searcher.doc(docId);
			System.out.println((i + 1) + ". " + "\t" + hits[i].score + "\t" + d.get("title") + " (" + d.get("link") + ")");
		}
	}

	/**
	 * @param args
	 * @throws IOException
	 * @throws ParseException
	 */
	public static void main(String[] args) throws IOException, ParseException {
		// The same analyzer should be used for indexing and searching
		StandardAnalyzer analyzer = new StandardAnalyzer(Version.LUCENE_40);

		// 1. create the index
		Directory index = new RAMDirectory();

		IndexWriterConfig config = new IndexWriterConfig(Version.LUCENE_40, analyzer);

		IndexWriter w = new IndexWriter(index, config);

		// define and fill initial queue
		HashSet<String> queue = new HashSet<String>();
		queue.add(START_PAGE);

		// fill the article_link_dict by starting the crawl with the initialized
		// queue
		crawl_wp(queue, 0, MAX_CRAWL_DEPTH, LINK_AMOUNT, w);
		w.close();
		
		int hitsPerPage = 10;
		IndexReader reader = DirectoryReader.open(index);
		IndexSearcher searcher = new IndexSearcher(reader);

		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
		while (true) {
			System.out.println("Type in query... ('break' for end)");
			String querystr = in.readLine();
			//break word
			if(querystr.equalsIgnoreCase("break"))break;
			
			Query q = new QueryParser(Version.LUCENE_40, "title", analyzer).parse(querystr+"~");
			// 3. search
			TopScoreDocCollector collector = TopScoreDocCollector.create(hitsPerPage, true);
			searcher.search(q, collector);
			ScoreDoc[] hits = collector.topDocs().scoreDocs;
			print(hits, searcher);
		}
		reader.close();
		System.out.println("program finished...");
	}
}
