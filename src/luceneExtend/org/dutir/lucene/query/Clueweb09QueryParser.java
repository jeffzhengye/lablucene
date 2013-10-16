/**
 * 
 */
package org.dutir.lucene.query;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Vector;

import org.apache.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.RMultiFieldQueryParser;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.RBooleanQuery;
import org.dutir.lucene.util.ApplicationSetup;
import org.dutir.lucene.util.Files;

/**
 * @author yezheng
 *
 */
public class Clueweb09QueryParser extends LuceneQueryParser {

	Logger logger = Logger.getLogger(this.getClass());
	/** The topic files used in this object */
	protected String[] topicFiles;

	/** The queries in the topic files. */
	protected String[] queries;

	/** The query identifiers in the topic files. */
	protected String[] query_ids;
	/** The index of the queries. */
	protected int index;
	
	public Clueweb09QueryParser(){
		this.extractQuery();
		this.index = 0;
	}
	
	public Clueweb09QueryParser(String queryfilename){
		Vector<String> vecStringQueries = new Vector<String>();
		Vector<String> vecStringQueryIDs = new Vector<String>();
		if (this.extractQuery(queryfilename, vecStringQueries,
				vecStringQueryIDs))
			this.topicFiles = new String[] { queryfilename };
		this.queries = vecStringQueries.toArray(new String[0]);
		this.query_ids = vecStringQueryIDs.toArray(new String[0]);
		this.index = 0;
	}
	public Clueweb09QueryParser(File file){
		this(file.getName());
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
			int fileCount = 0;
			while ((queryFilename = addressQueryFile.readLine()) != null) {
				if (queryFilename.startsWith("#") || queryFilename.equals(""))
					continue;
				if(logger.isInfoEnabled()) logger.info("Extracting queries from " + queryFilename);
				fileCount++;
				boolean rtr = extractQuery(queryFilename, vecStringQueries,
						vecStringQueryIDs);
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
			Vector<String> vecStringQueries, Vector<String> vecStringIds) {
		boolean gotSome = false;
		try {
			BufferedReader br;
			if (!Files.exists(queryfilename) || !Files.canRead(queryfilename)) {
				logger.error("The topics file " + queryfilename
						+ " does not exist, or it cannot be read.");
				return false;
			} else {
				br = Files.openFileReader(queryfilename, desiredEncoding);
				String line = null;
				while((line = br.readLine()) != null){
					String parts[] = line.split(":");
					
					
					vecStringQueries.add(parts[1]);
//					assert parts[0].length() > 5;
					if( parts[0].length() > 4 && parts[0].charAt(4) == '-'){
						vecStringIds.add(parts[0].substring(5));
					}else{
						vecStringIds.add(parts[0]);
					}
					
				}
				// after processing each query file, close the BufferedReader
				br.close();
				gotSome = true;
			}
		} catch (IOException ioe) {
			logger.error(
					"Input/Output exception while extracting queries from the topic file named "
							+ queryfilename, ioe);
		}
		return gotSome;
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

//	public RBooleanQuery getNextLuceneQuery(String fields[], Analyzer analyzer) {
//		try {
//			String squery = this.nextQuery();
//			String queryId = this.getQueryId();
//			RBooleanQuery query;
//			query = RMultiFieldQueryParser.parse(squery, fields,
//					analyzer);
//			query.setID(queryId);
//			return query;
//		} catch (ParseException e) {
//			e.printStackTrace();
//		}
//		return null;
//	}
}
