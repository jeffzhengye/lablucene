package org.apache.lucene.search;

import java.io.IOException;

import org.apache.lucene.TermDocsCache;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermDocs;
import org.apache.lucene.search.model.WeightModelManager;
import org.apache.lucene.search.model.WeightingModel;
import org.dutir.lucene.util.TermsCache;

/**
 * this class 
 * @author yezheng
 *
 */
public class RTermWeight implements Weight {
	String description = "";
	static TermsCache tcache = TermsCache.getInstance();
//	static TermDocsCache termDocsCache = TermDocsCache.getInstance();
	
	protected Similarity similarity;
//	protected float value = -1;
//	protected float idf = 1;
	protected float queryNorm;
//	protected float queryWeight = 1;
//	// added yezheng
//	protected float df = 0;
//	protected float termFreq = 0;
	protected float boost = 1;
//	protected int maxDoc;
//	protected float averageFiledLength = 0;
//	protected float numberOfTokens = 0;
//	protected float numberOfUniqueTerms = 0;
	
	
	protected WeightingModel weightModel = null;
	protected RTermQuery query;
	protected Term term;

	public RTermWeight() {

	}

	public RTermWeight(Searcher searcher, RTermQuery query) throws IOException {
		initial(searcher, query);
	}

	
	public String getInfo(){
		return this.description;
	}
	
	public void setInfo(String info){
		this.description = info;
	}
	
	/**
	 * this is method must be overwrote, if u want to implement your own
	 * weighting model that is different from the framework of BM25, DFR.
	 * 
	 * @param searcher
	 * @param query
	 */
	public void initial(Searcher searcher, RTermQuery query) {
		try {
			this.similarity = searcher.getSimilarity();
			this.weightModel = WeightModelManager.getFromPropertyFile(searcher, query);
			this.description = this.weightModel.getInfo();
			this.query = query;
			this.term = query.getTerm();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public WeightingModel getweightModel() {
		return weightModel;
	}

	// 1. where this fun is used? --> Query-->weight()
	public float sumOfSquaredWeights() {
//		throw new UnsupportedOperationException();
		/**
		 * make sure that it returns 1
		 */
		this.boost = getBoost();
		return boost * boost;
//		// queryWeight = idf * boost; // compute query weight
//		queryWeight = 1;
//		return queryWeight * queryWeight; // square it
	}

	// 1. where does queryNorm come from
	public void normalize(float queryNorm) {
//		throw new UnsupportedOperationException();
		this.queryNorm = queryNorm;
//		queryWeight *= queryNorm; // normalize query weight
//		value = queryWeight * idf; // idf for document
	}

	
	
	public Scorer scorer(IndexReader reader) throws IOException {
		TermDocs termDocs = reader.termDocs(term);
//		TermDocs termDocs = termDocsCache.termDocs(term, reader);
		//The corresponding RTermQuery is not in the index vocabulary. 
		
		//This statement can not be added, or there will lack a "optionalScorers
		if(this.weightModel.getDocumentFrequency() <= 0){
			return null;
		}
		if (termDocs == null)
			return null;
		GeneralTermScorer gts = new GeneralTermScorer(this, termDocs,
				similarity, reader.norms(term.field()));
		gts.setSearcher(reader);
		return gts;
	}

	public String toString() {
		return "weight(" + this.query + ")";
	}

	public Query getQuery() {
		return this.query;
	}

	public Term getTerm() {
		return this.term;
	}

//	public int getMaxDoc() {
//		return this.maxDoc;
//	}

	public float getValue() {
		return getBoost();
	}

//	// added yezheng
//	public float getDF() {
//		return df;
//	}

//	public float getTermFreq() {
//		return termFreq;
//	}

//	public float getQueryNorm() {
//		return queryNorm;
//	}

	public float getBoost() {
		return boost;
	}

	public Explanation explain(IndexReader reader, int doc) throws IOException {

		ComplexExplanation result = new ComplexExplanation();
		result.setDescription("weight(" + getQuery() + " in " + doc
				+ "), product of:");
		Explanation scorerExpl = scorer(reader).explain(doc);
		result.setValue(scorerExpl.getValue());
		result.setMatch(true);
		result.addDetail(scorerExpl);
		
		return result;
	}

}
