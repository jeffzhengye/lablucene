/*///**
// * 
// 
//package org.apache.lucene.postProcess;
//
//import gnu.trove.THashMap;
//
//import java.io.BufferedReader;
//import java.io.IOException;
//import java.util.Arrays;
//import java.util.HashMap;
//import java.util.HashSet;
//
//import org.apache.lucene.index.Term;
//import org.apache.lucene.index.TermFreqVector;
//import org.apache.lucene.search.BooleanClause;
//import org.apache.lucene.search.BooleanQuery;
//import org.apache.lucene.search.Query;
//import org.apache.lucene.search.Searcher;
//import org.apache.lucene.search.TermQuery;
//import org.apache.lucene.search.TopDocCollector;
//import org.apache.lucene.search.TopDocs;
//import org.dutir.lucene.evaluation.TRECQrelsInMemory;
//import org.dutir.lucene.util.ApplicationSetup;
//import org.dutir.lucene.util.ExpansionTerms;
//import org.dutir.lucene.util.Files;
//import org.dutir.lucene.util.ExpansionTerms.ExpansionTerm;
//
///**
// * @author yezheng This class is used for relevance feedback. only difference
// *         form QueryExpansion.java.
// */
//public class RocchioQueryExpansion extends QueryExpansion {
//	/** Lists of feedback documents mapped to each topic */
//	private THashMap<String, Feedback> feedbackMap;
//	private float beta;
//	private float gamma;
//	private boolean EXPAND_NEG_TERMS = Boolean.parseBoolean(ApplicationSetup
//			.getProperty("Rocchio.negative.terms", "false"));
//	private boolean EXPAND_POS_TERMS = Boolean.parseBoolean(ApplicationSetup
//			.getProperty("Rocchio.positive.terms", "true"));
//
//	/**
//	 * Class for encapsulating feedback documents for a given topic.
//	 * 
//	 * @author rodrygo
//	 */
//	protected class Feedback {
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
//	public RocchioQueryExpansion() {
//		this.beta = Float.parseFloat(ApplicationSetup.getProperty(
//				"rocchio_beta", "0.75"));
//		this.gamma = Float.parseFloat(ApplicationSetup.getProperty(
//				"rocchio_gamma", "0.15"));
//		loadFeedback(ApplicationSetup.getProperty("Rocchio.Feedback.filename",
//				ApplicationSetup.LUCENE_ETC + ApplicationSetup.FILE_SEPARATOR
//						+ "feedback"));
//	}
//
//	public TopDocCollector postProcess(BooleanQuery query,
//			TopDocCollector topDoc, Searcher seacher) {
//		setup(query, topDoc, seacher); // it is necessary
//		try {
//			expandQuery();
//		} catch (IOException e1) {
//			e1.printStackTrace();
//		}
//
//		BooleanQuery fquery = generateNewQuery(this.cluaseSet.values().toArray(
//				new BooleanClause[0]));
//		String log = fquery.toString();
//		logger.info("Query after QE: " + log);
//		int num = 2500;
//		TopDocCollector cls = new TopDocCollector(num);
//		try {
//			this.searcher.search(fquery, cls);
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
//		return cls;
//	}
//
//	protected void loadFeedback(String filename) {
//		logger.debug("Loading feedback information from " + filename + "...");
//		try {
//			feedbackMap = new THashMap<String, Feedback>();
//			TRECQrelsInMemory qrels = getTRECQerls();
//
//			BufferedReader br = Files.openFileReader(filename);
//			String line = null;
//			// for each line in the feedback (qrels) file
//			while ((line = br.readLine()) != null) {
//				line = line.trim();
//				if (line.length() == 0) {
//					continue;
//				}
//
//				// split line into space-separated pieces
//				String[] pieces = line.split("\\s+");
//
//				// grab topic id
//				String topId = pieces[0];
//				// grab docno
//				String docNo = pieces[2];
//				// grab relevance judgment of docno with respect to this topic
//				boolean relevant = // Integer.parseInt(pieces[3]) > 0;
//				qrels.isRelevant(topId, docNo);
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
//			br.close();
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
//		logger.debug("beta: " + beta + ", gamma: " + gamma);
//		int numberOfTermsToReweight = Math.max(
//				ApplicationSetup.EXPANSION_TERMS, bclause.length);
//
//		if (ApplicationSetup.EXPANSION_TERMS == 0)
//			numberOfTermsToReweight = 0;
//		// the number of term to re-weight (i.e. to do relevance feedback) is
//		// the maximum between the system setting and the actual query length.
//		// if the query length is larger than the system setting, it does not
//		// make sense to do relevance feedback for a portion of the query.
//		// Therefore,
//		// we re-weight the number of query length of terms.
//		/**
//		 * 2008/07/29, Ben wrote: this criteria has been revoked. Query terms
//		 * can be reweighed no matter how long the query is.
//		 */
//		// int numberOfTermsToReweight =
//		// Math.max(ApplicationSetup.EXPANSION_TERMS, query.length());
//		// if (ApplicationSetup.EXPANSION_TERMS == 0) {
//		// numberOfTermsToReweight = 0;
//		// }
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
//			/**
//			 * An alternate option is to do psuedo relevance feedback
//			 */
//		}
//
//		int positiveCount = positiveDocids.length;
//		int negativeCount = negativeDocids.length;
//
//		System.out.println("# POSITIVE DOCS: " + positiveCount);
//		System.out.println("# NEGATIVE DOCS: " + negativeCount);
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
//		// get all terms in positive documents as candidate expansion terms
//		// for each positive feedback document
//		for (int i = 0; i < positiveCount; i++) {
//			TermFreqVector tfv = this.reader.getTermFreqVector(
//					positiveDocids[i], field);
//			if (tfv == null)
//				logger.warn("document " + this.ScoreDoc[i].doc + " not found");
//			else {
//				String strterms[] = tfv.getTerms();
//				int freqs[] = tfv.getTermFrequencies();
//				for (int j = 0; j < strterms.length; j++)
//					positiveCandidateTerms.insertTerm(strterms[j],
//							(float) freqs[j]);
//			}
//		}
//
//		System.out.println("# UNIQUE TERMS IN POSITIVE DOCS: "
//				+ positiveCandidateTerms.getNumberOfUniqueTerms());
//
//		ExpansionTerm[] positiveQueryTerms = positiveCandidateTerms
//				.getExpandedTerms(numberOfTermsToReweight, QEModel);
//		// --------------------------------------------------------------------
//		// COMPUTATION OF NEGATIVE WEIGHTS ------------------------------------
//		// --------------------------------------------------------------------
//
//		// get total number of tokens in negative documents
//		float negativeDocLength = 0;
//		for (int i = 0; i < negativeCount; i++) {
//			negativeDocLength // +=
//			// documentIndex.getDocumentLength(negativeDocids[i]);
//			+= searcher.getFieldLength(field, negativeDocids[i]);
//		}
//
//		ExpansionTerms negativeCandidateTerms // = new ExpansionTerms(collStats,
//		// negativeDocLength, lexicon);
//		= new ExpansionTerms(searcher, negativeDocLength, field);
//		// get all terms in negative documents as candidate expansion terms
//		// for each negative feedback document
//		for (int i = 0; i < negativeCount; i++) {
//			TermFreqVector tfv = this.reader.getTermFreqVector(
//					negativeDocids[i], field);
//			if (tfv == null)
//				logger.warn("document " + this.ScoreDoc[i].doc + " not found");
//			else {
//				String strterms[] = tfv.getTerms();
//				int freqs[] = tfv.getTermFrequencies();
//				for (int j = 0; j < strterms.length; j++)
//					negativeCandidateTerms.insertTerm(strterms[j],
//							(float) freqs[j]);
//			}
//		}
//
//		System.out.println("# UNIQUE TERMS IN NEGATIVE DOCS: "
//				+ negativeCandidateTerms.getNumberOfUniqueTerms());
//
//		ExpansionTerm[] negativeQueryTerms = negativeCandidateTerms
//				.getExpandedTerms(numberOfTermsToReweight, QEModel);
//
//		// --------------------------------------------------------------------
//		// COMBINED WEIGHTS ---------------------------------------------------
//		// --------------------------------------------------------------------
//
//		// temporary structure for merging positiveQueryTerms and
//		// negativeQueryTerms
//		// TIntObjectHashMap<ExpansionTerm> queryTerms = new
//		// TIntObjectHashMap<ExpansionTerm>();
//		HashMap<String, ExpansionTerm> queryTerms = new HashMap<String, ExpansionTerm>();
//		// put all positive query term ids
//		// for (Integer k : positiveQueryTerms.keys()) {
//		// queryTerms.put(k, null);
//		// }
//		for (int i = 0; i < positiveQueryTerms.length; i++) {
//			queryTerms.put(positiveQueryTerms[i].getTerm(), null);
//
//		}
//		// put all negative query term ids
//		// for (Integer k : negativeQueryTerms.keys()) {
//		// queryTerms.put(k, null);
//		// }
//		for (int i = 0; i < negativeQueryTerms.length; i++) {
//			queryTerms.put(negativeQueryTerms[i].getTerm(), null);
//		}
//
//		System.out.println("# UNIQUE TERMS IN ALL DOCS: " + queryTerms.size());
//
//		// for (int i = 0; i < positiveQueryTerms.length; i++) {
//		// String term = positiveQueryTerms[i].getTerm();
//		// ExpansionTerm expTerm = queryTerms.get(term);
//		//
//		// if (expTerm == null) {
//		// expTerm = positiveQueryTerms[i].clone();
//		// expTerm.setWeightExpansion(beta
//		// * positiveQueryTerms[i].getWeightExpansion());
//		// queryTerms.put(term, positiveQueryTerms[i]);
//		// } else {
//		// expTerm.setWeightExpansion(expTerm.getWeightExpansion() + beta
//		// * positiveQueryTerms[i].getWeightExpansion());
//		// }
//		// }
//		//
//		// for (int i = 0; i < negativeQueryTerms.length; i++) {
//		// String term = negativeQueryTerms[i].getTerm();
//		// ExpansionTerm expTerm = queryTerms.get(term);
//		//
//		// if (expTerm == null) {
//		// expTerm = negativeQueryTerms[i].clone();
//		// expTerm.setWeightExpansion(-gamma
//		// * negativeQueryTerms[i].getWeightExpansion());
//		// queryTerms.put(term, positiveQueryTerms[i]);
//		// } else {
//		// expTerm.setWeightExpansion(expTerm.getWeightExpansion() - gamma
//		// * negativeQueryTerms[i].getWeightExpansion());
//		// }
//		// }
//
//		if (EXPAND_POS_TERMS) {
//
//			ExpansionTerm[] posTermArr = positiveQueryTerms;
//			Arrays.sort(posTermArr);
//			int numberOfTermsToReweigh = Math.min(posTermArr.length,
//					ApplicationSetup.EXPANSION_TERMS);
//
//			for (int i = 0; i < numberOfTermsToReweigh; i++) {
//
//				ExpansionTerm expandedTerm = posTermArr[i];
//				// queryTerms.get(posTermArr[i].getTerm());
//
//				BooleanClause clause = generateClause(expandedTerm);
//				BooleanClause tmpClause = this.cluaseSet.get(clause);
//				if (tmpClause != null) {
//					Query qry = tmpClause.getQuery();
//					qry.setOccurNum(qry.getOccurNum() + this.beta
//							* expandedTerm.getWeightExpansion());
//				} else {
//					Query qry = clause.getQuery();
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
//			/*
//			 * 2008/07/30 Ben: Expand the query with terms from the negative
//			 * document set. The number of expanded terms is half of that from
//			 * the positive document set.
//			 */
//			int modified = 0, added = 0;
//			for (int i = negTermArr.length - 1; i >= negTermArr.length
//					- numberOfTermsToReweigh; i--) {
//				ExpansionTerm expandedTerm = negTermArr[i];
//				// queryTerms.get(negTermArr[i].getTerm());
//				BooleanClause clause = generateClause(expandedTerm);
//				BooleanClause tmpClause = this.cluaseSet.get(clause);
//				if (tmpClause != null) {
//					Query qry = tmpClause.getQuery();
//					qry.setOccurNum(qry.getOccurNum() - this.gamma
//							* expandedTerm.getWeightExpansion());
//					modified++;
//				} else {
//					added++;
//					Query qry = clause.getQuery();
//					qry.setOccurNum(-this.gamma
//							* expandedTerm.getWeightExpansion());
//					this.cluaseSet.put(clause, clause);
//				}
//			}
//			logger.info("negative terms added:" + added + ", modified: "
//					+ modified);
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
//}
//