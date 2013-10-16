/**
 * 
 */
package org.apache.lucene.postProcess;

import gnu.trove.TObjectFloatHashMap;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;

import org.apache.log4j.Logger;
import org.apache.lucene.index.TermFreqVector;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.RBooleanClause;
import org.apache.lucene.search.RBooleanQuery;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.search.TopDocCollector;
import org.dutir.lucene.util.ApplicationSetup;
import org.dutir.lucene.util.TermsCache;
import org.dutir.lucene.util.ExpansionTerms.ExpansionTerm;

/**
 * @author yezheng
 * 
 */
public class LCAQueryExpansion extends QueryExpansion {

	Logger logger = Logger.getLogger(this.getClass());
	/** Lists of feedback documents mapped to each topic */
	private float beta;
	float delta = 0.01f;
	
	static TermsCache tc = TermsCache.getInstance();

	public LCAQueryExpansion() {
		this.beta = Float.parseFloat(ApplicationSetup.getProperty(
				"rocchio_beta", "0.75"));
	}

	public TopDocCollector postProcess(RBooleanQuery query,
			TopDocCollector topDoc, Searcher seacher) {
		setup(query, topDoc, seacher); // it is necessary
		logger.debug("beta=" + beta);
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

	public String getInfo() {
		int n_doc = ApplicationSetup.EXPANSION_DOCUMENTS;
		int n_term = ApplicationSetup.EXPANSION_TERMS;
		return "LCAQE_delta=" + delta + "_" + n_doc + "_" + n_term;
	}

	/**
	 * Re-weigh original query terms and possibly expand them with the highest
	 * scored terms in the relevant set considered according to the Rocchio's
	 * algorithm.
	 * 
	 * @throws IOException
	 */
	public void expandQuery() throws IOException {


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

		CBExpansionTerm posCBTerm = new CBExpansionTerm(termSet
				.toArray(new String[0]));
		// get all terms in positive documents as candidate expansion terms
		// for each positive feedback document
		for (int i = 0; i < positiveCount; i++) {
			TermFreqVector tfv = this.reader.getTermFreqVector(
					positiveDocids[i], field);
			if (tfv == null)
				logger.warn("document " + this.ScoreDoc[i].doc + " not found");
			else {
				String strterms[] = tfv.getTerms();
				int freqs[] = tfv.getTermFrequencies();
				for (int j = 0; j < strterms.length; j++)
					posCBTerm.insert(strterms, freqs);
			}
		}

		posCBTerm.compute();

		ExpansionTerm[] posTermArr = posCBTerm
				.getTop(ApplicationSetup.EXPANSION_TERMS);
		logger.debug(getExpansionInfo(posTermArr, 0,
				ApplicationSetup.EXPANSION_TERMS));
		
		int numberOfTermsToReweigh = Math.min(posTermArr.length,
				ApplicationSetup.EXPANSION_TERMS);

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
					/ (max));
//			logger.debug(queryTerms[i].getTerm() + "\t" + queryTerms[i].getWeightExpansion());
		    
		}
	}

	private void combine(ExpansionTerm[] queryTerms, CBExpansionTerm posCBTerm) {
		for (int i = 0; i < queryTerms.length; i++) {
			float cbWeight = posCBTerm.getScore(queryTerms[i].getTerm());
			queryTerms[i].setWeightExpansion(queryTerms[i].getWeightExpansion()
					* cbWeight);
			// queryTerms[i].setWeightExpansion(cbWeight);
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
		int queryNum = 0;
		TObjectFloatHashMap<String> fscore = new TObjectFloatHashMap<String>();
		HashMap<String, TObjectFloatHashMap<String>> termscore = new HashMap<String, TObjectFloatHashMap<String>>();

		CBExpansionTerm(String queryterms[]) {
			this.qterms = queryterms;
			this.queryNum = queryterms.length;
			Arrays.sort(qterms);
			freqs = new float[queryNum];
		}

		/**
		 * sort and norm, then return all
		 * @param expansion_terms
		 * @return
		 */
		public ExpansionTerm[] getTop(int expansion_terms) {
			int size = fscore.size();
			float v[] = fscore.getValues();
			String keys[] = (String[]) fscore.keys(new String[0]);
			ExpansionTerm[] eterms = new ExpansionTerm[size];
			for (int i = 0; i < size; i++) {
				eterms[i] = new ExpansionTerm(keys[i], 1);
				eterms[i].setWeightExpansion(v[i]);
			}
			sort_norm(eterms);
			return eterms;
		}

		public void insert(String terms[], int tfreqs[]) {
			Arrays.fill(freqs, 0);
			for (int i = 0; i < terms.length; i++) {// extract all terms in
														// both the query
				if (termSet.contains(terms[i])) {
					int pos = Arrays.binarySearch(qterms, terms[i]);
					freqs[pos] = tfreqs[i];
				}
			}

			for (int i = 0; i < terms.length; i++) {
				if (!termscore.containsKey(terms[i])) {
					termscore.put(terms[i], new TObjectFloatHashMap<String>());
				}
				TObjectFloatHashMap<String> tmap = termscore.get(terms[i]);
				for (int k = 0; k < queryNum; k++) {
					tmap.adjustOrPutValue(qterms[k], freqs[k] * tfreqs[i],
							freqs[k] * tfreqs[i]);
				}
			}

		}


		public void compute() {
			String keys[] = termscore.keySet().toArray(new String[0]);
			float values[] = new float[termscore.size()];
			for (int i = 0; i < keys.length; i++) {
				TObjectFloatHashMap<String> tmap = termscore.get(keys[i]);
				values[i] = score(keys[i], tmap);
			}

			for (int i = 0; i < keys.length; i++) {
				fscore.put(keys[i], values[i]);
			}
		}

		
		float n = ApplicationSetup.EXPANSION_DOCUMENTS;

		private float score(String string, TObjectFloatHashMap<String> tmap) {
			StringBuilder buf = new StringBuilder();
			buf.append(string+":");
			float retValue = 1;
			float idf = idf(string);
			
			idf =(float) Math.sqrt(Math.abs(idf));
			
			buf.append(",idf=" + idf);
			float values[] = tmap.getValues();
			String keys[] = tmap.keys(new String[0]);
			buf.append(",values=");
			for (int i = 0; i < values.length; i++) {
				
				float co_degree = (float) ( idf * Math.log10(1 + values[i]) / Math
						.log10(n));
				
				float kidf = idf(keys[i]);
				buf.append(values[i] + "|" + co_degree + "|" + kidf + ",  ");
				// method 1 and 2 should be used separately
				// method 1
				 retValue *= (delta + co_degree);
				// method 2
//				retValue = (float) (retValue * Math.pow(delta + co_degree, kidf));
			}
//			retValue = idf * retValue;
			buf.append(", rank=" + retValue);
			logger.debug("exp--" + buf.toString());
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

		public float getScore(String term) {
			float retValue = 0;
			retValue = this.fscore.get(term);
			return retValue;
		}

	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
