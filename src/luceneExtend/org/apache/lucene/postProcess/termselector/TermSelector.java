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
 * Contributor(s):
 *   Gianni Amati <gba{a.}fub.it> (original author)
 *   Vassilis Plachouras <vassilis{a.}dcs.gla.ac.uk>
 *   Ben He <ben{a.}dcs.gla.ac.uk>
 *   Craig Macdonald <craigm{a.}dcs.gla.ac.uk>
 */

/*
* This file is probably based on a class with the same name from Terrier, 
* so we keep the copyright head here. If you have any question, please notify me first.
* Thanks. 
*/
package org.apache.lucene.postProcess.termselector;

import gnu.trove.THashMap;
import gnu.trove.THashSet;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

import org.apache.log4j.Logger;
import org.apache.lucene.analysis.WordlistLoader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermFreqVector;
import org.apache.lucene.index.TermPositionVector;
import org.apache.lucene.postProcess.QueryExpansionModel;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.search.TopDocCollector;
import org.dutir.lucene.util.ApplicationSetup;
import org.dutir.lucene.util.ExpansionTerms;
import org.dutir.lucene.util.TermsCache;
import org.dutir.lucene.util.ExpansionTerms.ExpansionTerm;
import org.dutir.lucene.util.TermsCache.Item;

public abstract class TermSelector {
	/** The logger used */
	private static Logger logger = Logger.getLogger(TermSelector.class);

	static boolean LanguageModel = Boolean.parseBoolean(ApplicationSetup
			.getProperty("Lucene.Search.LanguageModel", "false"));
	protected int EXPANSION_MIN_DOCUMENTS;

	protected static HashSet stopSet = null;
	static {
		try {
			stopSet = WordlistLoader.getWordSet(new File(ApplicationSetup
					.getProperty("StopFilter.stopPath", "./conf/empty.txt")));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	protected HashMap<String, ExpansionTerm> termMap; // mapping from termid to
	// expansion terms

	protected HashSet<String> originalQueryTermidSet;
	protected String field = ApplicationSetup.getProperty(
			"Lucene.QueryExpansion.FieldName", "content");
	protected Searcher searcher = null;

	protected float feedbackSetLength;

	protected THashMap<String, String> metaMap = new THashMap<String, String>();

	protected TopDocCollector topDoc;

	protected float ATF = 1f; //average term frequency in the feedback set (if it's with Rocchio framework, it's actually ATF for a single document)
	protected int originalQueryLength = 0; 

	public TermSelector() {
		this.EXPANSION_MIN_DOCUMENTS = Integer.parseInt(ApplicationSetup
				.getProperty("expansion.mindocuments", "2"));
	}

	public void setResultSet(TopDocCollector topDoc) {
		this.topDoc = topDoc;
	}

	public void setField(String field) {
		this.field = field;
	}

	public ExpansionTerm[] getMostWeightedTerms(int numberOfExpandedTerms) {
		if (termMap == null) {
			ExpansionTerm[] expTerms = {};
			return expTerms;
		}
		int n = Math.min(numberOfExpandedTerms, termMap.size());
		boolean conservativeQE = (numberOfExpandedTerms == 0 && this.originalQueryTermidSet != null);
		THashSet<ExpansionTerm> tSet = new THashSet<ExpansionTerm>();
		Object[] obj = termMap.values().toArray();
		int len = obj.length;
		ExpansionTerm[] terms = new ExpansionTerm[len];
		for (int i = 0; i < len; i++)
			terms[i] = (ExpansionTerm) obj[i];
		Arrays.sort(terms);
		float total = 0;
		if (!conservativeQE) {
			for (int i = 0; i < n; i++) {
				if (terms[i].getWeightExpansion() > 0d) // filter 0 weight term
				{
					tSet.add(terms[i]);
					total += terms[i].getWeightExpansion();
				} else {
					if (logger.isDebugEnabled())
						logger.debug("term weight < 0 :" + terms[i]);
				}
				
			}
		} else {
			for (int i = 0; i < n; i++) {
				if (this.originalQueryTermidSet.contains(terms[i].getTerm())) {
					tSet.add(terms[i]);
					total += terms[i].getWeightExpansion();
					if (tSet.size() == this.originalQueryTermidSet.size()) {
						break;
					}
				}
			}
		}

		ExpansionTerm[] retETerms = (ExpansionTerm[]) tSet
				.toArray(new ExpansionTerm[tSet.size()]);
//		logger.info("returned len: " + retETerms.length + ", " + tSet.size() + ", " + n + ", " + total);
		// *******normalize***************************
		// for(int i=0; i < retETerms.length ; i++){
		// retETerms[i].setWeightExpansion(retETerms[i].getWeightExpansion() /
		// total);
		// }
		// *********************************************
		return retETerms;
	}

	public HashMap<String, ExpansionTerm> getMostWeightedTermsInHashMap(
			int numberOfExpandedTerms) {
		HashMap<String, ExpansionTerm> tMap = new HashMap<String, ExpansionTerm>();
		if (termMap == null)
			return tMap;
		int n = Math.min(numberOfExpandedTerms, termMap.size());
		boolean conservativeQE = (numberOfExpandedTerms == 0 && this.originalQueryTermidSet != null);
		Object[] obj = termMap.values().toArray();
		int len = obj.length;
		ExpansionTerm[] terms = new ExpansionTerm[obj.length];
		for (int i = 0; i < len; i++)
			terms[i] = (ExpansionTerm) obj[i];
		Arrays.sort(terms);
		if (!conservativeQE) {
			for (int i = 0; i < n; i++)
				if (terms[i].getWeightExpansion() > 0d)
					tMap.put(terms[i].getTerm(), terms[i]);
		} else {
			for (int i = 0; i < n; i++)
				if (this.originalQueryTermidSet.contains(terms[i].getTerm())) {
					tMap.put(terms[i].getTerm(), terms[i]);
					if (tMap.size() == this.originalQueryTermidSet.size())
						break;
				}
		}
		return tMap;
	}

	public int getNumberOfUniqueTerms() {
		int nTerms = 0;
		if (termMap != null)
			nTerms = termMap.size();
		return nTerms;
	}

	public void setMetaInfo(String property, String value) {
		metaMap.put(property, value);
	}

	protected void getTerms(int[] docids) {
		termMap = new HashMap<String, ExpansionTerm>();
		this.feedbackSetLength = 0;

		for (int i = 0; i < docids.length; i++) {
			int docid = docids[i];
			TermFreqVector tfv = null;
			try {
				tfv = this.searcher.getIndexReader().getTermFreqVector(docid,
						field);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if (tfv == null)
				logger.warn("document " + docid + " not found, field=" + field);
			else {
				String strterms[] = tfv.getTerms();
				int freqs[] = tfv.getTermFrequencies();
				for (int j = 0; j < strterms.length; j++) {
					if (!stopSet.contains(strterms[j])) {
						this.insertTerm(strterms[j], freqs[j]);
						feedbackSetLength += freqs[j];
					}
				}
				this.ATF  = feedbackSetLength / freqs.length;
			}
		}
	}

	protected void getTerms(String[][] terms, int[][] freqss,
			TermPositionVector[] tfvs) {
		termMap = new HashMap<String, ExpansionTerm>();
		this.feedbackSetLength = 0;
		for (int i = 0; i < terms.length; i++) {
			String strterms[] = terms[i];
			int freqs[] = freqss[i];
			TermPositionVector tfv = tfvs[i];
			for (int j = 0; j < strterms.length; j++) {
				if (!stopSet.contains(strterms[j])) {
					this.insertTerm(strterms[j], freqs[j]);
					feedbackSetLength += freqs[j];
				}
			}
		}
	}

	static TermsCache tcache = TermsCache.getInstance();

	protected Item getItem(String term) {
		Term lterm = new Term(field, term);
		return tcache.getItem(lterm, searcher);
	}

	/**
	 * Add a term in the X top-retrieved documents as a candidate of the
	 * expanded terms.
	 * 
	 * @param termID
	 *            int the integer identifier of a term
	 * @param withinDocumentFrequency
	 *            double the within document frequency of a term
	 */
	protected void insertTerm(String term, float withinDocumentFrequency) {
		final ExpansionTerm et = termMap.get(term);
		if (et == null)
			termMap.put(term, new ExpansionTerm(term, withinDocumentFrequency));
		else
			et.insertRecord(withinDocumentFrequency);
	}

	public void setSearcher(Searcher searcher) {
		this.searcher = searcher;
	}

	public static TermSelector getDefaultTermSelector(Searcher searcher) {
		String prefix = "org.apache.lucene.postProcess.termselector.";
		String name = ApplicationSetup
				.getProperty("term.selector.name",
						"org.apache.lucene.postProcess.termselector.RocchioTermSelector");
		if (name.indexOf('.') < 0)
			name = prefix.concat(name);
		TermSelector selector = null;
		try {
			selector = (TermSelector) Class.forName(name).newInstance();
			selector.setSearcher(searcher);
			// selector.setFeedSetLength(feedbackSetLength);
		} catch (Exception e) {
			logger.warn("Error while initializing TermSelector " + name);
			e.printStackTrace();
		}
		return selector;
	}

	protected void setFeedSetLength(float feedbackSetLength) {
		this.feedbackSetLength = feedbackSetLength;
	}
	
	protected void setOriginalQueryLength(int len) {
		this.originalQueryLength = len;
	}

	public static TermSelector getTermSelector(String name, Searcher searcher) {
		String prefix = "org.apache.lucene.postProcess.termselector.";
		if (name.indexOf('.') < 0)
			name = prefix.concat(name);
		TermSelector selector = null;
		try {
			selector = (TermSelector) Class.forName(name).newInstance();
			selector.setSearcher(searcher);
			// selector.setFeedSetLength(feedbackSetLength);
		} catch (Exception e) {
			logger.warn("Error while initializing TermSelector " + name);
			e.printStackTrace();
		}
		return selector;
	}

	// public abstract void assignTermWeights(int feedbackSetSize,
	// QueryExpansionModel QEModel);

	public abstract void assignTermWeights(int[] docids, float scores[],
			QueryExpansionModel QEModel);

	public abstract void assignTermWeights(String terms[][], int freqs[][],
			TermPositionVector tfvs[], QueryExpansionModel QEModel);

	public void setOriginalQueryTerms(HashSet<String> termStrings) {
		this.originalQueryTermidSet = new HashSet<String>();
		for (String term : termStrings) {
			this.originalQueryTermidSet.add(term);
		}
	}

	abstract public String getInfo();

}
