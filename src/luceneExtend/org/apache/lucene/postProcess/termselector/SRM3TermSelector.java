/**
 * 
 */
package org.apache.lucene.postProcess.termselector;

import gnu.trove.TObjectFloatHashMap;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;

import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.log4j.Logger;
import org.apache.lucene.index.TermFreqVector;
import org.apache.lucene.index.TermPositionVector;
import org.apache.lucene.postProcess.QueryExpansionModel;
import org.apache.lucene.search.model.Idf;
import org.dutir.lucene.util.ApplicationSetup;
import org.dutir.lucene.util.ExpansionTerms.ExpansionTerm;
import org.dutir.lucene.util.Rounding;
import org.dutir.lucene.util.TermsCache.Item;
import org.dutir.util.Arrays;

import redis.clients.jedis.Jedis;

/**
 * @author zheng
 * 
 */
public class SRM3TermSelector extends TermSelector {

	private static Logger logger = Logger.getLogger(SRM3TermSelector.class);
	private float alpha = Float.parseFloat(ApplicationSetup.getProperty(
			"srm3.alpha", "0.5"));
	private static String urlsim = ApplicationSetup.getProperty("srm3.urlsim",
			"http://10.7.10.219:5000/sim");
	static Jedis jedis = new Jedis("127.0.0.1", 6379, 100000); // zheng's

	class Structure {
		/**
		 * document level LM score (dir smoothing) for each candidate word in
		 * docs.
		 */
		float wordDoc[];
		float ctf = 0;
		float cf = 0;
		float collectWeight = -1;
		int df = 0;

		public Structure(int len) {
			wordDoc = new float[len];
		}

		public String toString() {
			StringBuilder buf = new StringBuilder();
			buf.append("df = " + df + ", ");
			for (int i = 0; i < wordDoc.length; i++) {
				buf.append("" + Rounding.round(wordDoc[i], 5) + ", ");
			}
			return buf.toString();
		}
	}

	float scores[] = null;

	public void setScores(float _scores[]) {
		scores = new float[_scores.length];
		float max = _scores[0];
		for (int i = 0; i < _scores.length; i++) {
			// scores[i] = (float) Math.exp(_scores[i] +_scores[0]);
			scores[i] = Idf.exp(max + _scores[i]);
		}
	}

	static String dmu = ApplicationSetup.getProperty("dlm.mu", "500");
	static float mu = Integer.parseInt(ApplicationSetup.getProperty("rm.mu",
			dmu));

	// float numOfTokens = this.searcher.getNumTokens(field);
	private float score(float tf, float docLength, float termFrequency,
			float numberOfTokens) {
		float pc = termFrequency / numberOfTokens;
		return (tf + mu * pc) / (docLength + mu);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.apache.lucene.postProcess.termselector.TermSelector#assignTermWeights
	 * (int[], org.apache.lucene.postProcess.QueryExpansionModel)
	 */
	@Override
	public void assignTermWeights(int[] docids, float scores[],
			QueryExpansionModel QEModel) {

		float numOfTokens = this.searcher.getNumTokens(field);
		feedbackSetLength = 0;
		termMap = new HashMap<String, ExpansionTerm>();
		float PD[] = new float[docids.length];
		float docLens[] = new float[docids.length];

		/*
		 * map-- key: all terms value: stru ([lm scores in each document], ctf)
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

		HashSet<String> tmpSet = new HashSet<String>(); // all terms in docs +
														// terms in Query
		tmpSet.addAll(originalQueryTermidSet);
		tmpSet.addAll(map.keySet());
		/**
		 * if the lm score is 0, it indicates not smooth. So smooth as follows:
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
			for (int k = 0; k < docLens.length; k++) {
				if (stru.wordDoc[k] == 0) {
					stru.wordDoc[k] = score(0, docLens[k], stru.ctf,
							numOfTokens); // let tf=0 to smooth, not log
				}
			}
			if (this.originalQueryTermidSet.contains(term)) {
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
			// PQ[i] = scores[i];
		}
		indriNorm(PQ);

		// *****************************compute semantic scores***************************************
		String qstr = "";
		for (String term : originalQueryTermidSet) {
			if (qstr.equals("")) {
				qstr += term;
			} else {
				qstr += ":" + term;
			}
		}
		TObjectFloatHashMap<String> sem_map = new TObjectFloatHashMap<String>();
		for (String term : tmpSet) {
			float s = sim(qstr, term);
			sem_map.put(term, s); // it's original score, not normalised
		}
		max_min_norm(sem_map);
		// ******************************************************************************************

		int termNum = map.size();
		ExpansionTerm[] exTerms = new ExpansionTerm[termNum];
		if (logger.isDebugEnabled())
			logger.debug("the total number of terms in feedback docs: "
					+ termNum);

		// **************RM1 -- Indri implementation********************** //
		float total = 0;
		pos = 0;
		float sum = 0, sum1 = 0;
		for (Entry<String, Structure> entry : map.entrySet()) {
			String w = entry.getKey();
			Structure ws = entry.getValue();
			float weight = 0, weight1 = 0;

			if (ws.df < EXPANSION_MIN_DOCUMENTS) { // || ws.ctf < 5
				weight = 0;
				weight1 = 0;
			} else {
				// float sem_score = sim(qstr, w);
//				float sem_score = sem_map.get(w);
				for (int i = 0; i < ws.wordDoc.length; i++) {
					// weight += PD[i] * ws.wordDoc[i] * (alpha * PQ[i] +
					// (1-alpha)*sem_map.get(w)/t_semscore);
					// weight += PD[i] * ws.wordDoc[i] * (alpha * PQ[i] +
					// (1-alpha)*sim(qstr, w));
					// weight += PD[i] * ws.wordDoc[i] * sim(qstr, w);
					// weight += PD[i] * ws.wordDoc[i];
					// weight += PD[i] * sem_score * PQ[i];
//					weight1 += PD[i] * sem_score * PQ[i];

					weight += PD[i] * ws.wordDoc[i] * PQ[i]; // original RM3
				}
			}

			exTerms[pos] = new ExpansionTerm(w, 0);
			exTerms[pos].setWeightExpansion(weight);
			pos++;
			sum += weight;

//			sem_map.put(w, weight1);
//			sum1 += weight1;
		}

		termNum = pos;

		java.util.Arrays.sort(exTerms);
		
		///////////////////////////////////////////////////////////////////////////
		int topNterm = 30;
		float doc_term_weights[][] = new float[docids.length][topNterm]; //P（w|D）
		float sem_sims[] = new float[docids.length];
		ArrayList<String> topNtermList = new ArrayList<String>();
		for(int i = 0; i < topNterm && i < exTerms.length; i++) {
			String term = exTerms[i].getTerm();
			topNtermList.add(term);
			Structure stru = map.get(term);
			for(int j = 0; j < docids.length; j++){
				doc_term_weights[j][i] = stru.wordDoc[j];
			}
		}
		
		float sem_sum = 0;
		for (int i = 0; i < docids.length; i++) {
			for (int j = 0; j < topNterm; j++) {
				// weighted similarity between all query terms and topNterm for each doc
				sem_sims[i] += sem_map.get(topNtermList.get(j)) * doc_term_weights[i][j];
			}
			sem_sum += sem_sims[i];
		}
//		indriNorm(sem_sims);
//		Normalizer.norm_MaxMin_0_1(sem_sims);
//		Normalizer.norm2(sem_sims);
		sum1 = 0;
		for (Entry<String, Structure> entry : map.entrySet()) {
			String w = entry.getKey();
			Structure ws = entry.getValue();
			float weight = 0, weight1 = 0;

			if (ws.df < EXPANSION_MIN_DOCUMENTS) { // || ws.ctf < 5
				weight = 0;
				weight1 = 0;
			} else {
				for (int i = 0; i < ws.wordDoc.length; i++) {

					weight1 += PD[i] * ws.wordDoc[i] * sem_sims[i]; 
				}
			}

//			exTerms[pos] = new ExpansionTerm(w, 0);
//			exTerms[pos].setWeightExpansion(weight);
//			pos++;
//			sum += weight;

			sem_map.put(w, weight1);
			sum1 += weight1;
		}
		
		for (int i = 0; i < docids.length; i++) {
			logger.info("doc_weight -- " + PQ[i] + ":" + sem_sims[i] + ":" + PQ[i]/sem_sims[i]); 
		}
		///////////////////////////////////////////////////////////////////////////
		
		if (logger.isDebugEnabled()) {
			StringBuilder buf = new StringBuilder();
			int tmpPos = 0;
			for (int i = 0; tmpPos < 40 && i < exTerms.length; i++) {
				if (true || exTerms[i].getWeightExpansion() < 1) {
					tmpPos++;
					buf.append(exTerms[i] + "\t");
				}
			}
			if (logger.isDebugEnabled())
				logger.debug("original: " + buf.toString());
		}

		if (logger.isDebugEnabled())
			logger.debug("the total weight: " + total);
		if (logger.isDebugEnabled())
			logger.debug("maxWeight=" + exTerms[0].getWeightExpansion()
					+ ", minWeight="
					+ exTerms[exTerms.length - 1].getWeightExpansion());

		StringBuilder buf = new StringBuilder();
		
		float d_sum = 0, d_sum1 =0;
		for (pos = 0; pos < termNum; pos++) {
			if (logger.isDebugEnabled()
					&& this.originalQueryTermidSet.contains(exTerms[pos]
							.getTerm())) {
				buf.append(exTerms[pos] + "\t");
			}
			// exTerms[pos].setWeightExpansion(exTerms[pos].getWeightExpansion()
			// / sum);
			String w = exTerms[pos].getTerm();
			float w_rm3 = exTerms[pos].getWeightExpansion() / sum;
			float w_srm3 = sem_map.get(w) / sum1;
			
			exTerms[pos].setWeightExpansion(alpha * w_rm3 + (1 - alpha)	* w_srm3);
//			logger.info(w + ":" + w_rm3 + ":" + w_srm3 + ":" + w_rm3/w_srm3);
//			exTerms[pos].setWeightExpansion(sem_map.get(w) / sum1);
			this.termMap.put(w, exTerms[pos]);
			
			d_sum += w_rm3;
			d_sum1 += w_srm3;
		}
		
//		logger.info("sum =" + d_sum + ":" + d_sum1);
		if (logger.isDebugEnabled()) {
			logger.debug("original Query Weight: " + buf.toString());
		}
	}

	// In: log(x1) log(x2) ... log(xN)
	// Out: x1/sum, x2/sum, ... xN/sum
	//
	// Extra care is taken to make sure we don't overflow
	// machine precision when taking exp (log x)
	// This is done by adding a constant K which cancels out
	// Right now K is set to maximally preserve the highest value
	// but could be altered to a min or average, or whatever...
	private void indriNorm(float[] pQ) {

		// float K = pQ[0]; // first is max
		float sum = 0;

		for (int i = 0; i < pQ.length; i++) {
			// pQ[i] = K * pQ[i];
			sum += pQ[i];
		}
		for (int i = 0; i < pQ.length; i++) {
			pQ[i] /= sum;
		}
	}
	
	private void indriNorm(TObjectFloatHashMap<String> map) {

		// float K = pQ[0]; // first is max
		float values[] = map.getValues();
		float sum = Arrays.sum(values);

		for (Object key: map.keys()) {
			float value = map.get((String) key);
			map.put((String) key, value/sum);
		}
	}

	private void max_min_norm(TObjectFloatHashMap<String> map) {
		float values[] = map.getValues();
		float max = values[0];
		float min = values[0];
		for (int i = 0; i < values.length; i++) {
			if (values[i] < min) {
				min = values[i];
			} else if (values[i] > max) {
				max = values[i];
			}
		}

		float gap = max - min;
		// System.out.print(max + ":" + min + ":" + gap);
		Object[] keys = map.keys();
		for (int i = 0; i < keys.length; i++) {
			String key = (String) keys[i];
			float value = map.get(key);
			map.put(key, (value - min) / gap);
		}
	}

	static PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
	static CloseableHttpClient httpclient;
	static {
		cm.setMaxTotal(100);
		httpclient = HttpClients.custom().setConnectionManager(cm).build();
	}

	static private String torediskey(String t1, String t2) {
		return ApplicationSetup.LUCENE_ETC + "|" + t1 + "|" + t2;
	}

	static protected float sim(String t1, String t2) {
		try {
			String key = torediskey(t1, t2);
			String value = jedis.get(key);
			if (value != null)
				return Float.parseFloat(value);

			List<NameValuePair> params = new ArrayList<NameValuePair>(2);
			params.add(new BasicNameValuePair("t1", t1));
			params.add(new BasicNameValuePair("t2", t2));
			HttpPost httppost = new HttpPost(urlsim);
			httppost.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));
			HttpContext context = new BasicHttpContext();

			// HttpGet httpget = new HttpGet(urisToGet[i]);
			// Execute and get the response.
			CloseableHttpResponse response = httpclient.execute(httppost,
					context);
			HttpEntity entity = response.getEntity();

			if (entity != null) {
				InputStream instream = entity.getContent();
				try {
					// BufferedReader br = new BufferedReader(instream);
					InputStreamReader ir = new InputStreamReader(instream);
					char cbuf[] = new char[30];
					int len = ir.read(cbuf);
					// System.out.print(new String(cbuf, 0, len));
					String str_ret = new String(cbuf, 0, len);
					jedis.set(key, str_ret);
					return Float.parseFloat(str_ret);
				} finally {
					instream.close();
					response.close();
				}
			}

		} catch (Exception e) {
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
		return "SRM3alpha=" + alpha;
		// return "original_by_alpha=1RM3alpha=" + alpha;
	}

	@Override
	public void assignTermWeights(String[][] terms, int[][] freqs,
			TermPositionVector[] tfvs, QueryExpansionModel QEModel) {
		throw new UnsupportedOperationException();
	}

	public static void main(String[] args) {
		// float score = sim("good", "nice");
		// System.out.print(score);

//		float values[] = { -1, -0.25f, 1.2f, 3.2f };
//		float max = values[0];
//		float min = values[0];
//		for (int i = 0; i < values.length; i++) {
//			if (values[i] < min) {
//				min = values[i];
//			} else if (values[i] > max) {
//				max = values[i];
//			}
//		}
//		float gap = max - min;
//		System.out.print(max + ":" + min + ":" + gap);
//		float ft[] = new float[10];
//		for(int i=0; i < ft.length; i++){
//			System.out.println(ft[i]);
//		}
		TObjectFloatHashMap<String> sem_map = new TObjectFloatHashMap<String>();
		sem_map.put("key", 1.0f);
		
		sem_map.put("key", 2.0f);
		for(Object key: sem_map.keys()){
			System.out.println(sem_map.get((String) key));
		}
	}
}
