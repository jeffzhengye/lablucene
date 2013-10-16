package org.apache.lucene.postProcess.termselector;


import java.io.IOException;
import java.util.Arrays;

import org.apache.log4j.Logger;
import org.apache.lucene.index.TermPositionVector;
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
