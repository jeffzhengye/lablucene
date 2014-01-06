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


import gnu.trove.TObjectDoubleHashMap;

import java.io.IOException;
import java.util.Arrays;

import org.apache.log4j.Logger;
import org.apache.lucene.index.TermPositionVector;
import org.apache.lucene.postProcess.MATF;
import org.apache.lucene.postProcess.QueryExpansionModel;
import org.dutir.lucene.util.ApplicationSetup;
import org.dutir.lucene.util.TermsCache;
import org.dutir.lucene.util.ExpansionTerms.ExpansionTerm;



public class DFRTermSelector extends TermSelector {
	/** The logger used */
	private static Logger logger =  Logger.getLogger(DFRTermSelector.class);
	
	public DFRTermSelector(){
		super();
		this.setMetaInfo("normalize.weights", "true");
	}
	
	TObjectDoubleHashMap<String> fscore = null;
	
	public TObjectDoubleHashMap<String> getFscore() {
		return fscore;
	}
	
	public void setFscore(TObjectDoubleHashMap<String> fscore) {
		this.fscore = fscore;
	}
	
	public void assignTermWeights(int[] docids, float scores[], QueryExpansionModel QEModel) {
		this.getTerms(docids);
		//feedbackSetLength is computed when getTerms(docids)
		assign(docids.length, QEModel);
	}

	@Override
	public String getInfo() {
		return "DFR";
	}


	@Override
	public void assignTermWeights(String[][] terms, int[][] freqs,
			TermPositionVector[] tfvs, QueryExpansionModel QEModel) {
		this.getTerms(terms, freqs, tfvs);
		assign(terms.length, QEModel);
	}
	
	/**
	 * 
	 * @param length : doc number of QE set
	 * @param QEModel
	 */
	private void assign(int length, QueryExpansionModel QEModel){
		QEModel.setTotalDocumentLength(this.feedbackSetLength);
		
		// NOTE: if the set of candidate terms is empty, there is no need to
		// perform any term re-weighing.
		if (termMap.size() == 0) {
			return;
		}
		
		// weight the terms
		Object[] arr = termMap.values().toArray();
		ExpansionTerm[] allTerms = new ExpansionTerm[arr.length];
		final int len = allTerms.length;
		for(int i=0;i<len;i++)
			allTerms[i] = (ExpansionTerm)arr[i];
		boolean classicalFiltering = Boolean.parseBoolean(ApplicationSetup.getProperty("expansion.classical.filter", "true"));
		float total =0;
		for (int i=0; i<len; i++){
			try{
				//only consider terms which occur in 2 or more documents. Alter using the expansion.mindocuments property.
				
				if (classicalFiltering && length > 1 && allTerms[i].getDocumentFrequency() < EXPANSION_MIN_DOCUMENTS &&
						!originalQueryTermidSet.contains(allTerms[i].getTerm())){
					allTerms[i].setWeightExpansion(0);
					continue;
				}
				/**
				 * 17/02/2009 Ben: this condition is changed to: only consider terms which occur in at least half of the feedback documents. 
				 */
				else if (!classicalFiltering){
					int minDocs = (length%2==0)?(length/2-1):(length/2);
					if (length>1&&allTerms[i].getDocumentFrequency() < minDocs &&
						!originalQueryTermidSet.contains(allTerms[i].getTerm())){
						allTerms[i].setWeightExpansion(0);
						continue;
					}
				}
				
				
				TermsCache.Item item = getItem(allTerms[i].getTerm());
				float TF = item.ctf;
				float DF = item.df;
				QEModel.setAVF(ATF); // currently this is for MAFT query expansion.
				if(fscore != null && QEModel instanceof MATF){
					((MATF) QEModel).setKtf((float) fscore.get(allTerms[i].getTerm()));
				}
//				assert(originalQueryLength != 0);
//				QEModel.setOriginalQueryLength(originalQueryLength);
//				logger.warn("" + originalQueryLength + ":" + QEModel.getOriginalQueryLength());
				float weight = QEModel.score(allTerms[i].getWithinDocumentFrequency(), TF, DF);
				allTerms[i].setWeightExpansion(weight);		
				total += weight;
			} catch(NullPointerException npe) {
				logger.fatal("A nullpointer exception occured while iterating over expansion terms at iteration number: "+"i = " + i,npe);
			}
		}
		
		
		// normalizeWeights : true means using Rochio, false means not using Rochio
		boolean normalizeWeights = Boolean.parseBoolean(metaMap.get("normalize.weights"));

		Arrays.sort(allTerms);
		
		// determine normalizing factor
		float normaliser = allTerms[0].getWeightExpansion();
		if (QEModel.PARAMETER_FREE && QEModel.SUPPORT_PARAMETER_FREE_QE){
			normaliser = QEModel.parameterFreeNormaliser(
					allTerms[0].getWithinDocumentFrequency(), 
					QEModel.getCollectionLength(), feedbackSetLength);
		}
		
//		System.out.println(metaMap.get("normalize.weights") +" " + normalizeWeights);
		// add all terms to the returning collection
		if(LanguageModel ){
			if (normalizeWeights){ // not Rocchio
				if(QEModel.PARAMETER_FREE){
					for (ExpansionTerm term : allTerms){
						if (normaliser != 0) {
							// if QEModel.PARAMETER_FREE == true, normaliser == total
							// else normaliser == max(allTerms)
							term.setWeightExpansion(term.getWeightExpansion()/total);
						}
					}
				}else{
					for (ExpansionTerm term : allTerms){
						if (normaliser != 0) {
							// if QEModel.PARAMETER_FREE == true, normaliser ==1 
							// else normaliser == max(allTerms)
							term.setWeightExpansion(term.getWeightExpansion()/normaliser);
						}
					}
				}

			}else{ // Using Rocchio
				// do nothing, keep the original score
			}
			
		}else{
			if (normalizeWeights) 
				for (ExpansionTerm term : allTerms){
					if (normaliser != 0) {
						term.setWeightExpansion(term.getWeightExpansion()/normaliser);
					}
				}
		}
	}

}
