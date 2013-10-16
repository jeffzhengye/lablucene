
package org.apache.lucene.postProcess;

import org.apache.lucene.search.model.Idf;

/**
 * This class implements the Kullback-Leibler divergence for query expansion.
 * See G. Amati's PhD Thesis.
 */
public class KL extends QueryExpansionModel {
	/** A default constructor. */
	public KL() {
		super();
	}

	/**
	 * Returns the name of the model.
	 * 
	 * @return the name of the model
	 */
	public final String getInfo() {
		if (PARAMETER_FREE)
			return "KLbfree";
		return "KLb" + ROCCHIO_BETA;
	}

	/**
	 * This method computes the normaliser of parameter-free query expansion.
	 * 
	 * @return The normaliser.
	 */
	public final float parameterFreeNormaliser() {
		return (float) ((maxTermFrequency)
				* Math.log(collectionLength / totalDocumentLength) / (Math
				.log(2d) * totalDocumentLength));
		// return maxTermFrequency *
		// idf.log(collectionLength/totalDocumentLength)/ idf.log
		// (totalDocumentLength);
	}

	/**
	 * This method computes the normaliser of parameter-free query expansion.
	 * 
	 * @param maxTermFrequency
	 *            The maximum of the term frequency of the query terms.
	 * @param collectionLength
	 *            The number of tokens in the collections.
	 * @param totalDocumentLength
	 *            The sum of the length of the top-ranked documents.
	 * @return The normaliser.
	 */
	public final float parameterFreeNormaliser(float maxTermFrequency,
			float collectionLength, float totalDocumentLength) {
		return (float) ((maxTermFrequency)
				* Math.log(collectionLength / totalDocumentLength) / (Math
				.log(2d) * totalDocumentLength));
	}

	/**
	 * This method implements the query expansion model.
	 * 
	 * @param withinDocumentFrequency
	 *            float The term frequency in the X top-retrieved documents.
	 * @param termFrequency
	 *            float The term frequency in the collection.
	 * @return float The query expansion weight using he complete
	 *         Kullback-Leibler divergence.
	 */
	public final float score(float withinDocumentFrequency, float termFrequency, float df) {
		float docLevel = withinDocumentFrequency / this.totalDocumentLength;
		float colLevel = termFrequency / this.collectionLength;
		if (docLevel < colLevel)
			return 0;
		else
			return (float) (docLevel * Idf.log(docLevel, colLevel));
	}

	/**
	 * This method implements the query expansion model.
	 * 
	 * @param withinDocumentFrequency
	 *            float The term frequency in the X top-retrieved documents.
	 * @param termFrequency
	 *            float The term frequency in the collection.
	 * @param totalDocumentLength
	 *            float The sum of length of the X top-retrieved documents.
	 * @param collectionLength
	 *            float The number of tokens in the whole collection.
	 * @param averageDocumentLength
	 *            float The average document length in the collection.
	 * @return float The score returned by the implemented model.
	 */
	public final float score(float withinDocumentFrequency,
			float termFrequency, float totalDocumentLength,
			float collectionLength, float averageDocumentLength
			,float df) {
		if (withinDocumentFrequency / totalDocumentLength < termFrequency
				/ collectionLength)
			return 0;
		else
			return (float) (withinDocumentFrequency / totalDocumentLength * Idf
					.log(withinDocumentFrequency / totalDocumentLength,
							termFrequency / collectionLength));
	}
}
