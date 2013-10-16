package org.apache.lucene.search.model;

import org.dutir.lucene.util.ApplicationSetup;
import org.dutir.lucene.util.Rounding;
import org.dutir.util.Math;

/**
 * @author Zheng Ye E-mail: yezh0716 {at} gmail.com
 * @version create ：2009-7-7 下午11:51:45 Fun:
 */
public class JelinekMercer_LM extends WeightingModel {

	static float lambda = Float.parseFloat(ApplicationSetup.getProperty(
			"JelinekMercer_LM.lambda", "0.15"));

	public JelinekMercer_LM() {
		super();
	}

	@Override
	public String getInfo() {
		return "JelinekMercer_LM_lambda=" + Rounding.format(lambda, 2);
	}

	@Override
	public float score(float tf, float docLength) {
		float docLevel = tf / docLength;
		float colLevel = termFrequency / numberOfTokens;
		return keyFrequency
				* Idf.log((1 - lambda) * docLevel + lambda * colLevel);
	}

	@Override
	public float score(float tf, float docLength, float n_t, float F_t,
			float keyFrequency) {
		return (float) (keyFrequency * Idf.log((1 - lambda) * tf / docLength
				+ lambda * F_t / (n_t)));
	}

	// float preCompute = 0;
	// boolean preTag = false;
	//	
	// public float unseenScore(float docLength){
	// if(preTag == false){
	// preCompute = keyFrequency * Math.log( lambda* termFrequency
	// /numberOfTokens );
	// preTag = true;
	// }
	// return preCompute;
	// }

}
