/**
 * 
 */
package org.apache.lucene.postProcess.termselector;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;

import org.apache.log4j.Logger;
import org.apache.lucene.index.TermFreqVector;
import org.apache.lucene.index.TermPositionVector;
import org.apache.lucene.postProcess.QueryExpansionModel;
import org.apache.lucene.search.model.Idf;

import org.dutir.lucene.util.ApplicationSetup;
import org.dutir.lucene.util.Rounding;
import org.dutir.lucene.util.ExpansionTerms.ExpansionTerm;
import org.dutir.lucene.util.TermsCache.Item;

/**
 * @author zheng
 * 
 */
public class RMTermSelector extends TermSelector {

	private static Logger logger = Logger.getLogger(RMTermSelector.class);
//	private float lambda = Float.parseFloat(ApplicationSetup.getProperty(
//			"expansion.lavrenko.lambda", "0.15"));

	static class Structure {
		/**
		 * document level LM score (dir smoothing) for each candidate word in docs. 
		 */
		float wordDoc[];
		float ctf =0;
		float cf =0; 
		float collectWeight = -1;
		int df = 0;

		public Structure(int len) {
			wordDoc = new float[len];
		}
		
		public String toString(){
			StringBuilder buf = new StringBuilder();
			buf.append("df = " + df +", ");
			for(int i =0; i < wordDoc.length; i++){
				buf.append("" +Rounding.round( wordDoc[i], 5) + ", ");
			}
			return buf.toString();
		}
	}
	
	float scores[] = null;
	public void setScores(float _scores[]){
		scores = new float[_scores.length];
		for(int i=0; i < _scores.length; i++){
//			scores[i] = (float) Math.exp(_scores[i] +_scores[0]);
			scores[i] = Idf.exp(_scores[i]);
		}
	}
	static String dmu = ApplicationSetup.getProperty("dlm.mu", "500");
	static float mu = Integer.parseInt(ApplicationSetup.getProperty("rm.mu", dmu));
//	float numOfTokens = this.searcher.getNumTokens(field);
	public float score(float tf, float docLength, float termFrequency, float numberOfTokens) {
		float pc = termFrequency / numberOfTokens;
		return  (tf + mu * pc) / (docLength + mu);
	}
	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.apache.lucene.postProcess.termselector.TermSelector#assignTermWeights
	 * (int[], org.apache.lucene.postProcess.QueryExpansionModel)
	 */
	@Override
	public void assignTermWeights(int[] docids, float scores[], QueryExpansionModel QEModel) {
		
		float numOfTokens = this.searcher.getNumTokens(field);
		feedbackSetLength = 0;
		termMap = new HashMap<String, ExpansionTerm>();
		float PD[] = new float[docids.length];
		float docLens[] = new float[docids.length];
		
		HashMap<String, Structure> map = new HashMap<String, Structure>();
		for (int i = 0; i < docids.length; i++) {
			int docid = docids[i];
			TermFreqVector tfv = null;
			try {
				tfv = this.searcher.getIndexReader().getTermFreqVector(docid,
						field);
			} catch (IOException e) {
				e.printStackTrace();
			}
			if (tfv == null)
				logger.warn("document " + docid + " not found, field=" + field);
			else {
				String strterms[] = tfv.getTerms();
				int freqs[] = tfv.getTermFrequencies();
				float dl = searcher.getFieldLength(field, docid);
				docLens[i] = dl;
				feedbackSetLength += dl;

				for (int j = 0; j < strterms.length; j++) {
//					this.insertTerm(strterms[j], freqs[j]);
					feedbackSetLength += freqs[j];
					Structure stru = map.get(strterms[j]);
					
					if (stru == null) {
						stru = new Structure(docids.length);
						Item item = getItem(strterms[j]);
						stru.ctf = item.ctf; 
						java.util.Arrays.fill(stru.wordDoc, 0);
						map.put(strterms[j], stru);
					}
					stru.wordDoc[i] = score(freqs[j], dl, stru.ctf, numOfTokens);
					stru.df++;
				}
			}
		}

		Structure[] queryStru = new Structure[this.originalQueryTermidSet
				.size()];
		int pos = 0;
		
		HashSet<String> tmpSet = new HashSet<String>();
		tmpSet.addAll(originalQueryTermidSet); tmpSet.addAll(map.keySet());
		for (String term : tmpSet) {
			Structure stru = map.get(term);

			if (stru == null) {
				stru = new Structure(docids.length);
				Item item = getItem(term);
				stru.ctf = item.ctf; 
				java.util.Arrays.fill(stru.wordDoc, 0);
				map.put(term, stru);
			}
			for(int k=0; k < docLens.length; k++){
				if(stru.wordDoc[k] == 0){
					stru.wordDoc[k] = score(0, docLens[k], stru.ctf, numOfTokens); //not log
				}
			}
			if(this.originalQueryTermidSet.contains(term)){
				queryStru[pos++] = stru;
			}
		}
		float uniform = 1 / (float) docids.length;
		java.util.Arrays.fill(PD, uniform);
		
		float PQ[] = new float[docids.length];
		java.util.Arrays.fill(PQ, 1);
		for (int i = 0; i < PQ.length; i++) {
			for (int j = 0; j < queryStru.length; j++) {
				PQ[i] *= queryStru[j].wordDoc[i];
			}
//			PQ[i] = scores[i];
		}
		indriNorm(PQ);
		
		int termNum = map.size();
		ExpansionTerm[] exTerms = new ExpansionTerm[termNum];
		if(logger.isDebugEnabled()) logger.debug("the total number of terms in feedback docs: " + termNum);
		
		// **************RM1 -- Indri implementation**********************//
		float total = 0;
		pos = 0;
		float sum = 0;
		for (Entry<String, Structure> entry : map.entrySet()) {
			String w = entry.getKey();
			Structure ws = entry.getValue();
			float weight = 0;
			for (int i = 0; i < ws.wordDoc.length; i++) {
				weight += PD[i] * ws.wordDoc[i] * PQ[i];
			}

			if (ws.df < EXPANSION_MIN_DOCUMENTS) {
				weight = 0;
			}

			total += weight;

			exTerms[pos] = new ExpansionTerm(w, 0);
			exTerms[pos].setWeightExpansion(weight);
			pos++;
			sum += weight;
		}

		termNum = pos;

		java.util.Arrays.sort(exTerms);

		if (logger.isDebugEnabled()) {
			StringBuilder buf = new StringBuilder();
			int tmpPos = 0;
			for (int i = 0; tmpPos < 40 && i < exTerms.length; i++) {
				if (true || exTerms[i].getWeightExpansion() < 1) {
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
		for (pos = 0; pos < termNum; pos++) {

			if (logger.isDebugEnabled()
					&& this.originalQueryTermidSet.contains(exTerms[pos]
							.getTerm())) {
				buf.append(exTerms[pos] + "\t");
			}
			exTerms[pos].setWeightExpansion(exTerms[pos].getWeightExpansion() / sum);
			this.termMap.put(exTerms[pos].getTerm(), exTerms[pos]);
		}

		if (logger.isDebugEnabled()) {
			logger.debug("original Query Weight: " + buf.toString());
		}
	}

	private void indriNorm(float[] pQ) {

		float K = pQ[0]; // first is max
		float sum = 0;

		for (int i=0; i < pQ.length; i++) {
			pQ[i] = K * pQ[i];
			sum += pQ[i];
		}
		for (int i=0; i < pQ.length; i++) {
			 pQ[i] /= sum;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.lucene.postProcess.termselector.TermSelector#getInfo()
	 */
	@Override
	public String getInfo() {
		// TODO Auto-generated method stub
		return "RM3_";
	}

	@Override
	public void assignTermWeights(String[][] terms, int[][] freqs,
			TermPositionVector[] tfvs, QueryExpansionModel QEModel) {
		throw new UnsupportedOperationException();
	}

}
