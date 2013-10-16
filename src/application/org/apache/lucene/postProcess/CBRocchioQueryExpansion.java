/**
 * 
 */
package org.apache.lucene.postProcess;

import gnu.trove.TObjectFloatHashMap;

import java.io.IOException;
import java.util.Arrays;

import org.apache.log4j.Logger;
import org.apache.lucene.index.TermPositionVector;
import org.apache.lucene.postProcess.termselector.TermSelector;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.RBooleanClause;
import org.apache.lucene.search.RBooleanQuery;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.search.TopDocCollector;
import org.dutir.lucene.util.ApplicationSetup;
import org.dutir.lucene.util.Distance;
import org.dutir.lucene.util.TermsCache;
import org.dutir.lucene.util.ExpansionTerms.ExpansionTerm;

/**
 * @author yezheng Context based Rocchio Query expansion (Pseudo)
 */

public class CBRocchioQueryExpansion extends QueryExpansion {
	/** Lists of feedback documents mapped to each topic */
	private float alpha;
	private float beta;
	Logger logger  = Logger.getLogger(this.getClass());
	static TermsCache tc = TermsCache.getInstance();
	int winSize = Integer.parseInt(ApplicationSetup.getProperty("CBQE.winSize",
	"500"));
	float lambda = Float.parseFloat(ApplicationSetup.getProperty("CBQE.lambda",	"0.5"));
	private String feedbackStrategy;
	
	public CBRocchioQueryExpansion() {
		this.alpha = Float.parseFloat(ApplicationSetup.getProperty(
				"rocchio.alpha", "1"));
		this.beta = Float.parseFloat(ApplicationSetup.getProperty(
				"rocchio.beta", "0.75"));
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
				"TRECQuerying.endFeedback", "1000"));
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
		return "CBRocchioQE_winSize=" + winSize + "_lamda=" + lambda +"_"+  + n_doc + "_" + n_term +feedbackStrategy;
	}
	
	/**
	 * Re-weigh original query terms and possibly expand them with the highest
	 * scored terms in the relevant set considered according to the Rocchio's
	 * algorithm.
	 * 
	 * @throws IOException
	 */
	public void expandQuery() throws IOException {
		logger.debug("alpha: " + alpha + ", beta: " + beta );
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
			positiveDocLength // +=
			// documentIndex.getDocumentLength(positiveDocids[i]);
			+= searcher.getFieldLength(field, positiveDocids[i]);
		}

		


	
//		ExpansionTerms positiveCandidateTerms = new ExpansionTerms(searcher,
//				positiveDocLength, field);

		GeoExpansionTerm posCBTerm = new GeoExpansionTerm(termSet
				.toArray(new String[0]));
		// get all terms in positive documents as candidate expansion terms
		// for each positive feedback document
		String sterms[][] = new String[positiveCount][];
		int termFreqs[][] = new int[positiveCount][];
		TermPositionVector tfvs[] = new TermPositionVector[positiveCount];
		for (int i = 0; i < positiveCount; i++) {
			TermPositionVector tfv = (TermPositionVector) this.reader
					.getTermFreqVector(positiveDocids[i], field);
			if (tfv == null)
				logger.warn("document " + this.ScoreDoc[i].doc + " not found");
			else {
				String strterms[] = sterms[i]= tfv.getTerms();
				int freqs[] = termFreqs[i]= tfv.getTermFrequencies();
				tfvs[i] = tfv;
//				for (int j = 0; j < strterms.length; j++)
//					positiveCandidateTerms.insertTerm(strterms[j],
//							(float) freqs[j]);
				posCBTerm.insert(strterms, freqs, tfv);
			}
		}

//		ExpansionTerm[] positiveQueryTerms = positiveCandidateTerms
//				.getExpandedTerms(positiveCandidateTerms
//						.getNumberOfUniqueTerms(), QEModel);
		TermSelector selector = TermSelector
				.getDefaultTermSelector(this.searcher);
		// selector.setResultSet(this.);
		selector.setOriginalQueryTerms(termSet);
		selector.setField(field);
		feedbackStrategy = selector.getInfo();
//		selector.assignTermWeights(positiveDocids, QEModel);
		selector.assignTermWeights(sterms, termFreqs, tfvs, QEModel);
		logger.debug("Number of unique terms in the feedback document set: "
				+ selector.getNumberOfUniqueTerms());
		ExpansionTerm[] positiveQueryTerms = selector
				.getMostWeightedTerms(selector.getNumberOfUniqueTerms());
			
		posCBTerm.compute();
		sort_norm(positiveQueryTerms);
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
				Query qry = tmpClause.getQuery();
				qry.setOccurNum(qry.getOccurNum() + this.beta
						* expandedTerm.getWeightExpansion());
			} else {
				Query qry = clause.getQuery();
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

	private void combine(ExpansionTerm[] queryTerms, GeoExpansionTerm posCBTerm) {
		for (int i = 0; i < queryTerms.length; i++) {
			float priorWeight = queryTerms[i].getWeightExpansion();
			float cbWeight = posCBTerm.getScore(queryTerms[i].getTerm());
//			queryTerms[i].setWeightExpansion(priorWeight * cbWeight);
//			queryTerms[i].setWeightExpansion((1-lambda) * priorWeight + lambda * (float) Math.sqrt(cbWeight));
			queryTerms[i].setWeightExpansion((float) Math.sqrt(cbWeight));
//			queryTerms[i].setWeightExpansion(cbWeight);
		}
	}

	private int[] getDocids() {
		int len = Math.min(ApplicationSetup.EXPANSION_DOCUMENTS, this.ScoreDoc.length);
		if(len < ApplicationSetup.EXPANSION_DOCUMENTS){
			logger.warn("num of documents retrieved is less that of FB needed");
		}
		int docids[] = new int[len];
		for (int i = 0; i < docids.length; i++) {
			docids[i] = this.ScoreDoc[i].doc;
		}
		return docids;
	}

	String docidField = null;


	class GeoExpansionTerm {
		String qterms[] = null;
		float freqs[] = null;
		int position[][] = null;
		int queryNum = 0;
		float idfs[] = null;
		float positiveLen;
		
		/**
		 * accumulated term frequency
		 */
		TObjectFloatHashMap<String> fscore = new TObjectFloatHashMap<String>();

		public GeoExpansionTerm(String queryterms[]) { 
			this.qterms = queryterms;
			this.queryNum = queryterms.length;
			Arrays.sort(qterms);
			freqs = new float[queryNum];
			idfs = new float[queryNum];
			for(int i=0; i < queryNum; i++){
				idfs[i] = idf(qterms[i]);
			}
		}

		public GeoExpansionTerm(String queryterms[], float positiveLen) {
			this(queryterms);
			this.positiveLen = positiveLen;
		}

		private void reset() {
			Arrays.fill(freqs, 0);
			position = new int[queryNum][];
			for (int i = 0; i < queryNum; i++) {
				position[i] = new int[0];
			}
		}

		public void insert(String terms[], int tfreqs[], TermPositionVector vec) {
			reset();
			int docLen = 0;
			for (int i = 0; i < terms.length; i++) { // extract all terms in
													// both the query
				docLen += tfreqs[i];
				if (termSet.contains(terms[i])) {
					int pos = Arrays.binarySearch(qterms, terms[i]);
					freqs[pos] = tfreqs[i];
					position[pos] = vec.getTermPositions(i);
					assert freqs[pos] == position[pos].length;
				}
			}

			for (int i = 0; i < terms.length; i++) {
				int[] termPos = vec.getTermPositions(i);
				assert tfreqs[i] == termPos.length;
				float count = idf(terms[i]) * count(termPos, position, winSize, docLen);
				fscore.adjustOrPutValue(terms[i], count, count);
			}
		}

		/**
		 * toDo: debug the starting position in TermPositionVector. 
		 * 
		 * @param start
		 * @return
		 */
		private float count(int[] tPos, int[][] queryPoss, int winSize, int docLen){
			float retValue = 0;
			for(int i=0; i < queryPoss.length; i++){
				int count = Distance.noTimes(tPos, queryPoss[i], winSize, docLen);
				retValue += idfs[i] * count;
			}
//			float selfCount = idf() * Distance.noTimes(tPos, tPos, winSize, docLen);
			return retValue;
		}
		
		private float idf(String string) {
			// return 1;
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
		
		public void compute() {
			String keys[] = fscore.keys(new String[0]);
			float values[] = fscore.getValues();
			float sum = org.dutir.util.Arrays.sum(values);
			float max = org.dutir.util.Arrays.findMax(values);
			fscore.clear();
			for (int i = 0; i < keys.length; i++) {
				fscore.put(keys[i], values[i]/max);
			}
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
