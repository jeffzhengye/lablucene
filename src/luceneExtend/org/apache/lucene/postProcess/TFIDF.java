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
