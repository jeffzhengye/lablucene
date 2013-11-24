
package org.apache.lucene.search.model;

import org.dutir.lucene.util.ApplicationSetup;

/**
 * This class implements the length-based BM25 weighting model from "When Documents Are Very Long, BM25 Fails!". The
 * default parameters used are:<br>
 * k_1 = 1.2d<br>
 * k_3 = 8d<br>
 * b = 0.75d<br> The b parameter can be altered by using the setParameter method.
 */
public class LBM25 extends WeightingModel {
	/** The constant k_1.*/
	private float k_1 = 1.2f;
	
	/** The constant k_3.*/
	private float k_3 = 8f;
	
	/** The parameter b.*/
	private float b = Float.parseFloat(ApplicationSetup.getProperty("bm25.b", "0.35"));
	private float sigma = Float.parseFloat(ApplicationSetup.getProperty("bm25.sigma", "0.5"));
	
	/** A default constructor.*/
	public LBM25() {
		super();
	}
	/**
	 * Returns the name of the model.
	 * @return the name of the model
	 */
	public final String getInfo() {
		return "LBM25b=" + b + "sigma=" + sigma;
	}
	/**
	 * Uses BM25 to compute a weight for a term in a document.
	 * @param tf The term frequency in the document
	 * @param docLength the document's length
	 * @return the score assigned to a document with the given 
	 *         tf and docLength, and other preset parameters
	 */
	public final float score(float tf, float docLength) {
		
		float ltf = tf/(1-b + b * docLength / averageDocumentLength);
		float ntf = (k_1 + 1) *(ltf+sigma)/(k_1 + ltf + sigma);
		float K = ntf;
		
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
		
		float ltf = tf/(1-b + b * docLength / averageDocumentLength);
		float ntf = (k_1 + 1) *(ltf+sigma)/(k_1 + ltf + sigma);
		float K = ntf;
		
	    
	    return Idf.log((numberOfDocuments - n_t + 0.5f) / (n_t+ 0.5f)) *
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
