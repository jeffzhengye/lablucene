package org.apache.lucene.postProcess;

import gnu.trove.TObjectFloatHashMap;
import gnu.trove.TObjectIntHashMap;

import java.io.IOException;
import java.util.Arrays;

import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermFreqVector;
import org.apache.lucene.search.RBooleanQuery;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.search.TopDocCollector;
import org.apache.lucene.search.model.BM25;
import org.apache.lucene.search.model.Idf;
import org.apache.lucene.search.model.WeightModelManager;
import org.apache.lucene.search.model.WeightingModel;
import org.dutir.lucene.util.ApplicationSetup;
import org.dutir.lucene.util.ExpansionTerms;
import org.dutir.lucene.util.TermsCache;
import org.dutir.lucene.util.ExpansionTerms.ExpansionTerm;

/**
 * Fun: expand the query based on Terrier's build-in QE method, and then use the
 * expanded query to rerank the results obtained from the first round retrieval
 * 
 * @author YeZheng
 * 
 */
public class QERerankPostProcess extends QueryExpansion {

	int effDocumentsNum = Integer.parseInt(ApplicationSetup.getProperty(
			"QERerankPostProcess.effDocumentsNum", "5"));
	int numberOfTermsAsFeatures = Integer.parseInt(ApplicationSetup
			.getProperty("QERerankPostProcess.numberOfTermsAsFeatures", "100"));
	
	/**
	 * store the weighs from the QE process. 
	 */
	public TObjectFloatHashMap<String> qeScoresMap = new TObjectFloatHashMap<String>();
	public TObjectFloatHashMap<String> idfMap = new TObjectFloatHashMap<String>();
	public TObjectFloatHashMap<String> dfMap = new TObjectFloatHashMap<String>();
	public TObjectFloatHashMap<String> keyFMap = new TObjectFloatHashMap<String>();
	WeightingModel wmodel = null;
	
	
	float qeScores[];

	private float k_1 = 1.2f;
	private float b = 0.35f;
	private float k_3 = 8f;
	protected float averageDocumentLength;
	float numberOfDocuments;

	void reset() {
		qeScores = null;
		qeScoresMap.clear();
		idfMap.clear();
		dfMap.clear();
		keyFMap.clear();
	}

	public String getInfo() {
		if (QEModel != null)
			return "QERerankPostProcess_" + QEModel.getInfo()
					+ "_doc=" + effDocumentsNum
					+ "_numberOfTermsAsFeatures=" + numberOfTermsAsFeatures;
		return "";
	}

	public void setup() {
		wmodel = WeightModelManager.getFromPropertyFile(searcher, field);
		if(wmodel instanceof BM25){
			wmodel.setParameter(0.75f);
		}
		averageDocumentLength = this.searcher.getAverageLength(field);
		try {
			numberOfDocuments = this.searcher.maxDoc();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	static TermsCache tcache = TermsCache.getInstance();
	
	public TopDocCollector postProcess(RBooleanQuery query,
			TopDocCollector topDoc, Searcher seacher) {
		this.setup(query, topDoc, seacher);
		setup();
		reset();
		

		logger.info("Starting query expansion post-processing.");
		// get the query expansion model to use

		int set_size = this.ScoreDoc.length;
		int docids[] = new int[set_size];
		float scores[] = new float[set_size];
		for (int i = 0; i < set_size; i++) {
			docids[i] = this.ScoreDoc[i].doc;
			scores[i] = this.ScoreDoc[i].score;
		}

		// get the expanded query terms
		try {
			expandQuery(effDocumentsNum);
		} catch (Exception e1) {
			e1.printStackTrace();
		}

		qeScores = new float[set_size];
		Arrays.fill(qeScores, 0);
		// reranking the documents
		for (int i = 0; i < set_size; i++) {
			float docLength = this.searcher.getFieldLength(field, docids[i]);
			TermFreqVector tfv = null;
			try {
				tfv = this.searcher.getIndexReader().getTermFreqVector(
						docids[i], field);
				// t_tfs_cache[i] = tfv;
			} catch (IOException e) {
				e.printStackTrace();
			}
			String strterms[] = tfv.getTerms();
			int freqs[] = tfv.getTermFrequencies();

			int querypos[] = new int[this.termSet.size()];
			TermFreqVector  qtfv[] = new TermFreqVector[this.termSet.size()];
			int pos = 0;
			Arrays.fill(querypos, -1);
			
			for (int k = 0; k < strterms.length; k++) {
				String termid = strterms[k];
				int termtf = freqs[k];
				
				if(this.termSet.contains(termid)){
					querypos[pos] = k;
					qtfv[pos++] = tfv;
				}
				
				if (qeScoresMap.contains(termid)) {
					float keyFrequency = qeScoresMap.get(termid);

					float termFreq = getTermFreq(termid);
					wmodel.setTermFrequency(termFreq);
					wmodel.setKeyFrequency(keyFrequency);
					wmodel.setDocumentFrequency(dfMap.get(termid));
					float retV = wmodel.score(termtf, docLength);
					
//					qeScores[i] += idfMap.get(termid)
//							* ((k_3 + 1) * keyFrequency / (k_3 + keyFrequency))
//							* ((k_1 + 1d) * termtf / (k_1
//									* ((1 - b) + b * docLength
//											/ averageDocumentLength) + termtf));
					qeScores[i] += retV;
				}
			}
			scores[i] = qeScores[i];
		}
		// HeapSort.descendingHeapSort(scores, docids, occurences, set_size);
//		int reRankNum = qeTag ? Math.min(Mitra_Reranking_rerankNum, set_size):set_size;
		
		int num = Integer.parseInt(ApplicationSetup.getProperty(
				"TRECQuerying.endFeedback", "1000"));
		
		TopDocCollector cls = new TopDocCollector(num);
		cls.setInfo(topDoc.getInfo());
		cls.setInfo_add(this.getInfo());
		for(int i=0; i < docids.length; i++){
			cls.collect(docids[i], scores[i]);
		}
		return cls;
	}

	public float getTermFreq(String term){
		TermsCache.Item item = tcache.getItem(new Term(field, term), searcher);
		float df = item.df;
		float termFreq = item.ctf;
		return termFreq;
	}
	public float getScorce(String termid, int termtf, float docLength) {
		float keyFrequency = qeScoresMap.get(termid);
//		float retV = idfMap.get(termid)
//				* ((k_3 + 1) * keyFrequency / (k_3 + keyFrequency))
//				* ((k_1 + 1f) * termtf / (k_1
//						* ((1 - b) + b * docLength / averageDocumentLength) + termtf));
		float termFreq = getTermFreq(termid);
		wmodel.setTermFrequency(termFreq);
		wmodel.setKeyFrequency(keyFrequency);
		wmodel.setDocumentFrequency(dfMap.get(termid));
		float retV = wmodel.score(termtf, docLength);
		
		return retV;
	}


	public ExpansionTerms expandQuery(int epNum) throws Exception {
		// the number of term to re-weight (i.e. to do relevance feedback) is
		// the maximum between the system setting and the actual query length.
		// if the query length is larger than the system setting, it does not
		// make sense to do relevance feedback for a portion of the query.
		// Therefore, 
		// we re-weight the number of query length of terms.

		// If no document retrieved, keep the original query.
		if (this.ScoreDoc.length < 1) {
			return null;
		}

		// int[] docIDs = resultSet.getDocids();
		// float[] scores = resultSet.getScores();
		int set_size = this.ScoreDoc.length;
		int[] docIDs = new int[set_size];
		float[] scores = new float[set_size];
		for (int i = 0; i < set_size; i++) {
			docIDs[i] = this.ScoreDoc[i].doc;
			scores[i] = this.ScoreDoc[i].score;
		}
		float totalDocumentLength = 0;

		// if the number of retrieved documents is lower than the parameter
		// EXPANSION_DOCUMENTS, reduce the number of documents for expansion
		// to the number of retrieved documents.
		int effDocuments = Math.min(docIDs.length, effDocumentsNum);
		for (int i = 0; i < effDocuments; i++) {
			totalDocumentLength += this.searcher.getFieldLength(field,
					docIDs[i]);
			// if (logger.isDebugEnabled()) {
			// logger.debug((i + 1) + ": "
			// + documentIndex.getDocumentNumber(docIDs[i]) + " ("
			// + docIDs[i] + ") with " + scores[i]);
			// }
		}
		ExpansionTerms expansionTerms = new ExpansionTerms(this.searcher,
				totalDocumentLength, field);

		for (int i = 0; i < epNum; i++) {
			TermFreqVector tfv = null;
			try {
				tfv = this.searcher.getIndexReader().getTermFreqVector(
						docIDs[i], field);
//				t_tfs_cache[i] = tfv;
			} catch (IOException e) {
				e.printStackTrace();
			}
			String strterms[] = tfv.getTerms();
			int freqs[] = tfv.getTermFrequencies();
			for (int j = 0; j < strterms.length; j++)
				expansionTerms.insertTerm(strterms[j], freqs[j]);
		}

		
		ExpansionTerm expandedTerms[] = expansionTerms.getExpandedTerms(
				numberOfTermsAsFeatures, QEModel);

		for (String term : this.termSet) {
//			LexiconEntry entry = lexicon.getLexiconEntry(term);
//			int n_t = entry.n_t;
			Term lterm = new Term(field, term);
			// float TF = searcher.termFreq(term);
			float n_t = searcher.docFreq(lterm);
//			qeScoresMap.put(entry.termId, query.getTermWeight(term));
			//TODO: 
			qeScoresMap.put(term, 1);
			idfMap.put(term, Idf.log((numberOfDocuments - n_t + 0.5f)
					/ (n_t + 0.5f)));
			dfMap.put(term, n_t);
		}

		for (int i = 0; i < expandedTerms.length; i++) {
			ExpansionTerm expandedTerm = expandedTerms[i];
			String strTerm = expandedTerm.getTerm();
			// if(query.getTermWeight(strTerm) > 0){
			// continue;
			// }
			String term = expandedTerm.getTerm();
			Term lterm = new Term(field, term);
			// float TF = searcher.termFreq(term);
			float n_t = searcher.docFreq(lterm);

			qeScoresMap.adjustOrPutValue(term,
					expandedTerm.getWeightExpansion(), expandedTerm.getWeightExpansion());
			idfMap.put(term, Idf.log((numberOfDocuments - n_t + 0.5f)
					/ (n_t + 0.5f)));
			dfMap.put(term, n_t);
		}

		return expansionTerms;
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		QERerankPostProcess qer = new QERerankPostProcess();
		qer.qeScoresMap.put("1225", 1225);
		qer.qeScoresMap.put("294", 1225);
		qer.qeScoresMap.put("1007", 1225);
		qer.qeScoresMap.put("536", 1225);
		if (qer.qeScoresMap.contains(3373)) {
			System.out.println("bug");
		}
	}

}
