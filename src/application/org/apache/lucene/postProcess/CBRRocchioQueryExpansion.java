///**
// * 
// */
//package org.apache.lucene.postProcess;
//
//import gnu.trove.THashMap;
//import gnu.trove.TObjectFloatHashMap;
//
//import java.io.BufferedReader;
//import java.io.IOException;
//import java.util.Arrays;
//import java.util.HashMap;
//import java.util.HashSet;
//
//import org.apache.log4j.Logger;
//import org.apache.lucene.index.Term;
//import org.apache.lucene.index.TermPositionVector;
//import org.apache.lucene.search.BooleanClause;
//import org.apache.lucene.search.Query;
//import org.apache.lucene.search.RBooleanClause;
//import org.apache.lucene.search.RBooleanQuery;
//import org.apache.lucene.search.RQuery;
//import org.apache.lucene.search.Searcher;
//import org.apache.lucene.search.TermQuery;
//import org.apache.lucene.search.TopDocCollector;
//import org.apache.lucene.search.TopDocs;
//import org.dutir.lucene.util.ApplicationSetup;
//import org.dutir.lucene.util.ExpansionTerms;
//import org.dutir.lucene.util.Files;
//import org.dutir.lucene.util.TermsCache;
//import org.dutir.lucene.util.ExpansionTerms.ExpansionTerm;
//
///**
// * @author yezheng Context-based Rocchio Query expansion
// */
//public class CBRRocchioQueryExpansion extends QueryExpansion {
//	static Logger logger = Logger.getLogger(CBRRocchioQueryExpansion.class);
//	/** Lists of feedback documents mapped to each topic */
//	private static THashMap<String, Feedback> feedbackMap;
//	private float alpha;
//	private float beta;
//	private float gamma;
//	private boolean EXPAND_NEG_TERMS = Boolean.parseBoolean(ApplicationSetup
//			.getProperty("Rocchio.negative.terms", "true"));
//	private boolean EXPAND_POS_TERMS = Boolean.parseBoolean(ApplicationSetup
//			.getProperty("Rocchio.positive.terms", "true"));
//	static TermsCache tc = TermsCache.getInstance();
//	float delta = Float.parseFloat(ApplicationSetup.getProperty("CBQE.delta",
//			"0.001f"));
//	int winSize = Integer.parseInt(ApplicationSetup.getProperty("CBQE.winSize",
//			"500"));
//
//	static {
//		loadFeedback(ApplicationSetup.getProperty("Rocchio.Feedback.filename",
//				ApplicationSetup.LUCENE_ETC + ApplicationSetup.FILE_SEPARATOR
//						+ "feedback"));
//	}
//
//	/**
//	 * Class for encapsulating feedback documents for a given topic.
//	 * 
//	 * @author rodrygo
//	 */
//	private static class Feedback {
//		/** list of positive feedback documents */
//		private HashSet<String> positiveDocs;
//		/** list of negative feedback documents */
//		private HashSet<String> negativeDocs;
//
//		public Feedback() {
//			positiveDocs = new HashSet<String>();
//			negativeDocs = new HashSet<String>();
//		}
//
//		public HashSet<String> getPositiveDocs() {
//			return positiveDocs;
//		}
//
//		public HashSet<String> getNegativeDocs() {
//			return negativeDocs;
//		}
//	}
//
//	public CBRRocchioQueryExpansion() {
//		this.alpha = Float.parseFloat(ApplicationSetup.getProperty(
//				"rocchio.alpha", "1"));
//		this.beta = Float.parseFloat(ApplicationSetup.getProperty(
//				"rocchio.beta", "0.4"));
//		this.gamma = Float.parseFloat(ApplicationSetup.getProperty(
//				"rocchio.gamma", "0.15"));
//	}
//
//	public TopDocCollector postProcess(RBooleanQuery query,
//			TopDocCollector topDoc, Searcher seacher) {
//		setup(query, topDoc, seacher); // it is necessary
//		try {
//			expandQuery();
//		} catch (IOException e1) {
//			e1.printStackTrace();
//		}
//
//		RBooleanQuery fquery = generateNewQuery(this.cluaseSet.values().toArray(
//				new RBooleanClause[0]));
//		logger.debug("Query after QE: "
//				+ this.getExpansionInfo(this.cluaseSet.values().toArray(
//						new RBooleanClause[0])));
//		int num = Integer.parseInt(ApplicationSetup.getProperty(
//				"TRECQuerying.endFeedback", "1000"));
//		TopDocCollector cls = new TopDocCollector(num);
//		cls.setInfo(topDoc.getInfo());
//		cls.setInfo_add(this.getInfo());
//		cls.setInfo_add(QEModel.getInfo());
//		try {
//			this.searcher.search(fquery, cls);
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
//		return cls;
//	}
//
//	private static void loadFeedback(String filename) {
//		logger.debug("Loading feedback information from: " + filename);
//		try {
//			feedbackMap = new THashMap<String, Feedback>();
//			if (trecR == null)
//				getTRECQerls();
//			BufferedReader br = Files.openFileReader(filename);
//			String line = null;
//			// for each line in the feedback (qrels) file
//			while ((line = br.readLine()) != null) {
//				line = line.trim();
//				if (line.length() == 0) {
//					continue;
//				}
//				// split line into space-separated pieces
//				String[] pieces = line.split("\\s+");
//
//				// grab topic id
//				String topId = pieces[0];
//				// grab docno
//				String docNo = pieces[2];
//				// grab relevance judgment of docno with respect to this topic
//				boolean relevant = trecR.isRelevant(topId, docNo);
//
//				// add topic entry to the feedback map
//				if (!feedbackMap.contains(topId)) {
//					feedbackMap.put(topId, new Feedback());
//				}
//
//				// add docno to the appropriate feedback list for this topic
//				if (relevant) {
//					feedbackMap.get(topId).getPositiveDocs().add(docNo);
//				} else {
//					feedbackMap.get(topId).getNegativeDocs().add(docNo);
//				}
//			}
//
//			br.close();
//
//		} catch (IOException e) {
//			e.printStackTrace();
//			System.exit(1);
//		}
//	}
//
//	/**
//	 * Re-weigh original query terms and possibly expand them with the highest
//	 * scored terms in the relevant set considered according to the Rocchio's
//	 * algorithm.
//	 * 
//	 * @throws IOException
//	 */
//	public void expandQuery() throws IOException {
//
//		if (QEModel.PARAMETER_FREE)
//			logger.debug("Parameter-free query expansion");
//		logger.debug("alpha: " + alpha + ", beta: " + beta + ", gamma: "
//				+ gamma);
//		int numberOfTermsToReweight = Math.max(
//				ApplicationSetup.EXPANSION_TERMS, bclause.length);
//
//		if (ApplicationSetup.EXPANSION_TERMS == 0)
//			numberOfTermsToReweight = 0;
//
//		// current topic id
//		String topId = this.topicId;
//		// get docnos from the positive feedback documents for this query
//		int[] positiveDocids = getDocids(feedbackMap.get(topId)
//				.getPositiveDocs().toArray(new String[0]));
//		// get docnos from the negative feedback documents for this query
//		int[] negativeDocids = getDocids(feedbackMap.get(topId)
//				.getNegativeDocs().toArray(new String[0]));
//
//		// if there is no positive feedback for this query
//		if (positiveDocids.length == 0) {
//			logger.info("No relevant document found for feedback.");
//			return;
//		}
//
//		int positiveCount = positiveDocids.length;
//		int negativeCount = negativeDocids.length;
//
//		logger.info("# POSITIVE DOCS: " + positiveCount);
//		logger.info("# NEGATIVE DOCS: " + negativeCount);
//
//		// return in case there is no (pseudo-)relevance feedback evidence for
//		// this query
//		if (positiveCount == 0 && negativeCount == 0) {
//			return;
//		}
//
//		// --------------------------------------------------------------------
//		// COMPUTATION OF POSITIVE WEIGHTS ------------------------------------
//		// --------------------------------------------------------------------
//
//		// get total number of tokens in positive documents
//		float positiveDocLength = 0;
//		for (int i = 0; i < positiveCount; i++) {
//			positiveDocLength // +=
//			// documentIndex.getDocumentLength(positiveDocids[i]);
//			+= searcher.getFieldLength(field, positiveDocids[i]);
//		}
//
//		ExpansionTerms positiveCandidateTerms = new ExpansionTerms(searcher,
//				positiveDocLength, field);
//
//		CBExpansionTerm posCBTerm = new CBExpansionTerm(termSet
//				.toArray(new String[0]));
//		// get all terms in positive documents as candidate expansion terms
//		// for each positive feedback document
//		for (int i = 0; i < positiveCount; i++) {
//			TermPositionVector tfv = (TermPositionVector) this.reader
//					.getTermFreqVector(positiveDocids[i], field);
//			if (tfv == null)
//				logger.warn("document " + this.ScoreDoc[i].doc + " not found");
//			else {
//				String strterms[] = tfv.getTerms();
//				int freqs[] = tfv.getTermFrequencies();
//				for (int j = 0; j < strterms.length; j++)
//					positiveCandidateTerms.insertTerm(strterms[j],
//							(float) freqs[j]);
//				posCBTerm.insert(strterms, freqs, tfv);
//			}
//		}
//
//		logger.info("# UNIQUE TERMS IN POSITIVE DOCS: "
//				+ positiveCandidateTerms.getNumberOfUniqueTerms());
//
//		ExpansionTerm[] positiveQueryTerms = positiveCandidateTerms
//				.getExpandedTerms(positiveCandidateTerms
//						.getNumberOfUniqueTerms(), QEModel);
//
//		posCBTerm.compute();
//		combine(positiveQueryTerms, posCBTerm);
//		sort_norm(positiveQueryTerms);
//		logger.debug(getExpansionInfo(positiveQueryTerms,
//				numberOfTermsToReweight));
//
//		// --------------------------------------------------------------------
//		// COMPUTATION OF NEGATIVE WEIGHTS ------------------------------------
//		// --------------------------------------------------------------------
//
//		// get total number of tokens in negative documents
//		float negativeDocLength = 0;
//		for (int i = 0; i < negativeCount; i++) {
//			negativeDocLength += searcher.getFieldLength(field,
//					negativeDocids[i]);
//		}
//
//		ExpansionTerms negativeCandidateTerms = new ExpansionTerms(searcher,
//				negativeDocLength, field);
//
//		CBExpansionTerm negCBTerm = new CBExpansionTerm(termSet
//				.toArray(new String[0]));
//		// get all terms in negative documents as candidate expansion terms
//		// for each negative feedback document
//		for (int i = 0; i < negativeCount; i++) {
//			TermPositionVector tfv = (TermPositionVector) this.reader
//					.getTermFreqVector(negativeDocids[i], field);
//			if (tfv == null)
//				logger.warn("document " + this.ScoreDoc[i].doc + " not found");
//			else {
//				String strterms[] = tfv.getTerms();
//				int freqs[] = tfv.getTermFrequencies();
//				for (int j = 0; j < strterms.length; j++)
//					negativeCandidateTerms.insertTerm(strterms[j],
//							(float) freqs[j]);
//
//				negCBTerm.insert(strterms, freqs, tfv);
//			}
//		}
//
//		System.out.println("# UNIQUE TERMS IN NEGATIVE DOCS: "
//				+ negativeCandidateTerms.getNumberOfUniqueTerms());
//
//		ExpansionTerm[] negativeQueryTerms = negativeCandidateTerms
//				.getExpandedTerms(negativeCandidateTerms
//						.getNumberOfUniqueTerms(), QEModel);
//
//		negCBTerm.compute();
//		combine(negativeQueryTerms, negCBTerm);
//		sort_norm(negativeQueryTerms);
//		logger.debug(getExpansionInfo(negativeQueryTerms,
//				numberOfTermsToReweight));
//
//		if (EXPAND_POS_TERMS) {
//			ExpansionTerm[] posTermArr = positiveQueryTerms;
//			// Arrays.sort(posTermArr);
//			int numberOfTermsToReweigh = Math.min(posTermArr.length,
//					ApplicationSetup.EXPANSION_TERMS);
//			for (int i = 0; i < numberOfTermsToReweigh; i++) {
//				ExpansionTerm expandedTerm = posTermArr[i];
//				if (expandedTerm.getWeightExpansion() <= 0) {
//					break;
//				}
//				// queryTerms.get(posTermArr[i].getTerm());
//				RBooleanClause clause = generateClause(expandedTerm);
//				RBooleanClause tmpClause = this.cluaseSet.get(clause);
//				if (tmpClause != null) {
//					RQuery qry = tmpClause.getQuery();
//					qry.setOccurNum(qry.getOccurNum() + this.beta
//							* expandedTerm.getWeightExpansion());
//				} else {
//					RQuery qry = clause.getQuery();
//					qry.setOccurNum(this.beta
//							* expandedTerm.getWeightExpansion());
//					this.cluaseSet.put(clause, clause);
//				}
//			}
//		}
//
//		if (EXPAND_NEG_TERMS) {
//			ExpansionTerm[] negTermArr = negativeQueryTerms;
//			Arrays.sort(negTermArr);
//			int numberOfTermsToReweigh = Math.min(negTermArr.length,
//					ApplicationSetup.EXPANSION_TERMS / 2);
//			int modified = 0, added = 0;
//			for (int i = 0; i < numberOfTermsToReweigh; i++) {
//				ExpansionTerm expandedTerm = negTermArr[i];
//				// queryTerms.get(negTermArr[i].getTerm());
//				if (expandedTerm.getWeightExpansion() <= 0) {
//					break;
//				}
//				RBooleanClause clause = generateClause(expandedTerm);
//				RBooleanClause tmpClause = this.cluaseSet.get(clause);
//				if (tmpClause != null) {
//					RQuery qry = tmpClause.getQuery();
//					qry.setOccurNum(qry.getOccurNum() - this.gamma
//							* expandedTerm.getWeightExpansion());
//					modified++;
//				} else {
//					added++;
//					RQuery qry = clause.getQuery();
//					qry.setOccurNum(-this.gamma
//							* expandedTerm.getWeightExpansion());
//					this.cluaseSet.put(clause, clause);
//				}
//			}
//			logger.info("negative terms added:" + added + ", modified: "
//					+ modified);
//		}
//
//	}
//
//	private void sort_norm(ExpansionTerm[] queryTerms) {
//		if (queryTerms.length < 1) {
//			return;
//		}
//		Arrays.sort(queryTerms);
//		float max = queryTerms[0].getWeightExpansion();
//		if (max == 0) {
//			logger.warn("sort_norm: encounter max =0 for topic" + this.topicId);
//			return;
//		}
//		for (int i = 0; i < queryTerms.length; i++) {
//			queryTerms[i].setWeightExpansion(queryTerms[i].getWeightExpansion()
//					/ max);
//		}
//	}
//
//	private void combine(ExpansionTerm[] queryTerms, CBExpansionTerm posCBTerm) {
//		for (int i = 0; i < queryTerms.length; i++) {
//			float cbWeight = posCBTerm.getScore(queryTerms[i].getTerm());
//			queryTerms[i].setWeightExpansion(queryTerms[i].getWeightExpansion()
//					* cbWeight);
//			// queryTerms[i].setWeightExpansion(cbWeight);
//		}
//	}
//
//	private int[] getDocids(Object[] objects) {
//		int docids[] = new int[objects.length];
//		for (int i = 0; i < objects.length; i++) {
//			try {
//				TopDocs topdocs = this.searcher.search(new TermQuery(new Term(
//						getIdFieldName(), (String) objects[i])), 1);
//				if (topdocs.totalHits < 1) {
//					logger.warn("doc " + objects[i] + " do not exist.");
//				} else {
//					docids[i] = topdocs.scoreDocs[0].doc;
//				}
//			} catch (IOException e) {
//				e.printStackTrace();
//			}
//		}
//		return docids;
//	}
//
//	String docidField = null;
//
//	class CBExpansionTerm {
//		String qterms[] = null;
//		float freqs[] = null;
//		int position[][] = null;
//		int queryNum = 0;
//		float positiveLen;
//		TObjectFloatHashMap<String> fscore = new TObjectFloatHashMap<String>();
//		HashMap<String, TObjectFloatHashMap<String>> termscore = new HashMap<String, TObjectFloatHashMap<String>>();
//
//		CBExpansionTerm(String queryterms[]) {
//			this.qterms = queryterms;
//			this.queryNum = queryterms.length;
//			Arrays.sort(qterms);
//			freqs = new float[queryNum];
//		}
//
//		CBExpansionTerm(String queryterms[], float positiveLen) {
//			this(queryterms);
//			this.positiveLen = positiveLen;
//		}
//
//		private void reset() {
//			Arrays.fill(freqs, 0);
//			position = new int[queryNum][];
//			for (int i = 0; i < queryNum; i++) {
//				position[i] = new int[0];
//			}
//		}
//
//		public void insert(String terms[], int tfreqs[], TermPositionVector vec) {
//			reset();
//			for (int i = 0; i < terms.length; i++) { // extract all terms in
//														// both the query
//				if (termSet.contains(terms[i])) {
//					int pos = Arrays.binarySearch(qterms, terms[i]);
//					freqs[pos] = tfreqs[i];
//					position[pos] = vec.getTermPositions(i);
//					assert freqs[pos] == position[pos].length;
//				}
//			}
//
//			for (int i = 0; i < terms.length; i++) {
//				if (!termscore.containsKey(terms[i])) {
//					termscore.put(terms[i], new TObjectFloatHashMap<String>());
//				}
//				TObjectFloatHashMap<String> tmap = termscore.get(terms[i]);
//				int[] termPos = vec.getTermPositions(i);
//				assert tfreqs[i] == termPos.length;
//				for (int k = 0; k < queryNum; k++) {
//					assert freqs[k] == position[k].length;
//					float cooccurr = getCooccurrence(termPos, position[k]);
//					tmap.adjustOrPutValue(qterms[k], cooccurr, cooccurr);
//					// assert cooccurr == (float)freqs[k] * tfreqs[i];
//
//					// tmap.adjustOrPutValue(qterms[k], freqs[k] * tfreqs[i],
//					// freqs[k] * tfreqs[i]);
//				}
//			}
//		}
//
//		private float getCooccurrence(int a[], final int b[]) {
//			float retValue = 0;
//			if (a == null || b == null) {
//				return retValue;
//			}
//
//			for (int i = 0; i < a.length; i++) {
//				for (int j = 0; j < b.length; j++) {
//					if (Math.abs(a[i] - b[j]) <= winSize) {
//						retValue++;
//					}
//				}
//			}
//			return retValue / a.length;
//		}
//
//		//
//		float ssigma = (float) Math.pow(winSize, 2); // sqare sigma
//
//		private float getCooccurrence1(int a[], int b[]) {
//			float retValue = 0;
//			if (a == null || b == null) {
//				return retValue;
//			}
//			for (int i = 0; i < a.length; i++) {
//				for (int j = 0; j < b.length; j++) {
//					float len = a[i] - b[j];
//					retValue += Math.exp(-len * len / ssigma);
//
//				}
//			}
//			return retValue / a.length;
//		}
//
//		public void compute() {
//			String keys[] = termscore.keySet().toArray(new String[0]);
//			float values[] = new float[termscore.size()];
//			for (int i = 0; i < keys.length; i++) {
//				TObjectFloatHashMap<String> tmap = termscore.get(keys[i]);
//				values[i] = score1(keys[i], tmap);
//				// System.out.println(values[i]);
//			}
//			for (int i = 0; i < keys.length; i++) {
//				fscore.put(keys[i], values[i]);
//			}
//		}
//
//		float n = ApplicationSetup.EXPANSION_DOCUMENTS;
//
//		private float score(String string, TObjectFloatHashMap<String> tmap) {
//			float retValue = 1;
//			float idf = idf(string);
//			idf = (float) Math.pow(idf > 0 ? idf : 0.00001, 1 / 2f);
//			float values[] = tmap.getValues();
//			String keys[] = tmap.keys(new String[0]);
//			for (int i = 0; i < values.length; i++) {
//				float co_degree = (float) (idf * Math.log10(values[i] + 1) / Math
//						.log10(n));
//				retValue = (float) (retValue * Math.pow(delta + co_degree,
//						idf(keys[i])));
//			}
//			return retValue;
//		}
//
//		private float score1(String string, TObjectFloatHashMap<String> tmap) {
//			float retValue = 1;
//			float values[] = tmap.getValues();
//			// float idf = idf(string);
//			// idf =(float) Math.pow(idf>0?idf:0.00001, 1/2f);
//			// idf =1;
//			// float sum = org.dutir.util.Arrays.sum(values);
//			// retValue *= (float)(delta + (sum) /(n*queryNum*winSize));
//
//			String keys[] = tmap.keys(new String[0]);
//			for (int i = 0; i < values.length; i++) {
//				float co_degree = (float) (Math
//						.pow((values[i] + 0.5) / (n), 1f));
//				// float co_degree = (float)((values[i] +1) /(n*queryNum));
//				retValue = (float) (retValue * Math.pow(delta + co_degree,
//						idf(keys[i])));
//			}
//			return retValue;
//		}
//
//		private float idf(String string) {
//			// return 1;
//			try {
//				TermsCache.Item item = tc.getItem(string, field, searcher);
//				// int df = searcher.docFreq(new Term(field, string));
//				float df = item.df;
//				int maxdoc = searcher.maxDoc();
//				return (float) Math.min(1, Math.log10(maxdoc / df) / 5.0f);
//			} catch (IOException e) {
//				e.printStackTrace();
//			}
//			return 0;
//		}
//
//		public float getScore(String term) {
//			float retValue = 0;
//			retValue = this.fscore.get(term);
//			return retValue;
//		}
//	}
//
//	public static void main(String[] args) {
//		TObjectFloatHashMap<String> termscore = new TObjectFloatHashMap<String>();
//
//		// termscore.put("hao", 15f);
//		termscore.adjustOrPutValue("hao", 1.6f, 1.7f);
//
//		System.out.println(termscore.get("hao"));
//	}
//
//}
