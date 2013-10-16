
package org.apache.lucene.search.model;
/**
 * This class implements the DFR_BM25 weighting model.
 * This DFR model, if expanded in Taylor's series, provides the BM25 formula, when the parameter c 
 *  is set to 1.
 * 
 */
public class DFR_BM25 extends WeightingModel {
	/** 
	 * A default constructor. This must be followed 
	 * by specifying the c value.
	 */
	public DFR_BM25() {
		super();
	}
	/** 
	 * Constructs an instance of this class with the specified 
	 * value for the parameter c.
	 * @param c the term frequency normalisation parameter value.
	 */
	public DFR_BM25(float c) {
		super();
		this.c = c;
	}
	/**
	 * Returns the name of the model.
	 * @return the name of the model
	 */
	public final String getInfo() {
		return "DFR_BM25c" + c;
	}
	/**
	* Computes the score according to the model DFR_BM25.
	* @param tf The term frequency in the document
	* @param docLength the document's length
	* @return the score assigned to a document with the 
	*         given tf and docLength, and other preset parameters
	*/
	public final float score(
			float tf,
			float docLength) {
		float k_1 = 1.2f;
	    float k_3 = 1000f;
		float TF = tf * Idf.log(1.0f + (c * averageDocumentLength) / docLength);
		float NORM = 1f / (TF + k_1);
		return  ( (k_3 + 1f) * keyFrequency / (k_3 + keyFrequency)) * NORM 
				*TF * Idf.log((numberOfDocuments - documentFrequency + 0.5f) / 
				(documentFrequency + 0.5f));
	}
	/**
	* Computes the score according to the model DFR_BM25.
	* @param tf The term frequency in the document
	* @param docLength the document's length
	* @param documentFrequency The document frequency of the term
	* @param termFrequency the term frequency in the collection
	* @param keyFrequency the term frequency in the query
	* @return the score returned by the implemented weighting model.
	*/
	public final float score(
			float tf,
			float docLength,
			float documentFrequency,
			float termFrequency,
			float keyFrequency) {
		float k_1 = 1.2f;
	    float k_3 = 1000f;
		float TF = tf * Idf.log(1.0f + (c * averageDocumentLength) / docLength);
		float NORM = 1f / (TF + k_1);
		return  ( (k_3 + 1f) * keyFrequency / (k_3 + keyFrequency)) * NORM 
				* TF * Idf.log((numberOfDocuments - documentFrequency + 0.5f) / 
						(documentFrequency + 0.5f));
	}
	
	public float unseenScore(float length){
		return 0;
	}
}
