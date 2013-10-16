package org.dutir.lucene.query;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.Vector;

import org.apache.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.RMultiFieldQueryParser;
import org.apache.lucene.search.RBooleanClause;
import org.apache.lucene.search.RBooleanQuery;
import org.apache.lucene.search.RConstantScoreRangeQuery;
import org.apache.lucene.search.RQuery;
import org.dutir.lucene.util.ApplicationSetup;
import org.dutir.lucene.util.Files;
import org.dutir.lucene.util.TagSet;

public class MicroblogQueryParser extends LuceneQueryParser {

	/** The logger used for this class */
	Logger logger = Logger.getLogger(this.getClass());
	static final SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
	static final SimpleDateFormat parser = new SimpleDateFormat("EEE MMM dd HH:mm:ss Z yyyy");
	/** retrieve docs before a specified date */
	protected String[] beforedate; 
	final String BEFORE_DATE_FIELD = "querytweettime"; 
	
	protected String[] querynewesdate; 
	final String querynewesttweet_DATE_FIELD = "querynewesttweet"; 
	
//	protected String[] longbeforedate; 
//	final String LONG_BEFORE_DATE_FIELD = "querytweettime"; 
	
	/**
	 * Value of <tt>trecquery.ignore.desc.narr.name.tokens</tt> - should the
	 * token DESCRIPTION and NARRATIVE in the desc and narr fields be ignored?
	 * Defaluts to true?
	 */
	protected static final boolean IGNORE_DESC_NARR_NAME_TOKENS = Boolean
			.parseBoolean(ApplicationSetup.getProperty(
					"trecquery.ignore.desc.narr.name.tokens", "true"));

	/**
	 * Value of <tt>string.use_utf</tt>. If set to true, TRECFullUTFTokenizer
	 * instead of TRECFullTokenizer is used to tokenize the topics file(s).
	 */
	protected final boolean UTF = Boolean.parseBoolean(ApplicationSetup
			.getProperty("string.use_utf", "false"));

	/** The topic files used in this object */
	protected String[] topicFiles;
	
	/** The queries in the topic files. */
	protected String[] queries;

	/** The query identifiers in the topic files. */
	protected String[] query_ids;
	/** The index of the queries. */
	protected int index;

	/**
	 * Constructs an instance of TRECQuery that reads and stores all the queries
	 * from a file with the specified filename.
	 * 
	 * @param queryfilename
	 *            String the name of the file containing all the queries.
	 */
	public MicroblogQueryParser(String queryfilename) {
		Vector<String> vecStringQueries = new Vector<String>();
		Vector<String> vecStringQueryIDs = new Vector<String>();
		Vector<String> vecStringQuerydate = new Vector<String>();
		Vector<String> vecStringnewestDates = new Vector<String>();
		if (this.extractQuery(queryfilename, vecStringQueries,
				vecStringQueryIDs, vecStringQuerydate, vecStringnewestDates))
			this.topicFiles = new String[] { queryfilename };
		this.queries = vecStringQueries.toArray(new String[0]);
		this.query_ids = vecStringQueryIDs.toArray(new String[0]);
		this.beforedate = vecStringQuerydate.toArray(new String[0]);
		this.querynewesdate = vecStringnewestDates.toArray(new String[0]);
		this.index = 0;
	}
	public MicroblogQueryParser() {
		extractQuery();
		index = 0;
	}
	
	/**
	 * Extracts and stores all the queries from the topic files, specified in
	 * the file with default name <tt>trec.topics.list</tt>.
	 */
	protected void extractQuery() {
		try {
			// open the query file
			BufferedReader addressQueryFile = Files
					.openFileReader(ApplicationSetup.TREC_TOPICS_LIST);
			ArrayList<String> parsedTopicFiles = new ArrayList<String>(1);
			String queryFilename;
			Vector<String> vecStringQueries = new Vector<String>();
			Vector<String> vecStringQueryIDs = new Vector<String>();
			Vector<String> vecStringQueryDates = new Vector<String>();
			Vector<String> vecStringnewestDates = new Vector<String>();
			int fileCount = 0;
			while ((queryFilename = addressQueryFile.readLine()) != null) {
				if (queryFilename.startsWith("#") || queryFilename.equals(""))
					continue;
				System.out.println("queryFilename: " + queryFilename);
				if(logger.isInfoEnabled()) logger.info("Extracting queries from " + queryFilename);
				fileCount++;
				boolean rtr = extractQuery(queryFilename, vecStringQueries,
						vecStringQueryIDs, vecStringQueryDates, vecStringnewestDates);
				if (rtr)
					parsedTopicFiles.add(queryFilename);
			}
			if (fileCount == 0) {
				logger.error("No topic files found in "
						+ ApplicationSetup.TREC_TOPICS_LIST
						+ "  - please check");
			}
			if (fileCount > 0 && parsedTopicFiles.size() == 0) {
				logger
						.error("Topic files were specified, but non could be parsed correctly to obtain any topics."
								+ "Check you have the correct topic files specified, and that TrecQueryTags properties are correct.");
			}
			this.queries = (String[]) vecStringQueries.toArray(new String[0]);
			this.query_ids = (String[]) vecStringQueryIDs
					.toArray(new String[0]);
			this.beforedate =  (String[]) vecStringQueryDates
					.toArray(new String[0]);
			this.querynewesdate =  (String[]) vecStringnewestDates
					.toArray(new String[0]);
			this.topicFiles = (String[]) parsedTopicFiles
					.toArray(new String[0]);
			// logger.info("found files ="+ this.topicFiles.length);
			addressQueryFile.close();
		} catch (IOException ioe) {
			logger.error(
					"Input/Output exception while performing the matching.",
					ioe);
		}
	}
	
	/**
	 * Extracts and stores all the queries from a query file.
	 * 
	 * @param queryfilename
	 *            String the name of a file containing topics.
	 * @param vecStringQueries
	 *            Vector a vector containing the queries as strings.
	 * @param vecStringIds
	 *            Vector a vector containing the query identifiers as strings.
	 * @return boolean true if some queries were successfully extracted.
	 */
	public boolean extractQuery(String queryfilename,
			Vector<String> vecStringQueries, Vector<String> vecStringIds, Vector<String> vecStringDates, Vector<String> vecStringnewestDates) {
		boolean gotSome = false;
		try {
			BufferedReader br;
			if (!Files.exists(queryfilename) || !Files.canRead(queryfilename)) {
				logger.error("The topics file " + queryfilename
						+ " does not exist, or it cannot be read.");
				return false;
			} else {
				br = Files.openFileReader(queryfilename, desiredEncoding);
				TRECFullTokenizer queryTokenizer = new TRECFullUTFTokenizer(
						new TagSet(TagSet.TREC_QUERY_TAGS), new TagSet(
								TagSet.EMPTY_TAGS), br);
				queryTokenizer.setIgnoreMissingClosingTags(true);
				while (!queryTokenizer.isEndOfFile()) {
					String docnoToken = null;
					String beforedate = null;
					String querynewestdata = null;
					StringBuilder query = new StringBuilder();
					boolean seenDescriptionToken = !IGNORE_DESC_NARR_NAME_TOKENS;
					boolean seenNarrativeToken = !IGNORE_DESC_NARR_NAME_TOKENS;
					while (!queryTokenizer.isEndOfDocument()) {
						String token = queryTokenizer.nextToken();
						
						if (token == null || token.length() == 0
								|| queryTokenizer.inTagToSkip())
							continue;
						token = token.trim();
						
						if (queryTokenizer.inDocnoTag()) {
							StringTokenizer docnoTokens = new StringTokenizer(
									token.trim(), " ");
							while (docnoTokens.hasMoreTokens())
								docnoToken = docnoTokens.nextToken().trim();
						} else if (queryTokenizer.inTagToProcess()) {
							
							if(queryTokenizer.currentTag().toLowerCase().equals(BEFORE_DATE_FIELD)){
								beforedate = " " + token; 
								continue;
							}
							
							if(queryTokenizer.currentTag().toLowerCase().equals(querynewesttweet_DATE_FIELD)){
								querynewestdata = " " + token; 
								continue;
							}
							
							if (!seenDescriptionToken
									&& queryTokenizer.currentTag()
											.toUpperCase().equals("DESC")
									&& token.toUpperCase()
											.equals("DESCRIPTION"))
								continue;
							if (!seenNarrativeToken
									&& queryTokenizer.currentTag()
											.toUpperCase().equals("NARR")
									&& token.toUpperCase().equals("NARRATIVE"))
								continue;
							query.append(token);
							query.append(' ');

						}
					}
					if (query.length() == 0)
						continue;
					vecStringQueries.add(query.toString());
					vecStringIds.add(docnoToken);
					
					
//						System.out.println(formatter.format(parser.parse(beforedate.trim())));
//						vecStringDates.add(formatter.format(parser.parse(beforedate.trim())));
					vecStringDates.add(beforedate.trim());
					vecStringnewestDates.add(querynewestdata.trim());
					queryTokenizer.nextDocument();
					gotSome = true;
				}
				// after processing each query file, close the BufferedReader
				br.close();
				if(logger.isInfoEnabled()) logger.info("Extracting Queries successfully");
			}
		} catch (IOException ioe) {
			logger.error(
					"Input/Output exception while extracting queries from the topic file named "
							+ queryfilename, ioe);
		}
		return gotSome;
	}

	public RBooleanQuery getNextLuceneQuery(String fields[], Analyzer analyzer) {
		try {
			if (!hasMoreQueries()) {
				return null;
			}
			String squery = this.nextQuery();
			String queryId = this.getQueryId();
			String date = this.getQueryDate();
			String newest_date = this.getNewestQueryDate();
			RBooleanQuery query;
			// System.out.println(queryId + ": " + squery);
			query = RMultiFieldQueryParser.parse(squery, fields, analyzer);
//			query.add(new RBooleanClause(lessThanRangeQuery(date), org.apache.lucene.search.RBooleanClause.Occur.MUST));
			query.setID(queryId);
			System.setProperty("BEFOREDATE", date);
			System.setProperty("NEWEST_BEFOREDATE", newest_date);
//			System.setProperty("LONG_BEFOREDATE", date);
			return query;
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return null;
	}
	
//	static Term minLowerTerm = new Term("date", "00000000"); 
	protected RQuery lessThanRangeQuery(String date) {
//		Term minLowerTerm = new Term("time", "00000000000000"); 
//		Term upperTerm = new Term("time", date);
		RConstantScoreRangeQuery rquery = new RConstantScoreRangeQuery("time", "00000000000000", date, true, true);
		rquery.setBoost(1);
//		RRangeQuery rquery = new RRangeQuery("time", "00000000000000", date, true, true);
//		RRangeQuery rquery = new RRangeQuery(minLowerTerm, upperTerm, true);
		return rquery;
	}
	/**
	 * Constructs an instance of TRECQuery that reads and stores all the queries
	 * from a the specified query file.
	 * 
	 * @param queryfile
	 *            File the file containing the queries.
	 */
	public MicroblogQueryParser(File queryfile) {
		this(queryfile.getName());
	}
	/**
	 * Returns the index of the last obtained query.
	 * 
	 * @return int the index of the last obtained query.
	 */
	public int getIndexOfCurrentQuery() {
		return index - 1;
	}

	/**
	 * Returns the number of the queries read from the processed topic files.
	 * 
	 * @return int the number of topics contained in the processed topic files.
	 */
	public int getNumberOfQueries() {
		return queries.length;
	}

	/**
	 * Returns the filenames of the topic files from which the queries were
	 * extracted
	 */
	public String[] getTopicFilenames() {
		return this.topicFiles;
	}

	/**
	 * Return the query for the given query number.
	 * 
	 * @return String the string representing the query.
	 * @param queryNo
	 *            String The number of a query.
	 */
	public String getQuery(String queryNo) {
		for (int i = 0; i < query_ids.length; i++)
			if (query_ids[i].equals(queryNo))
				return queries[i];
		return null;
	}

	/**
	 * Test if there are more queries to process.
	 * 
	 * @return boolean true if there are more queries to process, otherwise
	 *         returns false.
	 */
	public boolean hasMoreQueries() {
		if (index == queries.length)
			return false;
		return true;
	}

	/**
	 * Returns a query.
	 * 
	 * @return String the next query.
	 */
	public String nextQuery() {
		if (index == queries.length)
			return null;
		return queries[index++];
	}

	/**
	 * this should be used after calling nextQuery
	 * @return
	 */
	public String getQueryDate() {
		return beforedate[index == 0 ? 0 : index - 1];
	}
	
	/**
	 * this should be used after calling nextQuery
	 * @return
	 */
	public String getNewestQueryDate() {
		
		return querynewesdate[index == 0 ? 0 : index - 1];
	}
	/**
	 * Returns the query identifier of the last query fetched, or the first one,
	 * if none has been fetched yet.
	 * 
	 * @return String the query number of a query.
	 */
	public String getQueryId() {
		return query_ids[index == 0 ? 0 : index - 1];
	}

	/**
	 * Returns the query ids
	 * 
	 * @return String array containing the query ids.
	 * @since 2.2
	 */
	public String[] getQueryIds() {
		return query_ids;
	}

	/**
	 * Returns the queries in an array of strings
	 * 
	 * @return String[] an array containing the strings that represent the
	 *         queries.
	 */
	public String[] toArray() {
		return (String[]) queries.clone();
	}

	/**
	 * Resets the query index.
	 */
	public void reset() {
		this.index = 0;
	}

	
	static String[] tokenize(String query, Analyzer analyzer) {
		String field = "";
		ArrayList<String> list = new ArrayList<String>();
		RBooleanQuery bQuery = new RBooleanQuery();
		StringReader reader = new StringReader(query);
			TokenStream ts = analyzer.tokenStream(field, reader);
			org.apache.lucene.analysis.Token token = null;
			try {
				while((token = ts.next()) != null){
					list.add(token.term());
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		return list.toArray(new String[0]);
	}
	public static void main(String args[]){
		MicroblogQueryParser trecQ = new MicroblogQueryParser("./TopicQrel/microblog.topics.MB1-50.txt");
		trecQ.extractQuery();
	}
	
	
}
