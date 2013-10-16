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
package org.apache.lucene.postProcess.termselector;

import gnu.trove.TObjectFloatHashMap;

import java.util.Arrays;
import java.util.HashMap;

import org.apache.log4j.Logger;
import org.apache.lucene.index.TermPositionVector;
import org.apache.lucene.postProcess.QueryExpansionModel;
import org.dutir.lucene.util.ApplicationSetup;
import org.dutir.lucene.util.ExpansionTerms.ExpansionTerm;
import org.dutir.util.Normalizer;

public class RocchioTermSelector extends TermSelector {

	/** The logger used */
	private static Logger logger = Logger.getLogger(RocchioTermSelector.class);
	private static boolean weighted = Boolean.parseBoolean(ApplicationSetup
			.getProperty("rocchio.weighted", "false"));
	protected int EXPANSION_MIN_DOCUMENTS;
	String termSelector = ApplicationSetup.getProperty("rocchio.termSelector",
			"DFRTermSelector");
	String addInfo = "";
	private static int type = Integer.parseInt((ApplicationSetup.getProperty(
			"rocchio.weighteType", "0")));
	float exp = Float.parseFloat(ApplicationSetup.getProperty(
			"rocchio.exp", "1.5f"));
	
	
	public RocchioTermSelector() {
		super();
	}

	@Override
	public void assignTermWeights(int[] docids, float scores[],
			QueryExpansionModel QEModel) {
		int effDocuments = docids.length;
		TermSelector selector = TermSelector.getTermSelector(termSelector,
				this.searcher);
		selector.setField(field);
		selector.setMetaInfo("normalize.weights", "false");
		selector.setOriginalQueryTerms(originalQueryTermidSet);
		selector.setResultSet(topDoc);
		addInfo += selector.getInfo();
		ExpansionTerm[][] expTerms = new ExpansionTerm[effDocuments][];
		norm(scores);

		// for each of the top-ranked documents
		for (int i = 0; i < effDocuments; i++) {
			// obtain the weighted terms
			int[] oneDocid = { docids[i] };
			float[] oneScore = { scores == null ? 1 : scores[i] };
			selector.assignTermWeights(oneDocid, oneScore, QEModel);
			expTerms[i] = selector.getMostWeightedTerms(selector
					.getNumberOfUniqueTerms());
			if (weighted) {
				multiWeight(oneScore[0], expTerms[i]);
			}
		}
		// merge expansion terms: compute mean term weight for each term, sort
		// again
		TObjectFloatHashMap<String> termidWeightMap = new TObjectFloatHashMap<String>();
		TObjectFloatHashMap<String> termidDFMap = new TObjectFloatHashMap<String>();
		for (int i = 0; i < effDocuments; i++) {
			for (int j = 0; j < expTerms[i].length; j++) {
				termidWeightMap.adjustOrPutValue(expTerms[i][j].getTerm(),
						expTerms[i][j].getWeightExpansion(),
						expTerms[i][j].getWeightExpansion());
				termidDFMap.adjustOrPutValue(expTerms[i][j].getTerm(), 1, 1);
			}
		}

		ExpansionTerm[] candidateTerms = new ExpansionTerm[termidWeightMap
				.size()];
		float total = 0;
		int counter = 0;
		for (String termid : termidWeightMap.keys(new String[0])) {
			candidateTerms[counter] = new ExpansionTerm(termid, 0);
			if (effDocuments > this.EXPANSION_MIN_DOCUMENTS
					&& termidDFMap.get(termid) < this.EXPANSION_MIN_DOCUMENTS
					&& !originalQueryTermidSet.contains(termid))
				candidateTerms[counter].setWeightExpansion(0);
			else {
				float weight = termidWeightMap.get(termid) / effDocuments;
				candidateTerms[counter].setWeightExpansion(weight);
				total += weight;
			}
			counter++;
		}
		Arrays.sort(candidateTerms);
		termMap = new HashMap<String, ExpansionTerm>();

		float normaliser = candidateTerms[0].getWeightExpansion();
		if (LanguageModel) {
			if (QEModel.PARAMETER_FREE) {
				for (ExpansionTerm term : candidateTerms) {
					term.setWeightExpansion(term.getWeightExpansion() / total);
					termMap.put(term.getTerm(), term);
				}
			} else {
				for (ExpansionTerm term : candidateTerms) {
					term.setWeightExpansion(term.getWeightExpansion()
							/ normaliser);
					termMap.put(term.getTerm(), term);
				}
			}
		} else {// normalize the expansion weights by the maximum weight among
				// the expansion terms
			for (ExpansionTerm term : candidateTerms) {
				term.setWeightExpansion(term.getWeightExpansion() / normaliser);
				termMap.put(term.getTerm(), term);
			}
		}
	}
    
	private void norm(float[] scores) {
		if (type == 0) {
			return;
		} else if (type == 1) {
			Normalizer.norm_MaxMin_0_1(scores);
		} else if (type == 2) {
			Normalizer.norm2(scores);
		} else if (type == 3) {
			
			for (int i = 0; i < scores.length; i++) {
				if(scores[i] < 0){
					scores[i] = 0;
				}
			}
			
			Normalizer.norm2(scores);
			Normalizer.norm_MaxMin_0_1(scores);
			for (int i = 0; i < scores.length; i++) {
				scores[i] = (float) Math.pow(scores[i], exp);
			}
//			Normalizer.norm_MaxMin_0_1(scores);
		}
	}

	private String getType() {
		if (type == 0) {
			return "";
		}
		return "" + type;
	}

	private void multiWeight(float w, ExpansionTerm[] expansionTerms) {
		for (int i = 0; i < expansionTerms.length; i++) {
			expansionTerms[i].setWeightExpansion(w
					* expansionTerms[i].getWeightExpansion());
		}
	}

	@Override
	public String getInfo() {
		return (weighted ? "W" : "") + getType() + "Roc" + addInfo + "Pow=" + exp;
	}

	@Override
	public void assignTermWeights(String[][] terms, int[][] freqss,
			TermPositionVector[] tfvs, QueryExpansionModel QEModel) {
		int effDocuments = terms.length;

		TermSelector selector = TermSelector.getTermSelector("DFRTermSelector",
				this.searcher);
		selector.setField(field);
		selector.setMetaInfo("normalize.weights", "false");
		ExpansionTerm[][] expTerms = new ExpansionTerm[effDocuments][];

		// for each of the top-ranked documents
		String strterms[][] = new String[1][];
		int freqs[][] = new int[1][];
		TermPositionVector[] tfv = new TermPositionVector[1];
		for (int i = 0; i < effDocuments; i++) {
			strterms[0] = terms[i];
			freqs[0] = freqss[i];
			tfv[0] = tfvs[i];

			selector.assignTermWeights(strterms, freqs, tfv, QEModel);
			expTerms[i] = selector.getMostWeightedTerms(selector
					.getNumberOfUniqueTerms());
		}
		// merge expansion terms: compute mean term weight for each term, sort
		// again
		TObjectFloatHashMap<String> termidWeightMap = new TObjectFloatHashMap<String>();
		TObjectFloatHashMap<String> termidDFMap = new TObjectFloatHashMap<String>();
		for (int i = 0; i < effDocuments; i++) {
			for (int j = 0; j < expTerms[i].length; j++) {
				termidWeightMap.adjustOrPutValue(expTerms[i][j].getTerm(),
						expTerms[i][j].getWeightExpansion(),
						expTerms[i][j].getWeightExpansion());
				termidDFMap.adjustOrPutValue(expTerms[i][j].getTerm(), 1, 1);
			}
		}

		ExpansionTerm[] candidateTerms = new ExpansionTerm[termidWeightMap
				.size()];
		float total = 0;
		int counter = 0;
		for (String termid : termidWeightMap.keys(new String[0])) {
			candidateTerms[counter] = new ExpansionTerm(termid, 0);
			if (effDocuments > this.EXPANSION_MIN_DOCUMENTS
					&& termidDFMap.get(termid) < this.EXPANSION_MIN_DOCUMENTS
					&& !originalQueryTermidSet.contains(termid))
				candidateTerms[counter].setWeightExpansion(0);
			else {
				float weight = termidWeightMap.get(termid) / effDocuments;
				candidateTerms[counter].setWeightExpansion(weight);
				total += weight;
			}
			counter++;
		}
		Arrays.sort(candidateTerms);
		termMap = new HashMap<String, ExpansionTerm>();

		float normaliser = candidateTerms[0].getWeightExpansion();
		if (LanguageModel) {
			if (QEModel.PARAMETER_FREE) {
				for (ExpansionTerm term : candidateTerms) {
					term.setWeightExpansion(term.getWeightExpansion() / total);
					termMap.put(term.getTerm(), term);
				}
			} else {
				for (ExpansionTerm term : candidateTerms) {
					term.setWeightExpansion(term.getWeightExpansion()
							/ normaliser);
					termMap.put(term.getTerm(), term);
				}
			}
		} else {// normalize the expansion weights by the maximum weight among
				// the expansion terms
			for (ExpansionTerm term : candidateTerms) {
				term.setWeightExpansion(term.getWeightExpansion() / normaliser);
				termMap.put(term.getTerm(), term);
			}
		}

	}

}
