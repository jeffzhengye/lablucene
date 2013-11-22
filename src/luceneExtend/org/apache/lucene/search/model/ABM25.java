
package org.apache.lucene.search.model;

import org.dutir.lucene.util.ApplicationSetup;

/**
 * This class implements the Okapi BM25 weighting model. The
 * default parameters used are:<br>
 * k_1 = 1.2d<br>
 * k_3 = 8d<br>
 * b = 0.75d<br> The b parameter can be altered by using the setParameter method.
 */
public class ABM25 extends WeightingModel {
	/** The constant k_1.*/
	private float k_1 = 1.2f;
	
	/** The constant k_3.*/
	private float k_3 = 8f;
	
	/** The parameter b.*/
	private float b = Float.parseFloat(ApplicationSetup.getProperty("bm25.b", "0.35"));
	
	/** A default constructor.*/
	public ABM25() {
		super();
	}
	/**
	 * Returns the name of the model.
	 * @return the name of the model
	 */
	public final String getInfo() {
		return "ABM25";
	}
	/**
	 * Uses BM25 to compute a weight for a term in a document.
	 * @param tf The term frequency in the document
	 * @param docLength the document's length
	 * @return the score assigned to a document with the given 
	 *         tf and docLength, and other preset parameters
	 */
	public final float score(float tf, float docLength) {
		float alpha = 2 / (1 + Idf.log(1 + this.querylength));
		b = 0.3f * alpha + 0.7f * (1-alpha);
		float K = k_1 * ((1 - b) + b * docLength / averageDocumentLength) + tf;
		K = ((k_1 + 1f) * tf / K);
		
//		float LRTF = tf * Idf.log(1 + averageDocumentLength/docLength);
//		float BLRTF = LRTF / (1 + LRTF);
//		K = BLRTF;
		
		float idf = ((k_3 + 1) * keyFrequency / (k_3 + keyFrequency))
				* Idf.log((numberOfDocuments - documentFrequency + 0.5f)
						/ (documentFrequency + 0.5f));
		return K * idf;
	}                                                    
	
	public float unseenScore(float length){
		return 0;
	}
	/**
	 * Uses BM25 to compute a weight for a term in a document.
	 * @param tf The term frequency in the document
	 * @param docLength the document's length
	 * @param n_t The document frequency of the term
	 * @param F_t the term frequency in the collection
	 * @param keyFrequency the term frequency in the query
	 * @return the score assigned by the weighting model BM25.
	 */
	public final float score(
		float tf,
		float docLength,
		float n_t,
		float F_t,
		float keyFrequency) {
		float alpha = 2 / (1 + Idf.log(1 + this.querylength));
		b = 0.3f * alpha + 0.7f * (1-alpha);
		
//	    float K = k_1 * ((1 - b) + b * docLength / averageDocumentLength) + tf;
		
		float K = (k_1 + 1f) * tf / (k_1 * ((1 - b) + b * docLength / averageDocumentLength) + tf);
	    
//	    float LRTF = tf * Idf.log(1 + averageDocumentLength/docLength);                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                
//		float BLRTF = LRTF / (1 + LRTF);
//		K = BLRTF;
	    
	    return Idf.log((numberOfDocuments - n_t + 0.5f) / (n_t+ 0.5f)) *
//			((k_1 + 1d) * tf / (K + tf)) *
//			((k_1 + 1f) * tf / (K )) *
	    		K *
			((k_3+1)*keyFrequency/(k_3+keyFrequency));
	}

	/**
	 * Sets the b parameter to BM25 ranking formula
	 * @param b the b parameter value to use.
	 */
	public void setParameter(float b) {
	    this.b = b;
	}
	

	/**
	 * Returns the b parameter to the BM25 ranking formula as set by setParameter()
	 */
	public float getParameter() {
	    return this.b;
	}
	

}
