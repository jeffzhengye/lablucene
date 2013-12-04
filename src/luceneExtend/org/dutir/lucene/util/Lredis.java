package org.dutir.lucene.util;

import java.io.IOException;

import org.apache.lucene.index.TermFreqVector;
import org.apache.lucene.util.SmallFloat;

import redis.clients.jedis.Jedis;

/**
 * Redis client operator adapted for the use in LabLucene
 * @author zheng
 *
 */
public class Lredis {
//	static Jedis jedis = new Jedis("127.0.0.1", 6379, 100000); // zheng's
//	static String field = ApplicationSetup.getProperty(	
//			"Lucene.QueryExpansion.FieldName", "content");
	Jedis jedis = null;
	static Lredis lredis = null;
	private Lredis(){
		jedis = new Jedis("127.0.0.1", 6379, 100000); // connected to the default Redis server zheng's
	}
	
	public static Lredis getDefault(){
		if(lredis == null){
			lredis = new Lredis();
		}
		return lredis;
	}
	
	public boolean has(String key, boolean colBased){
		if(colBased){
			key = torediskey(key);
		}
		return jedis.exists(key);
	}
	
	public String get(String key, boolean colBased){
		if(colBased){
			key = torediskey(key);
		}
		return jedis.get(key);
	}
	
//	public String getColBased(String key){
//		return jedis.get(torediskey(key));
//	}
	
	public long put(String key, String value, boolean colBased){
		if(colBased){
			key = torediskey(key);
		}
		return jedis.append(key, value);
	}
	
//	public long putColBased(String key, String value){
//		return jedis.append(torediskey(key), value);
//	}
	
	static private String torediskey(String t) {
		int pos = ApplicationSetup.LUCENE_ETC.lastIndexOf("/");
		if(pos > -1){
			return ApplicationSetup.LUCENE_ETC.substring(pos + 1) + "|" + t;
		}
		return ApplicationSetup.LUCENE_ETC + "|" + t;
	}
	
//	private float AvgTF(float docLength, int innerid) {
//		if(cache != null){
//			return SmallFloat.byte315ToFloat(cache[innerid]);
//		}else{
////			cache = ATFCache.init(searcher);
//			ATFCache.initAll(searcher);
//			cache = ATFCache.cache;
//			norm = ATFCache.norm;
//		}
//		String key = torediskey(innerid);
//		String value = jedis.get(key);
//		if (value != null)
//			return Float.parseFloat(value);
//		
//		TermFreqVector tfv;
//		try {
//			tfv = this.searcher.getIndexReader().getTermFreqVector(
//					innerid, field);
//			if (tfv == null)
//				throw new RuntimeException("run time error");
//			else {
//				String strterms[] = tfv.getTerms();
//				float atf = docLength/strterms.length;
//				jedis.set(key, Float.toString(atf));
//				return atf;
//			}
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//		throw new RuntimeException("run time error");
//	}

}
