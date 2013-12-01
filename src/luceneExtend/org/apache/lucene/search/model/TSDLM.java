package org.apache.lucene.search.model;

import java.io.IOException;

import org.apache.lucene.index.TermFreqVector;
import org.apache.lucene.util.SmallFloat;
import org.dutir.lucene.util.ATFCache;
import org.dutir.lucene.util.ApplicationSetup;
/**
 * This class implements the Two-stage LM weighting model.
 * @author Zheng Ye
 * @version $Revision: 1.0 $
 */
public class TSDLM extends WeightingModel {
	/** 
	 * A default constructor. Uses the default value of mu=1000.
	 */
	static float mu = Float.parseFloat(ApplicationSetup.getProperty("tsdlm.mu", ApplicationSetup.getProperty("dlm.mu", "1000")));
	static float lambda = Float.parseFloat(ApplicationSetup.getProperty("tsdlm.lambda", "0.9"));
	public TSDLM() {
		super();
	}
	
	/** 
	 * Constructs an instance of this class with the 
	 * specified value for the parameter lambda.
	 * @param lambda the smoothing parameter.
	 */
	public TSDLM(double mu) {
		this();
	}
	
	/**
	 * @return the name of the model
	 */
	
	public final String getInfo(){
		return "TSDLM" + (int)mu +"lambda" + lambda;
	}

	@Override
	public float score(float tf, float docLength, int innerid) {
//		float docLevel = tf /docLength;
		float RITF = Idf.log(1 + tf)/Idf.log(1 + AvgTF(docLength, innerid));	
//		float pRITF = Idf.log((numberOfDocuments + 1f)/(documentFrequency)) * RITF/SmallFloat.byte315ToFloat(norm[innerid]) * docLength/AvgTF(docLength, innerid);
		float pRITF = RITF/(1+RITF);
		lambda = pRITF;
		return keyFrequency * log( lambda *(tf + mu * termFrequency / numberOfTokens)/ (docLength + mu) - (1-lambda)*termFrequency / numberOfTokens);
	}

	@Override
	public float score(float tf, float docLength, float n_t, float F_t,
			float keyFrequency, int innerid) {
		this.keyFrequency = keyFrequency;
		this.documentFrequency = n_t;
		this.termFrequency = F_t;
		return score(tf, docLength, innerid);
	}
	
	
	@Override
	public float score(float tf, float docLength) {
		throw new RuntimeException("should not use this method");
	}

	@Override
	public float score(float tf, float docLength, float n_t, float F_t,
			float keyFrequency) {
		throw new RuntimeException("should not use this method");
	}
	
	private float log(float score){
		return Idf.log(score);
	}
	
	static byte cache[] = null;
	static byte norm[] = null;
	static String field = ApplicationSetup.getProperty(	
			"Lucene.QueryExpansion.FieldName", "content");
	private float AvgTF(float docLength, int innerid) {
		if(cache != null){
			return SmallFloat.byte315ToFloat(cache[innerid]);
		}else{
			ATFCache.initAll(searcher);
			cache = ATFCache.cache;
			norm = ATFCache.norm;
		}
		
		TermFreqVector tfv;
		try {
			tfv = this.searcher.getIndexReader().getTermFreqVector(
					innerid, field);
			if (tfv == null)
				throw new RuntimeException("run time error");
			else {
				String strterms[] = tfv.getTerms();
				float atf = docLength/strterms.length;
				return atf;
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		throw new RuntimeException("run time error");
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