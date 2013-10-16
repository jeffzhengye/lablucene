/**
 * 
 */
package org.apache.lucene.postProcess.termselector;

import gnu.trove.TObjectFloatHashMap;
import gnu.trove.TObjectFloatIterator;
import gnu.trove.TObjectIntHashMap;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;

import org.apache.log4j.Logger;
import org.apache.lucene.index.TermFreqVector;
import org.apache.lucene.index.TermPositionVector;
import org.apache.lucene.postProcess.QueryExpansionModel;
import org.apache.lucene.search.model.Idf;
import org.apache.lucene.search.model.WeightModelManager;
import org.apache.lucene.search.model.WeightingModel;
import org.dutir.lucene.util.ApplicationSetup;
import org.dutir.lucene.util.TermsCache;
import org.dutir.lucene.util.ExpansionTerms.ExpansionTerm;
import org.dutir.lucene.util.TermsCache.Item;
import org.dutir.util.Arrays;
import org.dutir.util.Math;

/**
 *  * Modelbased Feedback -- Zhai, CIKM2001
 *  Actually, this class barely used main methods (getTerms, ) in its superclass. 
 *  
 * @author zheng
 *
 */


public class ModelBasedSelector extends TermSelector {
	private static Logger logger =  Logger.getLogger(ModelBasedSelector.class);
	private float lambda = Float.parseFloat(ApplicationSetup.getProperty(
			"ModelBased.lambda", "0.8"));
	
	private int modelNum = Integer.parseInt(ApplicationSetup.getProperty("ModelBased.kind", "1"));
	
	
	public ModelBasedSelector(){
		super();
		this.setMetaInfo("normalize.weights", "true");
	}
	
	public void assignTermWeights(int[] docids, float scores[], QueryExpansionModel QEModel) {
		String[][] termCache = null;
		int[][] termFreq = null;
		termMap = new HashMap<String, ExpansionTerm>();
		this.feedbackSetLength = 0;
		termCache = new String[docids.length][];
		termFreq = new int[docids.length][];
		TObjectFloatHashMap<String> termscoreMap = new TObjectFloatHashMap<String>();
		TObjectIntHashMap<String> dfMap = new TObjectIntHashMap<String>();
		HashSet<String> termSet = new HashSet<String>();
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
				termCache[i] = strterms;
				termFreq[i] = freqs;
				java.util.Collections.addAll(termSet, strterms);
			}
		}
		String vocabulary[] = new String[termSet.size()];
		termSet.toArray(vocabulary);
		java.util.Arrays.sort(vocabulary); 
		
		float averageFiledLength = searcher.getAverageLength(field);
		float numberOfTokens = searcher.getNumTokens(field);
		float numberOfUniqueTerms = searcher.getNumUniqTokens(field);
		for (int i = 0; i < docids.length; i++) {
			int len = Arrays.sum(termFreq[i]);
			
			int start =0, end = termCache[i].length;
			for(int j =0; j < vocabulary.length && start < end ; j++){
				int pos = java.util.Arrays.binarySearch(termCache[i], start, end, vocabulary[j]);
				float weight =0;
				Item item = this.getItem(vocabulary[j]);
				float ctf = item.ctf;
				float cdf = item.df;
				if(pos < 0){
					start = -pos -1;
					weight = score(0, len, ctf, cdf, 1, numberOfTokens); //LM doc score
				}else{
					start = pos+ 1;
					weight = score(termFreq[i][pos], len, ctf, cdf, 1, numberOfTokens);//LM doc score
//					weight = Math.log2(termFreq[i][pos] / (double)len);
					dfMap.adjustOrPutValue(vocabulary[j], 1, 1);
				}
				//LM doc score minus \lambda * log (P(w|C)
//				weight = (float) (weight - lambda * org.dutir.util.Math.log(ctf/numberOfTokens));
//				weight =  weight ==0 ? 0 : (weight - lambda *score(0, len, ctf, cdf, 1, numberOfTokens)); 
				weight =  weight ==0 ? 0 : (weight - lambda * org.dutir.util.Math.log(ctf/numberOfTokens));
				termscoreMap.adjustOrPutValue(vocabulary[j], weight, weight); 
			}
			
		}
		
		float normaliser =0;
		float sum =0;
		Object keys[] = termscoreMap.keys();
		float values[] = termscoreMap.getValues();
		float max = org.dutir.util.Arrays.findMax(values); 
		float min = org.dutir.util.Arrays.findMin(values);
		for(int i =0; i < keys.length; i++){
			String term = (String) keys[i];
			float weight = values[i];
			int df = dfMap.get(term);
			if( df < EXPANSION_MIN_DOCUMENTS ){
				continue;
			}else{
				weight = (float) java.lang.Math.exp( values[i] / (docids.length  *(1 -lambda)));
				ExpansionTerm exTerm = new ExpansionTerm(term, 0);
				exTerm.setWeightExpansion(weight);
				this.termMap.put(term,exTerm);
				sum += weight;
				if(normaliser < weight){
					normaliser = weight;
				}
			}
		}
		
		Object[] arr = termMap.values().toArray();
		ExpansionTerm[] allTerms = new ExpansionTerm[arr.length];
		final int len = allTerms.length;
		for(int i=0;i<len;i++)
			allTerms[i] = (ExpansionTerm)arr[i];
		if(QEModel.PARAMETER_FREE){
			for (ExpansionTerm term : allTerms){
				float weight = term.getWeightExpansion()/sum;
				term.setWeightExpansion(weight);
		}
		}else{
			for (ExpansionTerm term : allTerms){
				float weight = term.getWeightExpansion()/normaliser;
				term.setWeightExpansion(weight);
		}
		}

		
	}

	
	static float mu = Integer.parseInt(ApplicationSetup.getProperty("dlm.mu", "1000"));
	public float score(float tf, float docLength, float ctf, float cdf,
			float keyFrequency, float totoalTerms) {
//		float docLevel = tf /docLength;
		float colLevel = ctf / totoalTerms;
		return   (float) (keyFrequency * 
				org.dutir.util.Math.log( (tf + mu * colLevel)/(docLength + mu) ));
	}
	
	
	@Override
	public String getInfo() {
		return "ModelBased";
	}

	@Override
	public void assignTermWeights(String[][] terms, int[][] freqs,
			TermPositionVector[] tfvs, QueryExpansionModel QEModel) {
		// TODO Auto-generated method stub
		
	}
	
}
