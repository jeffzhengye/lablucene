package org.dutir.lucene;

import gnu.trove.THashSet;

import java.io.PrintWriter;

import org.apache.log4j.Logger;
import org.apache.lucene.OutputFormat;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.search.TopDocCollector;
import org.apache.lucene.search.TopDocs;
import org.dutir.lucene.evaluation.TRECQrelsInMemory;
import org.dutir.lucene.util.ApplicationSetup;

/**
 * this is only for RF 08 right now.
 * 
 * @author zheng
 * 
 */
public class TRECDocidFilterOutputFormat implements OutputFormat {
	Searcher searcher;
	protected static final Logger logger = Logger
			.getLogger(TRECDocidOutputFormat.class);
	String qrelsFilename = ApplicationSetup.getProperty("RF08Filter.file", "");
	TRECQrelsInMemory b2eqrels = new TRECQrelsInMemory(qrelsFilename);

	public TRECDocidFilterOutputFormat(Searcher searcher) {
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

		String queryid = "";
		THashSet<String> relSet = null;
		THashSet<String> nonrelSet = null;
		// if (minimum > set.getResultSize())
		// minimum = set.getResultSize();
		final String iteration = ITERATION + "0";
		final String queryIdExpanded = queryID + " " + iteration + " ";
		final String methodExpanded = " " + "LabLucene" + ApplicationSetup.EOL;
		StringBuilder sbuffer = new StringBuilder(1024 * 10);
		// the results are ordered in descending order
		// with respect to the score.
		int i = start;
		int p = 0;
		int skiped = 0;
		for (; i < maximum && p < 2500; i++) {
			int docid = topDocs.scoreDocs[i].doc;
			String filename = "" + docid;
			float score = topDocs.scoreDocs[i].score;

			if (!queryid.equals(queryID)) {
				queryid = queryID;
				// rank = 0;
				relSet = b2eqrels.getRelevantDocuments(queryid);
				nonrelSet = b2eqrels.getNonRelevantDocuments(queryid);
			}
			if (relSet == null) {
				skiped++;
				continue;
			} else if (relSet.contains(filename)) {
				skiped++;
				continue;
			}
			if (nonrelSet != null && nonrelSet.contains(filename)) {
				skiped++;
				continue;
			}

			if (filename != null && !filename.equals(filename.trim())) {
				if (logger.isDebugEnabled())
					logger.debug("orginal doc name not trimmed: |" + filename
							+ "|");
			} else if (filename == null) {
				logger.error("inner docid does not exist: " + docid
						+ ", score:" + score);
				if (docid > 0) {
					try {
						logger.error("previous docno: "
								+ this.searcher.doc(docid - 1).toString());
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				continue;
			}
			sbuffer.append(queryIdExpanded);
			sbuffer.append(filename);
			sbuffer.append(" ");
			sbuffer.append(p);
			sbuffer.append(" ");
			sbuffer.append(score);
			sbuffer.append(methodExpanded);
			p++;
		}
		if (logger.isDebugEnabled())
			logger.debug("skiped: " + skiped);
		pw.write(sbuffer.toString());
	}

	public static void main(String args[]) {
		String test = "BM25b=0.4k_1=1.2k_3=8.0_QEAdap_W3RocProx1w=500F=1.0_30_50_KLb0.8_5.gz";
		String prefix = "BM25b=0.4k_1=1.2k_3=8.0";
		if (test.matches(prefix + "_[0-9]{1,5}\\.gz")) {
			System.out.println("true");
		}
	}
}