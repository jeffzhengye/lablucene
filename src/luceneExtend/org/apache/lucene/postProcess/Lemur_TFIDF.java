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