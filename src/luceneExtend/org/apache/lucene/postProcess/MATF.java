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

import javax.print.attribute.standard.PresentationDirection;

import org.apache.log4j.Logger;
import org.apache.lucene.search.model.Idf;
import org.apache.lucene.util.SmallFloat;
import org.dutir.lucene.util.ATFCache;
import org.dutir.lucene.util.ApplicationSetup;
import org.apache.commons.math3.util.Precision;

import redis.clients.jedis.Jedis;

public class MATF extends QueryExpansionModel {
	
	static byte cache[] = null;
	protected float ktf = -1f;
	static float lambda1 = Float.parseFloat(ApplicationSetup.getProperty("MATF.lambda1", "1.0"));
	static float lambda2 = Float.parseFloat(ApplicationSetup.getProperty("MATF.lambda2", "0.5"));
	static float lambda3 = Float.parseFloat(ApplicationSetup.getProperty("MATF.lambda3", "0"));
	static boolean pure_matf = Boolean.parseBoolean(ApplicationSetup.getProperty("MATF.purematf", "true"));
	static Logger logger = Logger.getLogger(MATF.class);
	
	
	public float getKtf() {
		return ktf;
	}


	public void setKtf(float ktf) {
		this.ktf = ktf;
	}


	@Override
	public String getInfo() {
		return "MATF"+ ROCCHIO_BETA + ((ktf == -1f || pure_matf) && lambda3!=0f? "": "lambda="+Precision.round(lambda1, 2) +":"+Precision.round(lambda2, 2) +":" + Precision.round(lambda3, 2));
	}

	
	@Override
	public float parameterFreeNormaliser() {
		throw new RuntimeException("MATF feedback error");
	}

	@Override
	public float parameterFreeNormaliser(float maxTermFrequency,
			float collectionLength, float totalDocumentLength) {
		throw new RuntimeException("MATF feedback error");
	}

	@Override
	public float score(float withinDocumentFrequency, float termFrequency, float df) {
		float AEF = termFrequency/df;
		
		float IDF = Idf.log((numberOfDocuments + 1)/df) * AEF/(1 + AEF);
		
//		float QLF = ApplicationSetup.EXPANSION_TERMS; // or we should test original query length??
		
		float QLF = this.originalQueryLength;
		
		float alpha = 2 / (1 + Idf.log(1 + QLF));
		
		float RITF = Idf.log(1 + withinDocumentFrequency)/Idf.log(1 + AVF);
		float LRTF = withinDocumentFrequency * Idf.log(1 + averageDocumentLength/totalDocumentLength);
		float BRITF = RITF/ (1 + RITF);
		float BLRTF = LRTF / (1 + LRTF);
		if(pure_matf){
			lambda1 = alpha;
			lambda2 = 1 - alpha;
		}
		float TFF = lambda1 * BRITF + lambda2 * BLRTF;
		if(ktf > 0 ){ //&& !pure_matf is removed, set lambda3=0 for replacement
			float log_ktf = Idf.log(1 + ktf);
			TFF = TFF  +  log_ktf/(1+log_ktf) * lambda3;
		}
//		logger.info(BRITF +":"+ BLRTF + ":" + ktf + ":" + TFF);
	    return TFF * IDF;
	}

	@Override
	public float score(float withinDocumentFrequency, float termFrequency,
			float totalDocumentLength, float collectionLength,
			float averageDocumentLength, float df) {
		
		this.totalDocumentLength = totalDocumentLength;
		this.collectionLength = collectionLength;
		this.averageDocumentLength = averageDocumentLength;
		return score(withinDocumentFrequency, termFrequency, df);
		
		
//		float AEF = termFrequency/df;
//		
//		float IDF = Idf.log((numberOfDocuments + 1)/df) * AEF/(1 + AEF);
//		
////		float QLF = ApplicationSetup.EXPANSION_TERMS; // or we should test original query length??
//		float QLF = this.originalQueryLength;
//		
//		float alpha = 2 / (1 + Idf.log(1 + QLF));
//		
//		float RITF = Idf.log(1 + withinDocumentFrequency)/Idf.log(1 + AVF);
//		float LRTF = withinDocumentFrequency * Idf.log(1 + averageDocumentLength/totalDocumentLength);
//		float BRITF = RITF/ (1 + RITF);
//		float BLRTF = LRTF / (1 + LRTF);
//		
//		float TFF = alpha * BRITF + (1 - alpha) * BLRTF;
//		if(ktf >= 0){
//			TFF += ktf/(1+ktf) * lambda;
//		}
//	    return TFF * IDF;
	}
	
	
	static Jedis jedis = new Jedis("127.0.0.1", 6379, 100000); // zheng's
	static String field = ApplicationSetup.getProperty(	
			"Lucene.QueryExpansion.FieldName", "content");
	static private String torediskey(int t) {
		int pos = ApplicationSetup.LUCENE_ETC.lastIndexOf("/");
		if(pos > -1){
			return ApplicationSetup.LUCENE_ETC.substring(pos + 1) + "|" + t;
		}
		return ApplicationSetup.LUCENE_ETC + "|" + t;
	}
	
	private float AvgTF(float docLength, int innerid) {
		if(cache != null){
			return SmallFloat.byte315ToFloat(cache[innerid]);
		}else{
			cache = ATFCache.init((int)this.numberOfDocuments);
		}
		String key = torediskey(innerid);
		String value = jedis.get(key);
		if (value != null)
			return Float.parseFloat(value);
		throw new RuntimeException("run time error");
	}
	
	public static void main(String args[]){
        //断言1结果为true，则继续往下执行
        assert true;
        System.out.println("断言1没有问题，Go！");
 
        System.out.println("\n-----------------\n");
 
        //断言2结果为false,程序终止
//        assert false : "断言失败，此表达式的信息将会在抛出异常的时候输出！";
        
        System.out.println("断言2没有问题，Go！" + Precision.round(131.09523f, 2));
	}


}
