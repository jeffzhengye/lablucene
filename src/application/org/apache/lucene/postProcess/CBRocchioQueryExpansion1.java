/**
 * 
 */
package org.apache.lucene.postProcess;

import gnu.trove.TObjectFloatHashMap;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;

import org.apache.log4j.Logger;
import org.apache.lucene.index.TermPositionVector;
import org.apache.lucene.search.RBooleanClause;
import org.apache.lucene.search.RBooleanQuery;
import org.apache.lucene.search.RQuery;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.search.TopDocCollector;
import org.dutir.lucene.util.ApplicationSetup;
import org.dutir.lucene.util.ExpansionTerms;
import org.dutir.lucene.util.TermsCache;
import org.dutir.lucene.util.ExpansionTerms.ExpansionTerm;

/**
 * Context based Rocchio Query expansion (Pseudo)
 * @author yezheng 
 */

public class CBRocchioQueryExpansion1 extends QueryExpansion {
	/** Lists of feedback documents mapped to each topic */
	private float alpha;
	private float beta;
	Logger logger  = Logger.getLogger(this.getClass());
	
	static TermsCache tc = TermsCache.getInstance();
	float delta = Float.parseFloat(ApplicationSetup.getProperty("CBQE.delta", "0.001f"));
	int winSize = Integer.parseInt(ApplicationSetup.getProperty("CBQE.winSize", "500"));
	
	
	public CBRocchioQueryExpansion1() {
		this.alpha = Float.parseFloat(ApplicationSetup.getProperty(
				"rocchio_alpha", "1"));
		this.beta = Float.parseFloat(ApplicationSetup.getProperty(
				"rocchio_beta", "0.75"));
	}

	public TopDocCollector postProcess(RBooleanQuery query,
			TopDocCollector topDoc, Searcher seacher) {
		setup(query, topDoc, seacher); // it is necessary
		try {
			expandQuery();
		} catch (IOException e1) {
			e1.printStackTrace();
		}

		RBooleanQuery fquery = generateNewQuery(this.cluaseSet.values().toArray(
				new RBooleanClause[0]));
		int num = Integer.parseInt(ApplicationSetup.getProperty(
				"TRECQuerying.end", "1000"));
		TopDocCollector cls = new TopDocCollector(num);
		cls.setInfo(topDoc.getInfo());
		cls.setInfo_add(this.getInfo());
		cls.setInfo_add(QEModel.getInfo());
		try {
			this.searcher.search(fquery, cls);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return cls;
	}

	public String getInfo(){
		int n_doc =ApplicationSetup.EXPANSION_DOCUMENTS;
		int n_term = ApplicationSetup.EXPANSION_TERMS;
		return "CB1normalRocchioQE_delta=" + delta + "_" + "win="+ winSize + "_" + n_doc + "_" + n_term;
	}
	
	/**
	 * Re-weigh original query terms and possibly expand them with the highest
	 * scored terms in the relevant set considered according to the Rocchio's
	 * algorithm.
	 * 
	 * @throws IOException
	 */
	public void expandQuery() throws IOException {

		logger.info("alpha: " + alpha + ", beta: " + beta );

		// get docnos from the positive feedback documents for this query
		int[] positiveDocids = getDocids();

		// if there is no positive feedback for this query
		if (positiveDocids.length == 0) {
			logger.info("No relevant document found for feedback.");
			return;
		}

		int positiveCount = positiveDocids.length;

		// return in case there is no (pseudo-)relevance feedback evidence for
		// this query
		if (positiveCount == 0) {
			return;
		}

		// get total number of tokens in positive documents
		float positiveDocLength = 0;
		for (int i = 0; i < positiveCount; i++) {
			positiveDocLength += searcher.getFieldLength(field, positiveDocids[i]);
		}

		ExpansionTerms positiveCandidateTerms = new ExpansionTerms(searcher,
				positiveDocLength, field);

		CBExpansionTerm posCBTerm = new CBExpansionTerm(termSet
				.toArray(new String[0]), positiveDocLength);
		// get all terms in positive documents as candidate expansion terms
		// for each positive feedback document
		for (int i = 0; i < positiveCount; i++) {
			TermPositionVector tfv = (TermPositionVector) this.reader.getTermFreqVector(positiveDocids[i], field);
			
			if (tfv == null)
				logger.warn("document " + this.ScoreDoc[i].doc + " not found");
			else {
				String strterms[] = tfv.getTerms();
				int freqs[] = tfv.getTermFrequencies();
				
				for (int j = 0; j < strterms.length; j++)
					positiveCandidateTerms.insertTerm(strterms[j],
							(float) freqs[j]);
				
				posCBTerm.insert(strterms, freqs, tfv);
			}
		}

		ExpansionTerm[] positiveQueryTerms = positiveCandidateTerms
				.getExpandedTerms(positiveCandidateTerms
						.getNumberOfUniqueTerms(), QEModel);

		posCBTerm.compute();
		combine(positiveQueryTerms, posCBTerm);
		sort_norm(positiveQueryTerms);
         
		ExpansionTerm[] posTermArr = positiveQueryTerms;
		// Arrays.sort(posTermArr);
		int numberOfTermsToReweigh = Math.min(posTermArr.length,
				ApplicationSetup.EXPANSION_TERMS);
		logger.debug(getExpansionInfo(posTermArr, 0,
				ApplicationSetup.EXPANSION_TERMS));
		for (int i = 0; i < numberOfTermsToReweigh; i++) {

			ExpansionTerm expandedTerm = posTermArr[i];
			RBooleanClause clause = generateClause(expandedTerm);
			RBooleanClause tmpClause = this.cluaseSet.get(clause);
			if (tmpClause != null) {
				RQuery qry = tmpClause.getQuery();
				qry.setOccurNum(qry.getOccurNum() + this.beta
						* expandedTerm.getWeightExpansion());
			} else {
				RQuery qry = clause.getQuery();
				qry.setOccurNum(this.beta * expandedTerm.getWeightExpansion());
				this.cluaseSet.put(clause, clause);
			}
		}
	}

	private void sort_norm(ExpansionTerm[] queryTerms) {
		Arrays.sort(queryTerms);
		float max = queryTerms[0].getWeightExpansion();
		for (int i = 0; i < queryTerms.length; i++) {
			queryTerms[i].setWeightExpansion(queryTerms[i].getWeightExpansion()
					/ max);
		}
	}

	private void combine(ExpansionTerm[] queryTerms, CBExpansionTerm posCBTerm) {
		for (int i = 0; i < queryTerms.length; i++) {
			float cbWeight = posCBTerm.getScore(queryTerms[i].getTerm());
			queryTerms[i].setWeightExpansion(queryTerms[i].getWeightExpansion()
					* cbWeight);
//			 queryTerms[i].setWeightExpansion(cbWeight);
		}
	}

	private int[] getDocids() {
		int docids[] = new int[ApplicationSetup.EXPANSION_DOCUMENTS];
		for (int i = 0; i < docids.length; i++) {
			docids[i] = this.ScoreDoc[i].doc;
		}
		return docids;
	}

	String docidField = null;

	class CBExpansionTerm {
		String qterms[] = null;
		float freqs[] = null;
		int position[][] = null;
		int queryNum = 0;
		float positiveLen ;
		TObjectFloatHashMap<String> fscore = new TObjectFloatHashMap<String>();
		HashMap<String, TObjectFloatHashMap<String>> termscore = new HashMap<String, TObjectFloatHashMap<String>>();

		CBExpansionTerm(String queryterms[]) {
			this.qterms = queryterms;
			this.queryNum = queryterms.length;
			Arrays.sort(qterms);
			freqs = new float[queryNum];
			}
		
		CBExpansionTerm(String queryterms[], float positiveLen) {
			this(queryterms);
			this.positiveLen = positiveLen;
		}
		
		private void reset(){
			Arrays.fill(freqs, 0);
			position = new int[queryNum][];
			for(int i=0; i < queryNum; i++){
				position[i] = new int[0];
			}
		}
		
		public void insert(String terms[], int tfreqs[], TermPositionVector vec) {
			reset();
			for (int i = 0; i < terms.length; i++) { // extract all terms in both the query
				if (termSet.contains(terms[i])) {
					int pos = Arrays.binarySearch(qterms, terms[i]);
					freqs[pos] = tfreqs[i];
					position[pos] = vec.getTermPositions(i);
					assert freqs[pos] == position[pos].length;
				}
			}

			for (int i = 0; i < terms.length; i++) {
				if (!termscore.containsKey(terms[i])) {
					termscore.put(terms[i], new TObjectFloatHashMap<String>());
				}
				TObjectFloatHashMap<String> tmap = termscore.get(terms[i]);
				int[] termPos = vec.getTermPositions(i);
				assert tfreqs[i] == termPos.length;
				for (int k = 0; k < queryNum; k++) {
					assert freqs[k] == position[k].length;
					float cooccurr = getCooccurrence(termPos, position[k]);
					tmap.adjustOrPutValue(qterms[k], cooccurr,
							cooccurr);
//					assert cooccurr == (float)freqs[k] * tfreqs[i];
					
//					tmap.adjustOrPutValue(qterms[k], freqs[k] * tfreqs[i],
//							freqs[k] * tfreqs[i]);
				}
			}
		}

		private float getCooccurrence(int a[], final int b[]){
			float retValue =0;
			if(a ==null || b == null){
				return retValue;
			}
			
			for(int i=0; i < a.length; i++){
				for(int j=0; j< b.length; j++){
					if(Math.abs(a[i] - b[j]) <= winSize){
						retValue++;
					}
				}
			}
			return retValue/a.length;
		}
		
		//
		float ssigma = (float) Math.pow(winSize, 2); //sqare sigma
		private float getCooccurrence1(int a[], int b[]){
			float retValue =0;
			if(a ==null || b == null){
				return retValue;
			}
			for(int i=0; i < a.length; i++){
				for(int j=0; j< b.length; j++){
					float len = a[i] - b[j];
					retValue +=	Math.exp(- len*len/ssigma);

				}
			}
			return retValue/a.length;
		}

		public void compute() {
			String keys[] = termscore.keySet().toArray(new String[0]);
			float values[] = new float[termscore.size()];
			for (int i = 0; i < keys.length; i++) {
				TObjectFloatHashMap<String> tmap = termscore.get(keys[i]);
				values[i] = score1(keys[i], tmap);
//				System.out.println(values[i]);
			}
			for (int i = 0; i < keys.length; i++) {
				fscore.put(keys[i], values[i]);
			}
		}
		
		
		
		float n = ApplicationSetup.EXPANSION_DOCUMENTS;

		private float score(String string, TObjectFloatHashMap<String> tmap) {
			float retValue = 1;
			float idf = idf(string);
			idf =(float) Math.pow(idf>0?idf:0.00001, 1/2f);
			float values[] = tmap.getValues();
			String keys[] = tmap.keys(new String[0]);
			for (int i = 0; i < values.length; i++) {
				float co_degree = (float) (idf * Math.log10(values[i] +1) /Math.log10(n));
				retValue = (float) (retValue * Math.pow(delta + co_degree, idf(keys[i])));
			}
			return retValue;
		}
		
		private float score1(String string, TObjectFloatHashMap<String> tmap) {
			float retValue = 1;
			float values[] = tmap.getValues();
//			float idf = idf(string);
//			idf =(float) Math.pow(idf>0?idf:0.00001, 1/2f);
//			idf =1;
//			float sum = org.dutir.util.Arrays.sum(values);
//			retValue *= (float)(delta + (sum) /(n*queryNum*winSize));
			
			String keys[] = tmap.keys(new String[0]);
			for (int i = 0; i < values.length; i++) {
				float co_degree = (float) (Math.pow( (values[i] +0.5) /(n), 1f ));
//				float co_degree = (float)((values[i] +1) /(n*queryNum));
				retValue = (float) (retValue * Math.pow(delta + co_degree, idf(keys[i])));
			}
			return retValue ;
		}
		
		
		private float idf(String string) {
//			return 1;
			try {
				TermsCache.Item item = tc.getItem(string, field, searcher);
				// int df = searcher.docFreq(new Term(field, string));
				float df = item.df;
				int maxdoc = searcher.maxDoc();
				return (float) Math.min(1, Math.log10(maxdoc / df) / 5.0f);
			} catch (IOException e) {
				e.printStackTrace();
			}
			return 0;
		}

		public float getScore(String term) {
			float retValue = 0;
			retValue = this.fscore.get(term);
			return retValue;
		}
	}
	
	public static void main(String[] args) {
		TObjectFloatHashMap<String> termscore = new TObjectFloatHashMap<String>();

		termscore.adjustOrPutValue("hao", 1.6f, 1.7f);

		System.out.println(termscore.get("hao"));
	}

}
