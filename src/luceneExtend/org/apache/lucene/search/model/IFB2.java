
package org.apache.lucene.search.model;
/**
 * This class implements the IFB2 weighting model.
 */
public class IFB2 extends WeightingModel {
	/** 
	 * A default constructor. This must be followed by 
	 * specifying the c value.
	 */
	public IFB2() {
		super();
		this.c=1.0f;
	}
	
	/** 
	 * Constructs an instance of this class with 
	 * the specified value for the parameter c.
	 * @param c the term frequency normalisation parameter value.
	 */
	public IFB2(float c) {
		this();
		this.c = c;
	}
	/**
	 * Returns the name of the model.
	 * @return the name of the model
	 */
	public final String getInfo() {
		return "IFB2c" + c;
	}
	/**
	 * Uses IFB2 to compute a weight for a term in a document.
	 * @param tf The term frequency in the document
	 * @param docLength the document's length
	 * @return the score assigned to a document with the given 
	 *         tf and docLength, and other preset parameters
	 */
	public final float score(float tf, float docLength) {
		float TF =
			tf * Idf.log(1.0f + (c * averageDocumentLength) / docLength);
		float NORM = (termFrequency + 1f) / (documentFrequency * (TF + 1f));
		//float f = termFrequency / numberOfDocuments;
		return TF * keyFrequency * i.idfDFR(termFrequency) * NORM;
	}
	/**
	 * Uses IFB2 to compute a weight for a term in a document.
	 * @param tf The term frequency in the document
	 * @param docLength the document's length
	 * @param n_t The document frequency of the term
	 * @param F_t the term frequency in the collection
	 * @param keyFrequency the term frequency in the query
	 * @return the score assigned by the weighting model IFB2.
	 */
	public final float score(
		float tf,
		float docLength,
		float n_t,
		float F_t,
		float keyFrequency) {
		float TF =
			tf * Idf.log(1.0f + (c * averageDocumentLength) / docLength);
		float NORM = (termFrequency + 1f) / (documentFrequency * (TF + 1f));
		//float f = termFrequency / numberOfDocuments;
		return TF * keyFrequency * i.idfDFR(termFrequency) * NORM;
	}
	
}
