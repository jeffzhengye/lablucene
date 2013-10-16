/**
 * 
 */
package org.apache.lucene.postProcess;

import gnu.trove.TObjectFloatHashMap;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

import org.apache.log4j.Logger;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermFreqVector;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.RBooleanClause;
import org.apache.lucene.search.RBooleanQuery;
import org.apache.lucene.search.RQuery;
import org.apache.lucene.search.RTermQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.search.TopDocCollector;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.RBooleanClause.Occur;
import org.apache.lucene.search.model.Idf;
import org.dutir.lucene.util.ApplicationSetup;
import org.dutir.lucene.util.ExpansionTerms;
import org.dutir.lucene.util.TermsCache;
import org.dutir.lucene.util.ExpansionTerms.ExpansionTerm;
import org.dutir.lucene.util.TermsCache.Item;

/**
 * Caution: According to the implement of terrier, the occurrence of a term has
 * been modified, not the boost.
 * 
 * @author yezheng
 * 
 */

public class QueryExpansion implements PostProcess {

	static boolean Relevance = ApplicationSetup.FeedBack_Relevance;
	static String idtag = ApplicationSetup.getProperty("TrecDocTags.idtag",
			"DOCNO");

	static boolean LanguageModel = Boolean.parseBoolean(ApplicationSetup
			.getProperty("Lucene.Search.LanguageModel", "false"));
	static TermsCache tcache = TermsCache.getInstance();
	static TObjectFloatHashMap<String> idfCache = new TObjectFloatHashMap<String>();

	protected Item getItem(String term) {
		Term lterm = new Term(field, term);
		return tcache.getItem(lterm, searcher);
	}
	
	protected float getIDF(String term){
		float relVal = 0;
		if(idfCache.containsKey(term)){
			relVal = idfCache.get(term);
		}else{
			Item item = getItem(term);
			float Nt = item.df;
			relVal = Idf.log(((double) totalNumDocs - (double) Nt + 0.5d)
					/ ((double) Nt + 0.5d));
		}
		return relVal;
	}
	
	/** The constant k_1.*/
	private float k_1 = 1.2f;
	
	/** The constant b.*/
	private float b = 0.75f;
	
	
	protected float getBM25TF(int tf, float docLength){
		return k_1*tf/(tf+k_1*(1-b+b*docLength/averageDocumentLength));
	}

	protected boolean PARAMETER_FREE = true;
	protected boolean ROCCHIO_BETA = true;
	protected Searcher searcher = null;
	protected ScoreDoc[] ScoreDoc = null;
	protected IndexReader reader = null;
	protected Query pQuery = null;
	protected RBooleanClause bclause[] = null;
	protected float totalNumTokens = 0;
	protected int totalNumDocs = 0;
	protected float averageDocumentLength =0;
	protected HashMap<RBooleanClause, RBooleanClause> cluaseSet = null;



	/**
	 * a set of original string query terms
	 */
	protected HashSet<String> termSet = null;
	protected String topicId = null;
	static Logger logger = Logger.getLogger(QueryExpansion.class);
	static String field = ApplicationSetup.getProperty(	
			"Lucene.QueryExpansion.FieldName", "content");
	static String sQEModel = ApplicationSetup.getProperty(
			"Lucene.QueryExpansion.Model", "KL");
	static QueryExpansionModel QEModel = null;

	/**
	 * load the feedback selector, based on the property
	 * <tt>qe.feedback.selector</tt>
	 */
	protected FeedbackSelector getFeedbackSelector(Searcher searcher) {
		String name = ApplicationSetup.getProperty("qe.feedback.selector",
				"PseudoRelevanceFeedbackSelector");
//		if(logger.isInfoEnabled()) logger.info("FeedbackSelector: " + name);
		if (!name.contains("."))
			name = "org.apache.lucene.postProcess." + name;
		// else if (name.startsWith("org.apache.lucene.postProcess"))
		// name = name.replaceAll("uk.ac.gla.terrier", "org.terrier");

		FeedbackSelector next = null;
		try {
			next = Class.forName(name).asSubclass(FeedbackSelector.class)
					.newInstance();
			
		} catch (Exception e) {
			logger
					.error("Problem loading a FeedbackSelector called " + name,
							e);
			return null;
		}
		next.setField(field);
		next.setIndex(searcher);
//		int effDocuments = Math.min(ScoreDoc.length,
//				ApplicationSetup.EXPANSION_DOCUMENTS);
		int effDocuments = ApplicationSetup.EXPANSION_DOCUMENTS;
		if(next instanceof PseudoRelevanceFeedbackSelector){
			((PseudoRelevanceFeedbackSelector) next).setTopDocs(ScoreDoc);
			next.setExpDocuments(effDocuments);
		}else if(next instanceof RF08FeedbackSelector){
			((RF08FeedbackSelector) next).setTopDocs(ScoreDoc);
			next.setExpDocuments(effDocuments);
		}
		else{
			next.setExpDocuments(effDocuments);
		}
		return next;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.apache.lucene.postProcess.PostProcess#postProcess(org.apache.lucene
	 * .search.TopDocCollector, org.apache.lucene.search.Searcher)
	 */
	public TopDocCollector postProcess(RBooleanQuery query,
			TopDocCollector topDoc, Searcher seacher) {
		setup(query, topDoc, seacher); // it is necessary

		int numberOfTermsToReweight = Math.max(
				ApplicationSetup.EXPANSION_TERMS, bclause.length);

		if (ApplicationSetup.EXPANSION_TERMS == 0)
			numberOfTermsToReweight = 0;

		// If no document retrieved, keep the original query.
		if (ScoreDoc.length == 0) {
			return new TopDocCollector(0);
		}

		FeedbackSelector fselector = this.getFeedbackSelector(seacher);
		FeedbackDocuments fdocs = fselector.getFeedbackDocuments(topicId);

		ExpansionTerms expansionTerms = null;
		try {
			expansionTerms = new ExpansionTerms(searcher, fdocs.totalDocumentLength,
					field);
			for (int i = 0; i < fdocs.docid.length; i++) {
				TermFreqVector tfv = this.reader.getTermFreqVector(
						fdocs.docid[i], field);
				if (tfv == null)
					logger.warn("document " + fdocs.docid[i]
							+ " not found");
				else {
					String strterms[] = tfv.getTerms();
					int freqs[] = tfv.getTermFrequencies();
					for (int j = 0; j < strterms.length; j++)
						expansionTerms
								.insertTerm(strterms[j], (float) freqs[j]);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		ExpansionTerm[] expandedTerms = expansionTerms.getExpandedTerms(
				numberOfTermsToReweight, QEModel);

		for (int i = 0; i < expandedTerms.length; i++) {
			ExpansionTerm expandedTerm = expandedTerms[i];
			RBooleanClause clause = generateClause(expandedTerm);
			RBooleanClause tmpClause = this.cluaseSet.get(clause);
			if (tmpClause != null) {
				Query qry = tmpClause.getQuery();
				// qry.setBoost(qry.getOccurNum() +
				// expandedTerm.getWeightExpansion());
				float weight = (float) (qry.getOccurNum() + expandedTerm
						.getWeightExpansion());
				qry.setOccurNum(weight); // not boost

			} else {
				this.cluaseSet.put(clause, clause);
			}
		}
		RBooleanClause clauses[] = this.cluaseSet.values().toArray(
				new RBooleanClause[0]);
		RBooleanQuery fquery = generateNewQuery(clauses);
		logger.debug("Expansion Info: " + getExpansionInfo(clauses));

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

	protected static String getExpansionInfo(ExpansionTerm[] expandedTerms) {
		StringBuilder buf = new StringBuilder();
		for (int i = 0; i < expandedTerms.length; i++) {
			buf.append(expandedTerms[i].getTerm() + ":"
					+ expandedTerms[i].getWeightExpansion() + ", ");
		}
		return buf.toString();
	}

	protected static String getExpansionInfo(ExpansionTerm[] expandedTerms,
			int topK) {
		StringBuilder buf = new StringBuilder();
		for (int i = 0; i < expandedTerms.length && i < topK; i++) {
			buf.append(expandedTerms[i].getTerm() + ":"
					+ expandedTerms[i].getWeightExpansion() + ", ");
		}
		return buf.toString();
	}

	protected static String getExpansionInfo(ExpansionTerm[] expandedTerms,
			boolean sortag) {
		if (sortag) {
			Arrays.sort(expandedTerms);
		}
		StringBuilder buf = new StringBuilder();
		for (int i = 0; i < expandedTerms.length; i++) {
			buf.append(expandedTerms[i] + "\t");
		}
		return buf.toString();
	}

	protected static String getExpansionInfo(RBooleanClause clauses[]) {
		StringBuilder buf = new StringBuilder();
		RQuery qry = null;
		for (int i = 0; i < clauses.length; i++) {
			qry = clauses[i].getQuery();
			buf.append(qry.toString() + "\t");
		}
		return buf.toString();
	}

	protected static String getExpansionInfo(ExpansionTerm[] expandedTerms,
			int start, int end) {
		StringBuilder buf = new StringBuilder();
		for (int i = start; i < expandedTerms.length & i < end; i++) {
			buf.append(expandedTerms[i].getTerm() + ":"
					+ expandedTerms[i].getWeightExpansion() + "\t");
		}
		return buf.toString();
	}

	protected static RBooleanQuery generateNewQuery(RBooleanClause clauses[]) {
		RBooleanQuery bquery = new RBooleanQuery();
		for (int i = 0; i < clauses.length; i++) {
			bquery.add(clauses[i]);
		}
		return bquery;
	}

	protected static RBooleanClause generateClause(ExpansionTerm expandedTerm) {
		RTermQuery query = new RTermQuery(new Term(field, expandedTerm
				.getTerm()));
		// query.setBoost(expandedTerm.getWeightExpansion());
		query.setOccurNum(expandedTerm.getWeightExpansion());
		return new RBooleanClause(query, Occur.SHOULD);
	}

	/**
	 * 1. simply initial the local fields to refers 2. normalize the term
	 * frequency by dividing the maximum frequency in the original Query. range
	 * from 0 to 1;
	 * 
	 * @param query
	 * @param topDoc
	 * @param seacher
	 */
	protected void setup(RBooleanQuery query, TopDocCollector topDoc,
			Searcher seacher) {

		this.searcher = seacher;
		this.ScoreDoc = topDoc.topDocs().scoreDocs;
		this.pQuery = query;
		this.topicId = query.getTopicId();
		this.reader = seacher.getIndexReader();
		this.bclause = query.getClauses();
		this.totalNumTokens = this.searcher.getNumTokens(field);
		averageDocumentLength = this.searcher.getAverageLength(field);

		if (QEModel == null) {
			try {
				if (sQEModel.indexOf(".") == -1) {
					sQEModel = "org.apache.lucene.postProcess." + sQEModel;
				}
				QEModel = (QueryExpansionModel) Class.forName(sQEModel)
						.newInstance();
				QEModel.setCollectionLength(searcher.getNumTokens(field));
				QEModel.setAverageDocumentLength(searcher
						.getAverageLength(field));
				QEModel.setNumberOfDocuments(searcher.maxDoc());
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		cluaseSet = new HashMap<RBooleanClause, RBooleanClause>();
		termSet = new HashSet<String>();
		if (bclause.length == 1) {
			RBooleanClause bc = bclause[0];
			RQuery q = bc.getQuery();
			if (q instanceof RBooleanQuery) {
				this.bclause = ((RBooleanQuery) q).getClauses();

			}
		}

		// normalize term frequency
		float max = 0;
		float total = 0;
		for (int i = 0; i < bclause.length; i++) {
			Query rtq = bclause[i].getQuery();
			if (rtq instanceof RTermQuery) {
				float occur = rtq.getOccurNum();
				total += occur;
				if (max < occur) {
					max = occur;
				}
				cluaseSet.put(bclause[i], bclause[i]);
				termSet.add(((RTermQuery) rtq).getTerm().text());
			} else {
				cluaseSet.put(bclause[i], bclause[i]);
			}
		}

		for (int i = 0; i < bclause.length; i++) {
			Query rtq = bclause[i].getQuery();
			if (LanguageModel) {
				if (rtq instanceof RTermQuery) {
					rtq.setOccurNum(rtq.getOccurNum() / total);
				} else {
					rtq.setOccurNum(rtq.getOccurNum() / total);
					logger.warn("the query " + rtq + " is not expected for QE");
				}
			} else {
				if (rtq instanceof RTermQuery) {
					rtq.setOccurNum(rtq.getOccurNum() / max);
				} else {
					rtq.setOccurNum(rtq.getOccurNum() / max);
					logger.warn("the query " + rtq + " is not expected for QE");
				}
			}
		}
	}


	protected String outputStr(TopDocCollector topDoc) {
		TopDocs topDocs = topDoc.topDocs();
		int len = topDocs.totalHits;

		int maximum = Math.min(topDocs.scoreDocs.length, 1000);

		// if (minimum > set.getResultSize())
		// minimum = set.getResultSize();
		final String iteration = "Q" + "0";
		final String queryIdExpanded = this.topicId + " " + iteration + " ";
		final String methodExpanded = " " + "LabLucene" + ApplicationSetup.EOL;
		StringBuilder sbuffer = new StringBuilder();
		// the results are ordered in descending order
		// with respect to the score.
		for (int i = 0; i < maximum; i++) {
			int docid = topDocs.scoreDocs[i].doc;
			String filename = "" + docid;
			float score = topDocs.scoreDocs[i].score;

			if (filename != null && !filename.equals(filename.trim())) {
				if (logger.isDebugEnabled())
					logger.debug("orginal doc name not trimmed: |" + filename
							+ "|");
			}
			sbuffer.append(queryIdExpanded);
			sbuffer.append(filename);
			sbuffer.append(" ");
			sbuffer.append(i);
			sbuffer.append(" ");
			sbuffer.append(score);
			sbuffer.append(methodExpanded);
		}
		return sbuffer.toString();
	}
	
	static float get(float x) {
		return (8 + 1) * x / (x + 8);
	}

	protected String docidField = null;

	protected String getIdFieldName() {
		if (docidField == null) {
			docidField = ApplicationSetup.getProperty("TrecDocTags.idtag",
					"DOCNO");
		}
		return docidField;
	}

	public String getInfo() {
		int n_doc = ApplicationSetup.EXPANSION_DOCUMENTS;
		int n_term = ApplicationSetup.EXPANSION_TERMS;
		return (Relevance ? "TrueQE" : "QE") + "_" + n_doc + "_" + n_term;
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		System.out.println(get(0.5f));
		System.out.println(get(0.66f));
		System.out.println(get(0.02f));
	}

}
