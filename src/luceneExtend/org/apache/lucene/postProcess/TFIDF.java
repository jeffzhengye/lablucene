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

public class TFIDF extends QueryExpansionModel {

	@Override
	public String getInfo() {
		// TODO Auto-generated method stub
		return "TFIDF"+ ROCCHIO_BETA;
	}

	@Override
	public float parameterFreeNormaliser() {
		throw new RuntimeException("TFIDF feedback error");
	}

	@Override
	public float parameterFreeNormaliser(float maxTermFrequency,
			float collectionLength, float totalDocumentLength) {
		throw new RuntimeException("TFIDF feedback error");
	}

	@Override
	public float score(float withinDocumentFrequency, float termFrequency, float df) {
//		return (withinDocumentFrequency/this.totalDocumentLength)* Idf.log(this.numberOfDocuments/ df);
		return (withinDocumentFrequency)* Idf.log(this.numberOfDocuments/ df);
	}

	@Override
	public float score(float withinDocumentFrequency, float termFrequency,
			float totalDocumentLength, float collectionLength,
			float averageDocumentLength, float df) {
		
//		return (withinDocumentFrequency/this.totalDocumentLength) * Idf.log(this.numberOfDocuments/ df);
		return (withinDocumentFrequency) * Idf.log(this.numberOfDocuments/ df);
	}

}
