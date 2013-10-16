
package org.apache.lucene.postProcess;

import org.apache.lucene.search.model.Idf;


/** 
 * This class implements the Bo1 model for query expansion. 
 * See G. Amati's Phd Thesis.
 */
public class Bo1 extends QueryExpansionModel {
	/** A default constructor.*/
	public Bo1() {
		super();
	}
	/**
	 * Returns the name of the model.
	 * @return the name of the model
	 */
	public final String getInfo() {
		if (PARAMETER_FREE)
			return "Bo1bfree";
		return "Bo1b"+ROCCHIO_BETA;
	}
	
	/**
     * This method computes the normaliser of parameter-free query expansion.
     * @return The normaliser.
     */
	public final float parameterFreeNormaliser(){
		float numberOfDocuments =
			collectionLength / averageDocumentLength;
		float f = maxTermFrequency/numberOfDocuments; 
		return (float) ((maxTermFrequency* Math.log( (1d +f)/ f) + Math.log(1d +f))/ Math.log( 2d));
	}
	
	/**
     * This method computes the normaliser of parameter-free query expansion.
     * @param maxTermFrequency The maximum of the term frequency of the query terms.
     * @param collectionLength The number of tokens in the collections.
     * @param totalDocumentLength The sum of the length of the top-ranked documents.
     * @return The normaliser.
     */
	public final float parameterFreeNormaliser(float maxTermFrequency, float collectionLength, float totalDocumentLength){
		float numberOfDocuments =
			collectionLength / averageDocumentLength;
		float f = maxTermFrequency/numberOfDocuments; 
		return (float) ((maxTermFrequency* Math.log( (1d +f)/ f) + Math.log(1d +f))/ Math.log( 2d));
	}
	/** This method implements the query expansion model.
	 *  @param withinDocumentFrequency float The term frequency 
	 *         in the X top-retrieved documents.
	 *  @param termFrequency float The term frequency in the collection.
	 *  @return float The query expansion weight using the Bose-Einstein dsitribution where the mean is given by
	 *  the Poisson model.
	 */
	public final float score(
		float withinDocumentFrequency,
		float termFrequency, 
		float df) {
		//float numberOfDocuments =
			//collectionLength / averageDocumentLength;
		float f = termFrequency / numberOfDocuments;
		return (float) (withinDocumentFrequency * Idf.log((1f + f) / f)
			+ Idf.log(1f + f));
	}
	/**
	 * This method implements the query expansion model.
	 * @param withinDocumentFrequency float The term frequency 
	 *        in the X top-retrieved documents.
	 * @param termFrequency float The term frequency in the collection.
	 * @param totalDocumentLength float The sum of length of 
	 *        the X top-retrieved documents.
	 * @param collectionLength float The number of tokens in the whole collection.
	 * @param averageDocumentLength float The average document 
	 *        length in the collection.
	 * @return float The score returned by the implemented model.
	 */
	public final float score(
		float withinDocumentFrequency,
		float termFrequency,
		float totalDocumentLength,
		float collectionLength,
		float averageDocumentLength,
		float df) {
		//float numberOfDocuments =
			//collectionLength / averageDocumentLength;
		float f = termFrequency / numberOfDocuments;
		return (float) (withinDocumentFrequency * Idf.log((1f + f) / f)
			+ Idf.log(1f + f));
	}
}
