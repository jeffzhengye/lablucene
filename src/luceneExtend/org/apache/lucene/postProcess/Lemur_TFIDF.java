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
import org.dutir.lucene.util.ApplicationSetup;

public class Lemur_TFIDF extends QueryExpansionModel {

	/** The constant k_1.*/
	private float k_1 = 1.2f;
	
	/** The constant b.*/
//	private float b = Float.parseFloat(ApplicationSetup.getProperty("bm25.b", "0.35"));
	private float b = 0.75f;

	@Override
	public String getInfo() {
		// TODO Auto-generated method stub
		return "LemurTFIDF"+ ROCCHIO_BETA;
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
//		return withinDocumentFrequency * Idf.log(this.collectionLength/termFrequency);
		float tf = withinDocumentFrequency;
		
		float Robertson_tf = k_1*tf/(tf+k_1*(1-b+b*this.totalDocumentLength/averageDocumentLength));
		
		return (float) (Robertson_tf * Idf.log(numberOfDocuments/df));
	}

	@Override
	public float score(float withinDocumentFrequency, float termFrequency,
			float totalDocumentLength, float collectionLength,
			float averageDocumentLength, float df) {
		float tf = withinDocumentFrequency;
		
		float Robertson_tf = k_1*tf/(tf+k_1*(1-b+b*totalDocumentLength/averageDocumentLength));
		return (float) (Robertson_tf * 
				Idf.log(numberOfDocuments/df));
		
	}

}
