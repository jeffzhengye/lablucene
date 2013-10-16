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
