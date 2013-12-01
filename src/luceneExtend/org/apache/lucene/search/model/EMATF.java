package org.apache.lucene.search.model;

import java.io.IOException;

import org.apache.lucene.index.TermFreqVector;
import org.apache.lucene.util.SmallFloat;
import org.dutir.lucene.util.ATFCache;
import org.dutir.lucene.util.ApplicationSetup;

import redis.clients.jedis.Jedis;

/**
 * This class implements the MATF weighting model in A Novel TF-IDF Weighting Scheme for Effective Ranking. 
 */
public class EMATF extends WeightingModel {
	static byte cache[] = null;
	static byte norm[] = null;
	/** A default constructor.*/
	public EMATF() {
		super();
	}
	/**
	 * Returns the name of the model.
	 * @return the name of the model
	 */
	public final String getInfo() {
		return "EMATF";
	}
	/**
	 * Uses BM25 to compute a weight for a term in a document.
	 * @param tf The term frequency in the document
	 * @param docLength the document's length
	 * @return the score assigned to a document with the given 
	 *         tf and docLength, and other preset parameters
	 */
	public final float score(float tf, float docLength) {
		throw new RuntimeException("should not use this method");
	}
	
	public final float score(float tf, float docLength, int innerid) {
		float AEF = this.termFrequency/this.documentFrequency;
		
		
		float IDF = Idf.log((numberOfDocuments + 1)/this.documentFrequency) * AEF/(1 + AEF);
		IDF = Idf.log((numberOfDocuments - documentFrequency + 0.5f)
				/ (documentFrequency + 0.5f));
		
		float QLF = this.querylength;
		float alpha = 2 / (1 + Idf.log(1 + QLF));
		
		float RITF = Idf.log(1 + tf)/Idf.log(1 + AvgTF(docLength, innerid));
		
		RITF = Idf.log((numberOfDocuments + 1)/this.documentFrequency) * RITF/SmallFloat.byte315ToFloat(norm[innerid]) * docLength/AvgTF(docLength, innerid);
		
		float LRTF = tf * Idf.log(1 + averageDocumentLength/docLength);
		float BRITF = RITF/ (1 + RITF);
		float BLRTF = LRTF / (1 + LRTF);
		
		float TFF = alpha * BRITF + (1 - alpha) * BLRTF;
	    
	    return keyFrequency * TFF * IDF;
	}
	
	
	public float unseenScore(float length){
		return 0;
	}
	/**
	 * Uses MATF to compute a weight for a term in a document.
	 * @param tf The term frequency in the document
	 * @param docLength the document's length
	 * @param n_t The document frequency of the term
	 * @param F_t the term frequency in the collection
	 * @param keyFrequency the term frequency in the query
	 * @return the score assigned by the weighting model.
	 */
	public final float score(
		float tf,
		float docLength,
		float n_t,
		float F_t,
		float keyFrequency) {
		
		throw new RuntimeException("should not use this method");
	}

	
	public final float score(
			float tf,
			float docLength,
			float n_t,
			float F_t,
			float keyFrequency, int innerid) {
			
		this.keyFrequency = keyFrequency;
		this.documentFrequency = n_t;
		this.termFrequency = F_t;
		return score(tf, docLength, innerid);
		}
	
	static Jedis jedis = new Jedis("127.0.0.1", 6379, 100000); // zheng's
	static String field = ApplicationSetup.getProperty(	
			"Lucene.QueryExpansion.FieldName", "content");
	static private String torediskey(int t) {
		int pos = ApplicationSetup.LUCENE_ETC.lastIndexOf("/");
		if(pos > -1){
			return ApplicationSetup.LUCENE_ETC.substring(pos + 1) + "|" + t;
		}
		return ApplicationSetup.LUCENE_ETC + "|" + t;
	}
	
	private float AvgTF(float docLength, int innerid) {
		if(cache != null){
			return SmallFloat.byte315ToFloat(cache[innerid]);
		}else{
//			cache = ATFCache.init(searcher);
			ATFCache.initAll(searcher);
			cache = ATFCache.cache;
			norm = ATFCache.norm;
		}
		String key = torediskey(innerid);
		String value = jedis.get(key);
		if (value != null)
			return Float.parseFloat(value);
		
		TermFreqVector tfv;
		try {
			tfv = this.searcher.getIndexReader().getTermFreqVector(
					innerid, field);
			if (tfv == null)
				throw new RuntimeException("run time error");
			else {
				String strterms[] = tfv.getTerms();
				float atf = docLength/strterms.length;
				jedis.set(key, Float.toString(atf));
				return atf;
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		throw new RuntimeException("run time error");
	}



}
