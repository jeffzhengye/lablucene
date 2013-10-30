/**
 * 
 */
package org.apache.lucene.postProcess.termselector;


import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.log4j.Logger;
import org.apache.lucene.index.TermPositionVector;
import org.apache.lucene.postProcess.QueryExpansionModel;
import org.apache.lucene.search.model.Idf;
import org.dutir.lucene.IndexUtility;
import org.dutir.lucene.util.ApplicationSetup;
import org.dutir.lucene.util.ExpansionTerms.ExpansionTerm;
import org.dutir.lucene.util.TermsCache.Item;
import org.dutir.util.Arrays;

/**
 * This is an implementation for the Positional Relevance Model variant 2
 * proposed by Yuanhua Lv & Chengxiang Zhai Paper: 
 * "Positional Relevance Model for Pseudo-Relevance Feedback" 
 * @see http://dl.acm.org/citation.cfm?id=1835546
 * @author Jun Miao 
 * @since  17/10/2013
 * 
 */
public class PRM2TermSelector extends TermSelector {

	private static Logger logger = Logger.getLogger(PRM2TermSelector.class);
	// private float lambda = Float.parseFloat(ApplicationSetup.getProperty(
	// "expansion.lavrenko.lambda", "0.15"));
	IndexUtility indexUtil = null; // used to obtain statistical information for
									// terms,e.g., idf
	TermSelector selector = null;

	float scores[] = null;

	public void setScores(float _scores[]) {
		scores = new float[_scores.length];
		float max = _scores[0];
		for (int i = 0; i < _scores.length; i++) {
			// scores[i] = (float) Math.exp(_scores[i] +_scores[0]);
			scores[i] = Idf.exp(max + _scores[i]);
		}
	}

	final double dmu = Double.parseDouble(ApplicationSetup.getProperty("dlm.mu", "1500"));
	
	final double lambda = Double.parseDouble(ApplicationSetup.getProperty(
			"prmJM.lambda", "0.5"));
	
	final double sigma = Double.parseDouble(ApplicationSetup.getProperty(
			"guassian.sigma", "0.5"));

	// float numOfTokens = this.searcher.getNumTokens(field);

	
	/**
	 * Return the score of a feedback term with positional information in a feedback term
	 * Implementation of the main part of Formula 6
	 * @param fbTerm The object which contains relative information of a feedback term
	 * 
	 * @param queryTermMap The HashMap which contains the information of all original query terms
	 * 
	 * @param lambda The tune parameter for JM smoothing in calling queryTermProbabilityWithPosition() method
	 * 
	 * @param sigma The parameter for the Guassian kernel in calling queryTermProbabilityWithPosition() method
	 * 
	 * @param index The index of feedback document in the feedback doc set (Different to document ids in the collection index)
	 * 
	 * @param queryProb The normalized P(Q|D_i)/sum{P(Q|D_i)} where D_i belongs to the feedback doc set
	 * 
	 * @param sumProbQryAtPos The sum of P(Q|D,i) for feedback document D
	 * 
	 * @return The score of a feedback term given the original query with positional information in a feedback document
	 * 
	 * @author Jun Miao 
	 * @since 10/18/2013 
	 */
	private double score(FbTermInfo fbTerm, HashMap<String, FbTermInfo> queryTermMap, 
			double lambda, double sigma, int index, double queryProb, double sumProbQryAtPos) {

		double returnScore = 0.0;
		
		int [] positions = fbTerm.getpositionPerDoc(index);
		for (int i = 0; i < positions.length; i++){
			
			returnScore += queryProb * probQueryAtPos(queryTermMap, positions[i], lambda, sigma, index) /sumProbQryAtPos;
		}
		
		return returnScore;

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.apache.lucene.postProcess.termselector.TermSelector#assignTermWeights
	 * (int[], org.apache.lucene.postProcess.QueryExpansionModel) This is used
	 * to assign weights for the candidate feedback terms
	 */
	@Override
	public void assignTermWeights(int[] docids, float scores[],
			QueryExpansionModel QEModel) {

		feedbackSetLength = 0;
		termMap = new HashMap<String, ExpansionTerm>();
		float docLens[] = new float[docids.length];

		if (indexUtil == null)
			indexUtil = new IndexUtility(this.searcher);
		
		HashMap<String, FbTermInfo> feedbackTermMap = new HashMap<String, FbTermInfo>();
		HashMap<String, FbTermInfo> queryTermMap = new HashMap<String, FbTermInfo>();
		
		
		// get all terms in feedback documents as candidate expansion terms
		
		for (int fbDocIndex = 0; fbDocIndex < docids.length; fbDocIndex++) { //Go through all the feedback docs and store term information
			int docid = docids[fbDocIndex];
			TermPositionVector tfv = null;
			try {
				tfv = (TermPositionVector) this.searcher.getIndexReader()
						.getTermFreqVector(docid, field);
			} catch (IOException e) {
				e.printStackTrace();
			}// Interesting! TermPositionVector is actually the same as
				// TermFreqVector
			if (tfv == null)
				logger.warn("document " + docid + " not found, field=" + field);
			else {
				String strterms[] = tfv.getTerms();
				int freqs[] = tfv.getTermFrequencies();
				int dl = Arrays.sum(freqs);
				docLens[fbDocIndex] = dl;

				
				for (int j = 0; j < strterms.length; j++) {     //Get all the relative information and store it in the two HashMaps
					FbTermInfo fbTerm = feedbackTermMap.get(strterms[j]);
					if (fbTerm == null) {
						fbTerm = new FbTermInfo(docids.length);
						fbTerm.setdocIds(docids[fbDocIndex], fbDocIndex);
						Item item = getItem(strterms[j]);
						fbTerm.setcollectionProbability(item.ctf);
						fbTerm.setpositionPerDoc(tfv.getTermPositions(j), fbDocIndex);
						fbTerm.setfbDocLength(dl, fbDocIndex);
						fbTerm.setTfPerDoc(freqs[j], fbDocIndex);
						feedbackTermMap.put(strterms[j], fbTerm);
						
						if (this.originalQueryTermidSet.contains(strterms[j]))
							queryTermMap.put(strterms[j], fbTerm);
						
					}
					
				}//Get all the relative information and store it in the two HashMaps
				
							
				
			}//end of ELSE
		}//Go through all the feedback docs and store term information
		
		double queryProbInAllDoc = 0;
		double [] qryProbInDoc = new double[docids.length];
		
		
		for (int k = 0; k < docids.length; k++){   
			qryProbInDoc[k] = this.queryProbInDoc(queryTermMap, k, this.dmu);
			queryProbInAllDoc += qryProbInDoc[k];					
		}//Get P(Q|D_i) and the sum of P(Q|D_i) in all feedback docs
		
		double [] queryPosScoreSum = new double[docids.length];;
		for (int k = 0; k < docids.length; k++){   //Get sum P(Q|D,i) for each feedback document
			queryPosScoreSum[k] = 0;
			int docLen = 0;
			
			Iterator<Map.Entry<String, FbTermInfo>> it = queryTermMap.entrySet().iterator();  
	        while (it.hasNext()) {  
	            Entry<String, FbTermInfo> entry = it.next();  
	            FbTermInfo qterm = entry.getValue();
	            docLen = qterm.getfbDocLength(k);
	        }  
			
	        //This can be very time-consuming but no other ways so far
	        for (int length = 1; length <= docLen; length++)
	        	queryPosScoreSum[k] += probQueryAtPos(queryTermMap, length, this.lambda, this.sigma, k); 
		}//Get sum P(Q|D,i) for each feedback document
		
		
		/*Go through all the feedback docs and set term scores in each doc by applying position information*/
		for (int fbDocIndex = 0; fbDocIndex < docids.length; fbDocIndex++) {
			int docid = docids[fbDocIndex];
			TermPositionVector tfv = null;
			try {
				tfv = (TermPositionVector) this.searcher.getIndexReader()
						.getTermFreqVector(docid, field);
			} catch (IOException e) {
				e.printStackTrace();
			}
			if (tfv == null)
				logger.warn("document " + docid + " not found, field=" + field);
			else {
				String strterms[] = tfv.getTerms();
			
			double normalizedProb =  qryProbInDoc[fbDocIndex]/queryProbInAllDoc;
			for (int j = 0; j < strterms.length; j++) {
				FbTermInfo fbterm = feedbackTermMap.get(strterms[j]);
				if (fbterm != null){
					double positionalScore = 0;
					
					positionalScore = score(fbterm, queryTermMap, this.lambda, this.sigma, fbDocIndex, normalizedProb, queryPosScoreSum[fbDocIndex]);
					fbterm.setWeightPerDoc(positionalScore, fbDocIndex);
					feedbackTermMap.put(strterms[j], fbterm);
					
				}
				
			}	
			
		}/*Go through all the feedback docs and set term scores in each doc by applying position information*/
	}
		
		
		//Add up the scores of each feedback terms and normalize the final score to [0,1]
		
		int termNum = feedbackTermMap.size();
		ExpansionTerm[] exTerms = new ExpansionTerm[termNum];
		if(logger.isDebugEnabled()) logger.debug("the total number of terms in feedback docs: " + termNum);
		
		float total = 0;
		int fbTermCount = 0;
		float sum = 0;
		for (Entry<String, FbTermInfo> entry : feedbackTermMap.entrySet()) {
			String w = entry.getKey();
			FbTermInfo fbTerm = entry.getValue();
			float weight = 0;
			for (int i = 0; i < fbTerm.docNumber; i++) {
				weight += fbTerm.getWeightPerDoc(i);
			}

			if (fbTerm.docNumber < EXPANSION_MIN_DOCUMENTS) {
				weight = 0;
			}

			total += weight;

			exTerms[fbTermCount] = new ExpansionTerm(w, 0);
			exTerms[fbTermCount].setWeightExpansion(weight);
			fbTermCount++;
			sum += weight;
		}

		termNum = fbTermCount;
		
		
		java.util.Arrays.sort(exTerms);

		if (logger.isDebugEnabled()) {
			StringBuilder buf = new StringBuilder();
			int tmpPos = 0;
			for (int i = 0; tmpPos < 40 && i < exTerms.length; i++) {
				if (exTerms[i].getWeightExpansion() < 1) {
					tmpPos++;
					buf.append(exTerms[i] + "\t");
				}
			}
			if(logger.isDebugEnabled()) logger.debug("original: " + buf.toString());
		}

		if(logger.isDebugEnabled()) logger.debug("the total weight: " + total);
		if(logger.isDebugEnabled()) logger.debug("maxWeight=" + exTerms[0].getWeightExpansion()
				+ ", minWeight="
				+ exTerms[exTerms.length - 1].getWeightExpansion());
		
		StringBuilder buf = new StringBuilder();
		for (fbTermCount = 0; fbTermCount < termNum; fbTermCount++) {

			if (logger.isDebugEnabled()
					&& this.originalQueryTermidSet.contains(exTerms[fbTermCount]
							.getTerm())) {
				buf.append(exTerms[fbTermCount] + "\t");
			}
			exTerms[fbTermCount].setWeightExpansion(exTerms[fbTermCount].getWeightExpansion() / sum);
			this.termMap.put(exTerms[fbTermCount].getTerm(), exTerms[fbTermCount]);
		}

		if (logger.isDebugEnabled()) {
			logger.debug("original Query Weight: " + buf.toString());
		}

	}

	
	/**
	 * Return the query probability given a document.
	 * 
	 * @param queryTermMap All query terms denoted by the fbTermInfo objects
	 * 
	 * @param docIndex The index of feedback document in the feedback document set
	 * 
	 * @param mu The tune parameter for Dirichlet smoothing
	 * 
	 * @return The query probability given the docIndex-th feedback document and a
	 * position
	 * 
	 * @author Jun Miao 
	 * @since 10/18/2013 
	 */

	private double queryProbInDoc(
			HashMap<String, FbTermInfo> queryTermMap, int docIndex, double mu) {
		
		double probability = 1;
		
		Iterator<Map.Entry<String, FbTermInfo>> it = queryTermMap.entrySet().iterator();  
        while (it.hasNext()) {  
            Entry<String, FbTermInfo> entry = it.next();  
            FbTermInfo qterm = entry.getValue();
            double colProbability = qterm.getcollectionProbability();
            double queryTermProb = (qterm.getTfPerDoc(docIndex) + mu * colProbability)/(qterm.getfbDocLength(docIndex));
            probability *= queryTermProb; 
              
    
        }  
		
        return probability;
	}
	
	

	/**
	 * Return the query probability given a document and a position.
	 * Implementation of Formula 17 P(Q|D,i)
	 * 
	 * @param queryTermMap All query terms denoted by the fbTermInfo objects
	 * 
	 * @param i The position which feedback term w can associate
	 * 
	 * @param lambda The tune parameter for JM smoothing in calling
	 * queryTermProbabilityWithPosition() method
	 * 
	 * @param sigma The parameter for the Guassian kernel in calling
	 * queryTermProbabilityWithPosition() method
	 * 
	 * @param docIndex The index of feedback document in the feedback document set
	 * 
	 * @return The query probability given the index-th feedback document and a
	 * position
	 * 
	 * @author Jun Miao 10/15/2013 *
	 */

	private double probQueryAtPos(
			HashMap<String, FbTermInfo> queryTermMap, int i, double lambda,
			double sigma, int docIndex) {
		
		double probability = 1;
		
		Iterator<Map.Entry<String, FbTermInfo>> it = queryTermMap.entrySet().iterator();  
        while (it.hasNext()) {  
            Entry<String, FbTermInfo> entry = it.next();  
            FbTermInfo fbterminfo = entry.getValue();
            int []positions = fbterminfo.getpositionPerDoc(docIndex);
            double colProbability = fbterminfo.getcollectionProbability();
            
            probability *= queryTermProbAtPos(positions,
        			i, lambda, sigma, colProbability);              
    
        }  
		
        return probability;
	}

	/**
	 * Return the smoothed probability of a term w appearing at position i in a
	 * feedback document based on positional language model (plm).
	 * Implementation of Formula 16
	 * 
	 * @param positionVector All positions of term w in the feedback document
	 * 
	 * @param i The position which term w can associate
	 * 
	 * @param lambda The tune parameter for JM smoothing
	 * 
	 * @param sigma The parameter for the Guassian kernel
	 * 
	 * @param colProbability The collection probability of term w
	 * 
	 * @return The smoothed probability of a term w appearing at position i in a
	 * feedback document
	 * 
	 * @author Jun Miao 
	 * @since 10/11/2013 
	 */

	private double queryTermProbAtPos(int[] positionVector,
			int i, double lambda, double sigma, double colProbability) {
		double probability;

		double plmProbability = propagatedCount(positionVector, i, sigma)
				/ Math.sqrt((2 * Math.PI * Math.pow(sigma, 2.0)));
		probability = (1 - lambda) * plmProbability + lambda * colProbability;
		return probability;

	}

	/**
	 * Return the total propagated count of term w at position i from the
	 * occurrences of w in all the positions. An implementation of c'(w,i) and
	 * Gaussian kernel is used. Implementation of Formula 13
	 * 
	 * @param positionVector All positions of term w in the feedback document
	 * 
	 * @param i The position which term w can associate
	 * 
	 * @param sigma The parameter for the Guassian kernel
	 * 
	 * @return The total propagated count of term w at position i from the
	 * occurrences of w in all the positions. Actually, it denotes the
	 * association of a term w on the term at position i.
	 * 
	 * @author Jun Miao 10/08/2013 *
	 */

	private double propagatedCount(int[] positionVector, int i, double sigma) {

		double count = 0;
		for (int j = 0; j < positionVector.length; j++) {
			count += Math.exp(-Math.pow(i - positionVector[j], 2.0) 
					/ (2 * Math.pow(sigma, 2.0)));
		}
		return count;

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.lucene.postProcess.termselector.TermSelector#getInfo()
	 */
	@Override
	public String getInfo() {
		// TODO Auto-generated method stub
		return "PRM2";
	}

	@Override
	public void assignTermWeights(String[][] terms, int[][] freqs,
			TermPositionVector[] tfvs, QueryExpansionModel QEModel) {
		// TODO Auto-generated method stub

	}
}
	