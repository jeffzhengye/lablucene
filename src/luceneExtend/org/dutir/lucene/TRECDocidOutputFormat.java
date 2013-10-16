package org.dutir.lucene;

import java.io.PrintWriter;

import org.apache.log4j.Logger;
import org.apache.lucene.OutputFormat;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.search.TopDocCollector;
import org.apache.lucene.search.TopDocs;
import org.dutir.lucene.util.ApplicationSetup;

public 	class TRECDocidOutputFormat implements OutputFormat {
	Searcher searcher;
	protected static final Logger logger = Logger.getLogger(TRECDocidOutputFormat.class);
	public TRECDocidOutputFormat(Searcher searcher) {
		this.searcher = searcher;
	}

	/**
	 * Prints the results for the given search request, using the specified
	 * destination.
	 * 
	 * @param pw
	 *            PrintWriter the destination where to save the results.
	 * @param q
	 *            SearchRequest the object encapsulating the query and the
	 *            results.
	 */
	public void printResults(String queryID, final PrintWriter pw,
			final TopDocCollector collector) {
		
		TopDocs topDocs = collector.topDocs();
		int len = topDocs.totalHits;

		int maximum = Math.min(topDocs.scoreDocs.length, end);
		// if (minimum > set.getResultSize())
		// minimum = set.getResultSize();
		final String iteration = ITERATION + "0";
		final String queryIdExpanded = queryID + " " + iteration + " ";
		final String methodExpanded = " " + runName
				+ ApplicationSetup.EOL;
		StringBuilder sbuffer = new StringBuilder();
		// the results are ordered in descending order
		// with respect to the score.
		
		int limit = 1000;
		int counter = 0;
		
		for (int i = start; i < maximum && counter < limit; i++) {
			int docid = topDocs.scoreDocs[i].doc;
			String filename = "" + docid;
			float score = topDocs.scoreDocs[i].score;

//			if (filename != null && !filename.equals(filename.trim())) {
//				if (logger.isDebugEnabled())
//					logger.debug("orginal doc name not trimmed: |"
//							+ filename + "|");
//			} else if (filename == null) {
//				logger.error("inner docid does not exist: " + docid
//						+ ", score:" + score);
//				if (docid > 0) {
//					try {
//						logger.error("previous docno: "
//								+ this.searcher.doc(docid - 1).toString());
//					} catch (Exception e) {
//						e.printStackTrace();
//					}
//				}
//				continue;
//			}
			
			sbuffer.append(queryIdExpanded);
			sbuffer.append(filename);
			sbuffer.append(" ");
			sbuffer.append(counter);
			sbuffer.append(" ");
			sbuffer.append(score);
			sbuffer.append(methodExpanded);
			counter++;

		}
		pw.write(sbuffer.toString());
	}
}
