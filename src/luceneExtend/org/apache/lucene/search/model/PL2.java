
package org.apache.lucene.search.model;
/**
 * This class implements the PL2 weighting model.
 */
public class PL2 extends WeightingModel {
	/** 
	 * A default constructor. This must be followed 
	 * by specifying the c value.
	 */
	public PL2() {
		super();
	}
	/** 
	 * Constructs an instance of this class with the 
	 * specified value for the parameter c.
	 * @param c the term frequency normalisation parameter value.
	 */
	public PL2(float c) {
		this();
		this.c = c;
	}
	/**
	 * Returns the name of the model.
	 * @return the name of the model
	 */
	public final String getInfo() {
		return "PL2c" + c;
	}
	/**
	 * Uses PL2 to compute a weight for a term in a document.
	 * @param tf The term frequency in the document
	 * @param docLength the document's length
	 * @return the score assigned to a document with the given 
	 *         tf and docLength, and other preset parameters
	 */
	public final float score(float tf, float docLength) {
		float tfn =
			tf * Idf.log(1.0f + (c * averageDocumentLength) / docLength);
		float NORM = 1.0f / (tfn + 1f);
		float f = (1.0f * termFrequency) / (1.0f * numberOfDocuments);
		float value = NORM
			* keyFrequency
			* (tfn * Idf.log(1.0f / f)
				+ f * Idf.REC_LOG_2_OF_E
				+ 0.5f * Idf.log((float) (2 * Math.PI * tfn))
				+ tfn * (Idf.log(tfn) - Idf.REC_LOG_2_OF_E));
//		if(value < 0) System.out.println("" + tfn +", " + NORM +", " + f +" = " + value);
//		return value > 0 ? value: 0;
		return value;
	}
	
	public float unseenScore(float docLength){
 		return 0;
	}
	/**
	 * Uses PL2 to compute a weight for a term in a document.
	 * @param tf The term frequency in the document
	 * @param docLength the document's length
	 * @param n_t The document frequency of the term
	 * @param F_t the term frequency in the collection
	 * @param keyFrequency the term frequency in the query
	 * @return the score assigned by the weighting model PL2.
	 */
	public final float score(
		float tf,
		float docLength,
		float n_t,
		float F_t,
		float keyFrequency) {
		float TF =
			tf * Idf.log(1.0f + (c * averageDocumentLength) / docLength);
		float NORM = 1.0f / (TF + 1f);
		float f = F_t / numberOfDocuments;
		return NORM
			* keyFrequency
			* (TF * Idf.log(1f / f)
				+ f * Idf.REC_LOG_2_OF_E
				+ 0.5f * Idf.log((float) (2 * Math.PI * TF))
				+ TF * (Idf.log(TF) - Idf.REC_LOG_2_OF_E));
	}
}
