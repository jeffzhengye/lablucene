
package org.apache.lucene.search.model;
/**
 * This class implements the PL2 weighting model.
 */
public class In_expB2 extends WeightingModel {
	/** 
	 * A default constructor. This must be followed 
	 * by specifying the c value.
	 */
	public In_expB2() {
		super();
		this.c=1.0f;
	}
	/** 
	 * Constructs an instance of this class with the specified 
	 * value for the parameter beta.
	 * @param c the term frequency normalisation parameter value.
	 */
	public In_expB2(float c) {
		this();
		this.c = c;
	}
	/**
	 * Returns the name of the model.
	 * @return the name of the model
	 */
	public final String getInfo() {
		return "In_expB2c=" + c;
	}
	/**
	* This method provides the contract for implementing weighting models.
	* @param tf The term frequency in the document
	* @param docLength the document's length
	* @return the score assigned to a document with the given tf and 
	*         docLength, and other preset parameters
	*/
	public final float score(float tf, float docLength) {
		float TF =
			tf * Idf.log(1.0f + (c * averageDocumentLength) / docLength);
		float NORM = (termFrequency + 1f) / (documentFrequency * (TF + 1f));
		float f = this.termFrequency / numberOfDocuments;
		float n_exp = numberOfDocuments * (float)(1 - Math.exp(-f));
		return TF * i.idfDFR(n_exp) * keyFrequency * NORM;
	}
	public float unseenScore(float length){
		return 0;
	}
	/**
	*This method provides the contract for implementing weighting models.
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
		//float TF = tf * beta * averageDocumentLength / docLength;
		float TF =
			tf * Idf.log(1.0f + (c * averageDocumentLength) / docLength);
		float NORM = (termFrequency + 1f) / (documentFrequency * (TF + 1f));
		float f = termFrequency / numberOfDocuments;
		float n_exp =
			numberOfDocuments * (float)(1 - Math.exp(-f));
		return TF * i.idfDFR(n_exp) * keyFrequency * NORM;
	}

}
