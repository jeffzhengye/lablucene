package org.apache.lucene.search.model;

import java.io.IOException;

import org.apache.log4j.Logger;
import org.apache.lucene.index.TermDocs;
import org.apache.lucene.index.TermFreqVector;
import org.apache.lucene.util.SmallFloat;
import org.dutir.lucene.util.ATFCache;
import org.dutir.lucene.util.ApplicationSetup;
import org.dutir.lucene.util.Lredis;
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
	static float mu1 = Float.parseFloat(ApplicationSetup.getProperty("tsdlm.mu1", "3000"));
	static float lambda = Float.parseFloat(ApplicationSetup.getProperty("tsdlm.lambda", "0.9"));
	static Logger logger = Logger.getLogger(TSDLM.class);
	float cRITF = 0f;
	static float clen = 0f;
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
		float _cRITF = getCRITF();
//		lambda = 2 / (1f + Idf.log(1 + querylength));
//		switch ((int) querylength) {
//		case (int) 1:
//			lambda = 1;
//			break;
//		case 2:
//			lambda = 2 / (1f + Idf.log(1 + querylength));
//			break;
//		case 3: 
//			lambda = 0.0005f;
//			break;
//		default:
//			lambda = 0f;
//			break;
//		}
//		logger.info("" + tf +":" + (tf + mu * termFrequency / numberOfTokens)/ (docLength + mu) + ":" + pRITF/documentFrequency);
//		return keyFrequency * log( lambda *(tf + mu * termFrequency / numberOfTokens)/ (docLength + mu) + (1-lambda)*(pRITF+0.1f)/documentFrequency);
		
//		logger.info("" + tf +":" + (tf + mu * termFrequency / numberOfTokens)/ (docLength + mu) + ":" + (RITF+0.1f)/_cRITF +":" + termFrequency / numberOfTokens);
//		return keyFrequency * log( lambda *(tf + mu * termFrequency / numberOfTokens)/ (docLength + mu) + (1-lambda)*(RITF+0.1f)/_cRITF);
		
//		return keyFrequency * log( (RITF + mu * _cRITF /colLen() )/ (docLength + mu));
		logger.info("" + tf +":" + (tf + mu * termFrequency / numberOfTokens)/ (docLength + mu) + 
				":" + (pRITF + mu1 * _cRITF /colLen() )/ (docLength + mu1) +
				":" + termFrequency / numberOfTokens + ":" + _cRITF /colLen());
		return keyFrequency * log( (1-lambda) *(tf + mu * termFrequency / numberOfTokens)/ (docLength + mu) + (lambda)*(pRITF + mu1 * _cRITF /colLen() )/ (docLen(innerid) + mu1) );
	}

	
	static Lredis lredis = Lredis.getDefault();
	private float getCRITF() {
		try {
			if(cRITF > 0){
//				logger.info("from cache cRITF");
				return cRITF;
			}
			if(lredis.has(this.query.getTerm().text(), true)){
				cRITF = Float.parseFloat(lredis.get(this.query.getTerm().text(), true)); 
				return cRITF;
			}
			TermDocs tdocs = this.searcher.getIndexReader().termDocs(this.query.getTerm());
			
			while(tdocs.next()){
				int docid = tdocs.doc();
				float RITF =  Idf.log(1 + docid)/Idf.log(1 + AvgTF(this.searcher.getFieldLength(field, docid), docid));
				RITF = RITF/(1+RITF);
				cRITF += RITF;
			}
			lredis.put(this.query.getTerm().text(), Float.toString(cRITF), true);
			logger.info("from online computing");
			return cRITF;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return 0;
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
	
	private float avlDL(float docLength, int innerid) {
		if(norm == null){
			ATFCache.initAll(searcher);
			cache = ATFCache.cache;
			norm = ATFCache.norm;
		}
		return SmallFloat.byte315ToFloat(norm[innerid]);
	}
	private float docLen(int innerid){
		if(norm == null){
			ATFCache.initAll(searcher);
			cache = ATFCache.cache;
			norm = ATFCache.norm;
		}
		return SmallFloat.byte315ToFloat(norm[innerid]);
	}
	private float colLen() {
		if(clen > 0f){
			return clen;
		}
		if(norm == null){
			ATFCache.initAll(searcher);
			cache = ATFCache.cache;
			norm = ATFCache.norm;
		}
		for(int i =0; i < norm.length; i++){
			clen += SmallFloat.byte315ToFloat(norm[i]);
		}
		return clen;
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