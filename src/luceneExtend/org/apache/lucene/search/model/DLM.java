package org.apache.lucene.search.model;

import org.dutir.lucene.util.ApplicationSetup;
/**
 * This class implements the Dirichlet LM weighting model.
 * @author Zheng Ye
 * @version $Revision: 1.0 $
 */
public class DLM extends WeightingModel {
	/** 
	 * A default constructor. Uses the default value of mu=1000.
	 */
	static float mu = Integer.parseInt(ApplicationSetup.getProperty("dlm.mu", "1000"));
	public DLM() {
		super();
	}
	
	/** 
	 * Constructs an instance of this class with the 
	 * specified value for the parameter lambda.
	 * @param lambda the smoothing parameter.
	 */
	public DLM(double mu) {
		this();
	}
	
	/**
	 * @return the name of the model
	 */
	
	public final String getInfo(){
		return "DLM" + (int)mu;
	}

	@Override
	public float score(float tf, float docLength) {
//		float docLevel = tf /docLength;
		return keyFrequency * log( (tf + mu * termFrequency / numberOfTokens)/ (docLength + mu) );
	}

	@Override
	public float score(float tf, float docLength, float n_t, float F_t,
			float keyFrequency) {
		return keyFrequency * log( (tf + mu * F_t / n_t)/ (docLength + mu) );
	}
	
	private float log(float score){
		return Idf.log(score);
//		return (float) Math.log(score);
	}
	
//	/**
//	 * it's of note that for unseen term 
//	 */
//	
//	float preCompute = 0;
//	boolean preTag = false;
//	
//	public float unseenScore(float docLength){
//		if(preTag == false){
//			preCompute =  keyFrequency * log( ( mu * termFrequency / numberOfTokens)/ (docLength + mu) );
//			preTag = true;
//		}
//		return preCompute;
//	}
	
}