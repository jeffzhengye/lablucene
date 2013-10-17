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
import org.apache.lucene.postProcess.termselector.RM3TermSelector;
import org.apache.lucene.postProcess.termselector.TermSelector;
import org.apache.lucene.search.model.Idf;

import org.dutir.lucene.util.ApplicationSetup;
import org.dutir.lucene.util.Rounding;
import org.dutir.lucene.util.ExpansionTerms.ExpansionTerm;
import org.dutir.lucene.util.TermsCache.Item;
import org.dutir.util.Arrays;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import gnu.trove.TObjectFloatHashMap;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

/**
 * @author zheng
 * 
 */
public class SRM3TermSelector extends TermSelector {

	private static Logger logger = Logger.getLogger(RM3TermSelector.class);
//	private float lambda = Float.parseFloat(ApplicationSetup.getProperty(
//			"expansion.lavrenko.lambda", "0.15"));
	private static String urlsim = ApplicationSetup.getProperty(
			"srm3.urlsim", "http://10.7.10.219:5000/sim");
	
	class Structure {
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
		float max = _scores[0];
		for(int i=0; i < _scores.length; i++){
//			scores[i] = (float) Math.exp(_scores[i] +_scores[0]);
			scores[i] = Idf.exp(max + _scores[i]);
		}
	}
	static String dmu = ApplicationSetup.getProperty("dlm.mu", "500");
	static float mu = Integer.parseInt(ApplicationSetup.getProperty("rm.mu", dmu));
//	float numOfTokens = this.searcher.getNumTokens(field);
	private float score(float tf, float docLength, float termFrequency, float numberOfTokens) {
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
		
		/*
		 map-- key: all terms  
		       value: stru ([lm scores in each document], ctf)
		*/
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
				float dl = Arrays.sum(freqs);
				docLens[i] = dl;
				feedbackSetLength += dl;

				for (int j = 0; j < strterms.length; j++) {
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
		
		HashSet<String> tmpSet = new HashSet<String>(); //all terms in docs + terms in Query
		tmpSet.addAll(originalQueryTermidSet); tmpSet.addAll(map.keySet());
		/**
		 *if the lm score is 0, it indicates not smooth.
		 *So smooth as follows: 
		 */
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
					stru.wordDoc[k] = score(0, docLens[k], stru.ctf, numOfTokens); //let tf=0 to smooth, not log
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
		
		//*****************************compute semantic scores***************************************
		String qstr = "";
		for(String term : originalQueryTermidSet){
			if( qstr.equals("") ){
				qstr += term;
			}else{
				qstr += ":" + term;
			}		
		}
		TObjectFloatHashMap<String> sem_map = new TObjectFloatHashMap<String>();
		float t_semscore = 0;
		for (String term : tmpSet) {
			float s = sim(qstr, term);
			sem_map.put(term, s); // it's original score, not normalized
			t_semscore += s;      //to be used.
		}
//		indriNorm();
		//******************************************************************************************
		
		int termNum = map.size();
		ExpansionTerm[] exTerms = new ExpansionTerm[termNum];
		if(logger.isDebugEnabled()) logger.debug("the total number of terms in feedback docs: " + termNum);
		
		// **************RM1 -- Indri implementation********************** //
		float total = 0;
		pos = 0;
		float sum = 0;
		for (Entry<String, Structure> entry : map.entrySet()) {
			String w = entry.getKey();
			Structure ws = entry.getValue();
			float weight = 0;
			for (int i = 0; i < ws.wordDoc.length; i++) {
				weight += PD[i] * ws.wordDoc[i] * (PQ[i] + sem_map.get(w));
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

	// In:  log(x1) log(x2) ... log(xN)
	// Out: x1/sum, x2/sum, ... xN/sum
	//
	// Extra care is taken to make sure we don't overflow
	// machine precision when taking exp (log x)
	// This is done by adding a constant K which cancels out
	// Right now K is set to maximally preserve the highest value
	// but could be altered to a min or average, or whatever...
	private void indriNorm(float[] pQ) {

//		float K = pQ[0]; // first is max
		float sum = 0;

		for (int i=0; i < pQ.length; i++) {
//			pQ[i] = K * pQ[i];
			sum += pQ[i];
		}
		for (int i=0; i < pQ.length; i++) {
			 pQ[i] /= sum;
		}
	}

	
	static HttpClient httpclient = new DefaultHttpClient();
	static HttpPost httppost = new HttpPost(urlsim);

	static protected float sim(String t1, String t2){
		try{
			List<NameValuePair> params = new ArrayList<NameValuePair>(2);
			params.add(new BasicNameValuePair("t1", t1));
			params.add(new BasicNameValuePair("t2", t2));
			httppost.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));

			//Execute and get the response.
			HttpResponse response = httpclient.execute(httppost);
			HttpEntity entity = response.getEntity();

			if (entity != null) {
			    InputStream instream = entity.getContent();
			    try {
//			        BufferedReader br = new BufferedReader(instream);
			        InputStreamReader ir = new InputStreamReader(instream);
			        char cbuf[] = new char[30];
			        int len = ir.read(cbuf);
//			        System.out.print(new String(cbuf, 0, len));
			        return Float.parseFloat(new String(cbuf, 0, len));
			    } finally {
			        instream.close();
			    }
			}
		}catch (Exception e) {
			e.printStackTrace();
		}
		return -10f;
	}
	// Request parameters and other properties.

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.lucene.postProcess.termselector.TermSelector#getInfo()
	 */
	@Override
	public String getInfo() {
		// TODO Auto-generated method stub
		return "SRM3";
	}

	@Override
	public void assignTermWeights(String[][] terms, int[][] freqs,
			TermPositionVector[] tfvs, QueryExpansionModel QEModel) {
		throw new UnsupportedOperationException();
	}
	
	public static void main(String[] args){
		float score = sim("good", "nice");
		System.out.print(score);
	}
}
