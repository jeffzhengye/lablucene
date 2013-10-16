
package org.apache.lucene.search.model;

/**
 * This class implements the DFRee weighting model. DFRee stands for DFR free
 * from parameters.
 */
public class DFRee extends WeightingModel {

	/** model name */
	private static final String name = "DFRee";

	/**
	 * A default constructor to make this model.
	 */
	public DFRee() {
		super();
	}

	/**
	 * Returns the name of the model, in this case "DFRee"
	 * 
	 * @return the name of the model
	 */
	public final String getInfo() {
		return name;
	}

	/**
	 * Uses DFRee to compute a weight for a term in a document.
	 * 
	 * @param tf
	 *            The term frequency of the term in the document
	 * @param docLength
	 *            the document's length
	 * @return the score assigned to a document with the given tf and docLength,
	 *         and other preset parameters
	 */
	public final float score(float tf, float docLength) {
		/**
		 * DFRee model with the log normalisation function.
		 */
		float prior = tf / docLength;
		float posterior = (tf + 1f) / (docLength + 1);
		float InvPriorCollection = numberOfTokens / termFrequency;
		// float alpha = 1d/docLength; //0 <= alpha <= posterior

		float norm = tf * Idf.log(posterior / prior);

		return keyFrequency
				* norm
				* (tf * (-i.log(prior * InvPriorCollection)) + (tf + 1f)
						* (+i.log(posterior * InvPriorCollection)) + 0.5f * Idf
						.log(posterior / prior));
	}

	/**
	 * Uses DFRee to compute a weight for a term in a document.
	 * 
	 * @param tf
	 *            The term frequency of the term in the document
	 * @param docLength
	 *            the document's length
	 * @param documentFrequency
	 *            The document frequency of the term (ignored)
	 * @param termFrequency
	 *            the term frequency in the collection (ignored)
	 * @param keyFrequency
	 *            the term frequency in the query (ignored).
	 * @return the score assigned by the weighting model DFRee.
	 */
	public final float score(float tf, float docLength,
			float documentFrequency, float termFrequency, float keyFrequency) {
		/**
		 * DFRee model with the log normalisation function.
		 */
		float prior = tf / docLength;
		float posterior = (tf + 1f) / (docLength + 1);
		float InvPriorCollection = numberOfTokens / termFrequency;
		// float alpha = 1d/docLength; //0 <= alpha <= posterior

		float norm = tf * Idf.log(posterior / prior);

		return keyFrequency
				* norm
				* (tf * (-i.log(prior * InvPriorCollection)) + (tf + 1f)
						* (+i.log(posterior * InvPriorCollection)) + 0.5f * Idf
						.log(posterior / prior));
	}
	
	public float unseenScore(float docLength){
 		return 0;
	}
}
