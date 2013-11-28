
package org.apache.lucene.search.model;

import org.dutir.lucene.util.ApplicationSetup;

/**
 *
 *According to Zhai
 */
public class Hiemstra_LM extends WeightingModel {
	/**
	 * A default constructor. Uses the default value of lambda=0.15.
	 */

	public Hiemstra_LM() {
		super();
		this.c = Float.parseFloat(ApplicationSetup.getProperty("wm.c", "0.15"));

	}

	/**
	 * Constructs an instance of this class with the specified value for the
	 * parameter lambda.
	 * 
	 * @param lambda
	 *            the smoothing parameter.
	 */
	public Hiemstra_LM(float lambda) {
		this();
		this.c = lambda;
	}

	/**
	 * Returns the name of the model.
	 * 
	 * @return the name of the model
	 */

	public final String getInfo() {
		return "Hiemstra_LM" + c;
	}

	/**
	 * Uses Hiemestra_LM to compute a weight for a term in a document.
	 * 
	 * @param tf
	 *            The term frequency in the document
	 * @param docLength
	 *            the document's length
	 * @return the score assigned to a document with the given tf and docLength,
	 *         and other preset parameters
	 */
	public final float score(float tf, float docLength) {

		return keyFrequency * Idf.log(1 + ((1-c) * tf * numberOfTokens)
				/ (c * termFrequency * docLength)) 
				+ keyFrequency * Idf.log(c * termFrequency / numberOfTokens);
		 
	}

	/**
	 * Uses Hiemstra_LM to compute a weight for a term in a document.
	 * 
	 * @param tf
	 *            The term frequency in the document
	 * @param docLength
	 *            the document's length
	 * @param n_t
	 *            The document frequency of the term
	 * @param F_t
	 *            the term frequency in the collection
	 * @param keyFrequency
	 *            the term frequency in the query
	 * @return the score assigned by the weighting model Hiemstra_LM.
	 */
	public final float score(float tf, float docLength, float n_t, float F_t,
			float keyFrequency) {

		return

		keyFrequency *  Idf.log(1 + (1- c) * tf * numberOfTokens / (c * F_t * docLength))
		+keyFrequency * Idf.log(c * termFrequency / numberOfTokens);
		/**
		 * Idf.log(((1 - c) * (tf / docLength)) / (c * (F_t / numberOfTokens)) +
		 * 1 ) + Idf.log(c* (F_t / numberOfTokens));
		 */

	}
	
	public float unseeScore(int dl){
		return score(0, dl);
	}
	
}
