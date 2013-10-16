
package org.apache.lucene.search.model;
/**
 * This class implements the BB2 weighting model.
 */
public class BB2 extends WeightingModel {
	/** 
	 * A default constructor. This must be followed by 
	 * specifying the c value.
	 */
	public BB2() {
		super();
		this.c = 1.0f;
	}
	
	/** 
	 * Constructs an instance of this class with the 
	 * specified value for the parameter c.
	 * @param c the term frequency normalisation parameter value.
	 */
	public BB2(float c) {
		this();
		this.c = c;
	}
	
	/**
	 * Returns the name of the model.
	 * @return the name of the model
	 */
	public final String getInfo() {
		return "BB2c" + c;
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
		//float f = termFrequency / numberOfDocuments;
		return NORM
			* keyFrequency
			* (
				- Idf.log(numberOfDocuments - 1)
				- Idf.REC_LOG_2_OF_E
				+ stirlingPower(
					numberOfDocuments
						+ termFrequency
						- 1f,
					numberOfDocuments
						+ termFrequency
						- TF
						- 2f)
				- stirlingPower(termFrequency, termFrequency - TF));
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
		float TF =(float)
			tf * Idf.log(1.0f + (c * averageDocumentLength) / docLength);
		float NORM = (termFrequency + 1f) / (documentFrequency * (TF + 1f));
		//float f = termFrequency / numberOfDocuments;
		return NORM
			* keyFrequency
			* (
				- Idf.log(numberOfDocuments - 1)
				- Idf.REC_LOG_2_OF_E
				+ stirlingPower(
					numberOfDocuments
						+ termFrequency
						- 1f,
					numberOfDocuments
						+ termFrequency
						- TF
						- 2f)
				- stirlingPower(termFrequency, termFrequency - TF));
	}
	
}
