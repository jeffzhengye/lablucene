
package org.apache.lucene.search.model;
/**
 * This class implements the InL2 weighting model.
 */
public class InL2 extends WeightingModel {
	/** 
	 * A default constructor. This must be followed 
	 * by specifying the c value.
	 */
	public InL2() {
		super();
		this.c=1.0f;
	}
	/** 
	 * Constructs an instance of this class with the specified 
	 * value for the parameter c.
	 * @param c the term frequency normalisation parameter value.
	 */
	public InL2(float c) {
		this();
		this.c = c;
	}
	/**
	 * Returns the name of the model.
	 * @return the name of the model
	 */
	public final String getInfo() {
		return "InL2c" + c;
	}
	/**
	* Computes the score according to the model InL2.
	* @param tf The term frequency in the document
	* @param docLength the document's length
	* @return the score assigned to a document with the 
	*         given tf and docLength, and other preset parameters
	*/
	public final float score(float tf, float docLength) {
		float TF =
			tf * Idf.log(1.0f + (c * averageDocumentLength) / docLength);
		float NORM = 1f / (TF + 1f);
		return TF * i.idfDFR(documentFrequency) * keyFrequency * NORM;
	}
	
	/**
	* Computes the score according to the model InL2.
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
		float TF =
			tf * Idf.log(1.0f + (c * averageDocumentLength) / docLength);
		float NORM = 1f / (TF + 1f);
		return TF * i.idfDFR(documentFrequency) * keyFrequency * NORM;
	}

}
