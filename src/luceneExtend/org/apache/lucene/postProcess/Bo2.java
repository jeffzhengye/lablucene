/*
 * Terrier - Terabyte Retriever
 * Webpage: http://terrier.org
 * Contact: terrier{a.}dcs.gla.ac.uk
 * University of Glasgow - School of Computing Science
 * http://www.ac.gla.uk
 *
 * The contents of this file are subject to the Mozilla Public License
 * Version 1.1 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
 * the LiCense for the specific language governing rights and limitations
 * under the License.
 *
 * The Original Code is TRECQuerying.java.
 *
 * The Original Code is Copyright (C) 2004-2011 the University of Glasgow.
 * All Rights Reserved.
 *
*/

/*
* This file is probably based on a class with the same name from Terrier, 
* so we keep the copyright head here. If you have any question, please notify me first.
* Thanks. 
*/
package org.apache.lucene.postProcess;

import org.apache.lucene.search.model.Idf;


/** 
 * This class implements the Bo2 model for query expansion. 
 * See G. Amati's Phd Thesis.
 */
public class Bo2 extends QueryExpansionModel {
	/** A default constructor.*/
	public Bo2() {
		super();
	}
	/**
	 * Returns the name of the model.
	 * @return the name of the model
	 */
	public final String getInfo() {
		if (PARAMETER_FREE)
			return "Bo2bfree";
		return "Bo2b"+ROCCHIO_BETA;
	}
	
	/**
     * This method computes the normaliser of parameter-free query expansion.
     * @return The normaliser.
     */
	public final float parameterFreeNormaliser(){
		float f =  (maxTermFrequency) * totalDocumentLength/collectionLength;
		return (float) ( ((maxTermFrequency)*  Idf.log((1f +f)/ f) +  Idf.log(1f +f)) );
	}
	/**
     * This method computes the normaliser of parameter-free query expansion.
     * @param maxTermFrequency The maximum of the term frequency of the query terms.
     * @param collectionLength The number of tokens in the collections.
     * @param totalDocumentLength The sum of the length of the top-ranked documents.
     * @return The normaliser.
     */
	public final float parameterFreeNormaliser(float maxTermFrequency, float collectionLength, float totalDocumentLength){
		float f =  (maxTermFrequency) * totalDocumentLength/collectionLength;
		return (float) ( ((maxTermFrequency)*  Idf.log((1f +f)/ f) +  Idf.log(1f +f)) );
	}
	/** This method implements the query expansion model.
	 *  @param withinDocumentFrequency float The term frequency 
	 *         in the X top-retrieved documents.
	 *  @param termFrequency float The term frequency in the collection.
	 *  @return float The query expansion weight using the Bose-Einstein statistics
	 *  where the mean is given by the Bernoulli process.
	 */
	public final float score(
		float withinDocumentFrequency,
		float termFrequency, 
		float df) {
		float f =
			withinDocumentFrequency
				* totalDocumentLength
				/ collectionLength;
		return (float) ( withinDocumentFrequency * Idf.log((1f + f) / f)
			+ Idf.log(1f + f) );
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
		float f =
			withinDocumentFrequency
				* totalDocumentLength
				/ collectionLength;
		return (float) (withinDocumentFrequency * Idf.log((1f + f) / f)
			+ Idf.log(1f + f) );
	}
}
