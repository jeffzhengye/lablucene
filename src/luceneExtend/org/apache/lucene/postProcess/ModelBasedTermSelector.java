/**
 * 
 */
package org.apache.lucene.postProcess;

import org.dutir.lucene.util.ApplicationSetup;

/**
 * Modelbased Feedback with Maximal likelihood estimation -- Zhai, CIKM2001
 * @author zheng
 *
 */
public class ModelBasedTermSelector extends QueryExpansionModel {

	private float lambda = Float.parseFloat(ApplicationSetup.getProperty(
			"ModelBased.lambda", "0.5"));
	/* (non-Javadoc)
	 * @see org.apache.lucene.postProcess.termselector.TermSelector#getInfo()
	 */
	@Override
	public String getInfo() {
		String free = PARAMETER_FREE ? "Free" : "notFree";
		return "Modelbased_lambda=" + lambda + "alpha=" + LM_ALPHA + free;
	}


	@Override
	public float parameterFreeNormaliser() {
		return 1;
	}

	@Override
	public float parameterFreeNormaliser(float maxTermFrequency,
			float collectionLength, float totalDocumentLength) {
		return 1;
	}

	@Override
	public float score(float withinDocumentFrequency, float termFrequency, float df) {
		float weight = ( withinDocumentFrequency / totalDocumentLength 
	       - lambda * termFrequency /collectionLength) / (1 - lambda); 
		return weight > 0.001 ? weight: 0;
	}
	
	@Override
	public float score(float withinDocumentFrequency, float termFrequency,
			float totalDocumentLength, float collectionLength,
			float averageDocumentLength, float df) {
		float weight = ( withinDocumentFrequency / totalDocumentLength 
			       - lambda * termFrequency /collectionLength) / lambda; 
		return weight > 0.001 ? weight: 0;
	}
//	
//	public float divergence_minimaze(){
//		
//	}
	
}
