package org.apache.lucene;

import java.io.PrintWriter;

import org.apache.lucene.search.TopDocCollector;
import org.dutir.lucene.util.ApplicationSetup;

/** interface for adjusting the output of TRECQuerying */
public  interface OutputFormat {
	public void printResults(String queryID, final PrintWriter pw,
			final TopDocCollector collector);
	
	static String runName = ApplicationSetup.getProperty(
			"TRECQuerying.runname", "LabLucene");
	static int start = Integer.parseInt(ApplicationSetup.getProperty(
			"TRECQuerying.start", "0"));
	static int first_end = Integer.parseInt(ApplicationSetup.getProperty(
			"TRECQuerying.end", "1000"));
	static int end = Integer.parseInt(ApplicationSetup.getProperty(
			"TRECQuerying.endFeedback", "" + first_end));
	
	/** A TREC specific output field. */
	static String ITERATION = ApplicationSetup.getProperty(
			"trec.iteration", "Q");
	static String docidField = ApplicationSetup.getProperty("TrecDocTags.idtag",
			"DOCNO");
}
