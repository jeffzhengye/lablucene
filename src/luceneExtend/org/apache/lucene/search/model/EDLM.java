package org.apache.lucene.search.model;

import java.io.IOException;

import org.apache.log4j.Logger;
import org.apache.lucene.index.TermFreqVector;
import org.apache.lucene.postProcess.QueryExpansion;
import org.apache.lucene.util.SmallFloat;
import org.dutir.lucene.util.ATFCache;
import org.dutir.lucene.util.ApplicationSetup;
/**
 * This class implements the Dirichlet LM weighting model.
 * @author Zheng Ye
 * @version $Revision: 1.0 $
 */
public class EDLM extends WeightingModel {
	/** 
	 * A default constructor. Uses the default value of mu=1000.
	 */
	static float mu = Integer.parseInt(ApplicationSetup.getProperty("dlm.mu", "1000"));
	static float alpha = Float.parseFloat(ApplicationSetup.getProperty("edlm.alpha", "0.3"));
	static Logger logger = Logger.getLogger(EDLM.class);
	public EDLM() {
		super();
	}
	
	/** 
	 * Constructs an instance of this class with the 
	 * specified value for the parameter lambda.
	 * @param lambda the smoothing parameter.
	 */
	public EDLM(double mu) {
		this();
	}
	
	/**
	 * @return the name of the model
	 */
	
	public final String getInfo(){
		return "EDLM" + (int)mu + "alpha=" + alpha;
	}

	@Override
	public float score(float tf, float docLength) {
		throw new RuntimeException("should not use this method");
	}
	
	public final float score(float tf, float docLength, int innerid) {
//		alpha = 2 / (1 + Idf.log(1 + querylength));
		float RITF = Idf.log(1 + tf)/Idf.log(1 + AvgTF(docLength, innerid));	
		float pRITF = Idf.log((numberOfDocuments  + 1f)/(documentFrequency) ) * RITF/SmallFloat.byte315ToFloat(norm[innerid]);
//		pRITF = RITF/(1+RITF);
		float pterm = (tf + mu * termFrequency / numberOfTokens)/ (docLength + mu);
		float partA = 0;
		if(tf > 0f){
			partA = alpha *log( pRITF);
		}
		if(partA != 0f){
			logger.warn(alpha + ":" + pRITF + ":" + log( pRITF));
		}
		float retvalue = keyFrequency * ( partA + (1-alpha)* log(pterm) );
		return retvalue;
	}

	@Override
	public float score(float tf, float docLength, float n_t, float F_t,
			float keyFrequency) {
		throw new RuntimeException("should not use this method");
	}
	
	public float score(float tf, float docLength, float n_t, float F_t,
			float keyFrequency, int innerid) {
//		alpha = 2 / (1 + Idf.log(1 + querylength));
		float RITF = Idf.log(1 + tf)/Idf.log(1 + AvgTF(docLength, innerid));
		float pRITF = Idf.log((numberOfDocuments + 1f)/(n_t)) * RITF/SmallFloat.byte315ToFloat(norm[innerid]);
//		pRITF = RITF/(1+RITF);
		float pterm = (tf + mu * F_t / n_t)/ (docLength + mu);
		
		float partA = 0f;
		if(tf > 0f){
			partA = alpha *log( pRITF);
		}
		if(partA != 0f){
			logger.warn(alpha + ":" + pRITF + ":" + log( pRITF));
		}
		float retvalue = keyFrequency * ( partA + (1-alpha)* log(pterm) );
		return retvalue;
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
	
}
