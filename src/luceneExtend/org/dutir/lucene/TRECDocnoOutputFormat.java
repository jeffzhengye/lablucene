/*
 * Terrier - Terabyte Retriever
 * Webpage: http://terrier.org
 * Contact: terrier{a.}dcs.gla.ac.uk
 * University of Glasgow - School of Computing Science
 * http://www.ac.gla.uk
 *
 * The contents of this file are subject to the Mozilla Public License
 * Version 1.1 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
 * the LiCense for the specific language governing rights and limitations
 * under the License.
 *
 * The Original Code is TRECQuerying.java.
 *
 * The Original Code is Copyright (C) 2004-2011 the University of Glasgow.
 * All Rights Reserved.
 *
*/

/*
* This file is probably based on a class with the same name from Terrier, 
* so we keep the copyright head here. If you have any question, please notify me first.
* Thanks. 
*/
package org.dutir.lucene;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;

import org.apache.log4j.Logger;
import org.apache.lucene.OutputFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.search.TopDocCollector;
import org.apache.lucene.search.TopDocs;
import org.dutir.lucene.util.ApplicationSetup;

public class TRECDocnoOutputFormat implements OutputFormat {
	protected static final Logger logger = Logger.getLogger(TRECDocnoOutputFormat.class);
	Searcher searcher;

	public TRECDocnoOutputFormat(Searcher searcher) {
		this.searcher = searcher;
	}

//	String docidField = null;
//
//	String getIdFieldName() {
//		if (docidField == null) {
//			docidField = ApplicationSetup.getProperty("TrecDocTags.idtag",
//					"DOCNO");
//		}
//		return docidField;
//	}

	/**
	 * Prints the results for the given search request, using the specified
	 * destination.
	 * 
	 * @param pw
	 *            PrintWriter the destination where to save the results.
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
		final String methodExpanded = " " + "LabLucene"
				+ ApplicationSetup.EOL;
		StringBuilder sbuffer = new StringBuilder();
		// the results are ordered in descending order
		// with respect to the score.
		int limit = 1000;
		int counter = 0;
		HashSet<String> docnoset = new HashSet<String>();
		
		for (int i = start; i < maximum && counter < limit; i++) {
			int docid = topDocs.scoreDocs[i].doc;

			Document doc = null;
			String filename = null;
			try {
				doc = searcher.doc(docid);
				filename = doc.get(docidField);
			} catch (Exception e) {
				e.printStackTrace();
			}
			float score = topDocs.scoreDocs[i].score;

			if (filename != null && !filename.equals(filename.trim())) {
				if (logger.isDebugEnabled())
					logger.debug("orginal doc name not trimmed: |"
							+ filename + "|");
			} else if (filename == null || filename.equalsIgnoreCase("NULL")) {
				logger.error("docno does not exist: " + doc.toString());
				logger.error("inner docid: " + docid + ", score:" + score);
				if (docid > 0) {
					try {
						logger.error("previous docno: "
								+ this.searcher.doc(docid - 1).toString());
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				continue;
			}else{
				if(docnoset.contains(filename)){
					logger.debug("existing docno removed: " + filename);
					continue;
				}else{
					docnoset.add(filename);
				}
			}
			
			
			
			sbuffer.append(queryIdExpanded);
			sbuffer.append(filename.trim());
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
