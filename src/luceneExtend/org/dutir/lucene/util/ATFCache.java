package org.dutir.lucene.util;

import gnu.trove.TObjectFloatHashMap;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.log4j.Logger;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermFreqVector;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.search.model.Idf;
import org.apache.lucene.util.SmallFloat;
import org.dutir.lucene.util.TermsCache.Item;
import org.dutir.util.Arrays;

/**
 * @author zheng
 * Cache average tf in each document
 *
 */
public class ATFCache {
	static Logger logger = Logger.getLogger(ATFCache.class);
	static String field = ApplicationSetup.getProperty(	
			"Lucene.QueryExpansion.FieldName", "content");
	static boolean rebuild = Boolean.parseBoolean(ApplicationSetup.getProperty(	
			"ATFCache.rebuild", "false"));
	/**
	 * you should set a different path for every different collection.
	 */
	static String path = null;
	static String normpath = null;
	static {
		String indexPath = ApplicationSetup.getProperty(
				"Lucene.indexDirectory", ".");
		path = indexPath + "/averTF.cache";
		normpath = indexPath + "/sumRITF.cache";
	}
	public static byte[] cache = null;
	public static byte[] norm = null;
	
	static TermsCache tcache = TermsCache.getInstance();
	static TObjectFloatHashMap<String> idfCache = new TObjectFloatHashMap<String>();

	static Item getItem(String term, Searcher searcher) {
		Term lterm = new Term(field, term);
		return tcache.getItem(lterm, searcher);
	}
	
	static float getIDF(String term, float totalNumDocs, Searcher searcher){
		float relVal = 0;
		if(idfCache.containsKey(term)){
			relVal = idfCache.get(term);
		}else{
			Item item = getItem(term, searcher);
			float Nt = item.df;
			relVal = Idf.log(( totalNumDocs + 1f)/Nt);
		}
		return relVal;
	}
	
	public static byte[] init(Searcher searcher){
		if(rebuild){
			build(searcher);
		}
		if(cache != null){
			return cache;
		}
		initCache(searcher);//read from file, failure leads to cache ==null. Then proceed to build
		if (cache == null) {
			build(searcher);
		}
		return cache;
	}
	
	public static void initAll(Searcher searcher){
		if(rebuild){
			build(searcher);
		}
		if(cache != null && norm !=null){
			return;
		}
		initATF_norm(searcher);//read from file, failure leads to cache ==null. Then proceed to build
		if (cache == null || norm == null) {
			build(searcher);
		}
	}
	
	public static byte[] init(int maxdoc){
		if(cache != null){
			return cache;
		}
		try {
			File file = new File(path);
			if (file.exists()) {
				FileInputStream fis = new FileInputStream(file);
				cache = new byte[maxdoc];
				int len = fis.read(cache);
				if(len != maxdoc){
					logger.warn("inconsistant");
				}
				fis.close();
			}
		} catch (Exception e) {
			logger.warn("cannot init");
		}
		return cache;
	}
	
	public static void initATF_norm(int maxdoc){
		if(cache != null && norm != null){
			return;
		}
		try {
			File file = new File(path);
			if (file.exists()) {
				FileInputStream fis = new FileInputStream(file);
				cache = new byte[maxdoc];
				int len = fis.read(cache);
				if(len != maxdoc){
					logger.warn("inconsistant");
				}
				fis.close();
			}
			
			file = new File(path);
			if (file.exists()) {
				FileInputStream fis = new FileInputStream(file);
				norm = new byte[maxdoc];
				int len = fis.read(norm);
				if(len != maxdoc){
					logger.warn("inconsistant");
				}
				fis.close();
			}
		} catch (Exception e) {
			logger.warn("cannot init");
		}
	}
	

	private static void initCache(Searcher searcher) {
		try {
			File file = new File(path);
			if (file.exists()) {
				FileInputStream fis = new FileInputStream(file);
				int maxdoc = searcher.getIndexReader().maxDoc();
				cache = new byte[maxdoc];
				int len = fis.read(cache);
				if(len != maxdoc){
					logger.warn("inconsistant");
				}
				fis.close();
			}
		} catch (Exception e) {
			logger.warn("cannot init");
		}
	}
	
	private static void initATF_norm(Searcher searcher) {
		try {
			File file = new File(path);
			if (file.exists()) {
				FileInputStream fis = new FileInputStream(file);
				int maxdoc = searcher.getIndexReader().maxDoc();
				cache = new byte[maxdoc];
				int len = fis.read(cache);
				if(len != maxdoc){
					logger.warn("inconsistant");
				}
				fis.close();
			}
			
			file = new File(normpath);
			if (file.exists()) {
				FileInputStream fis = new FileInputStream(file);
				int maxdoc = searcher.getIndexReader().maxDoc();
				norm = new byte[maxdoc];
				int len = fis.read(norm);
				if(len != maxdoc){
					logger.warn("norm inconsistant");
				}
				fis.close();
			}
		} catch (Exception e) {
			logger.warn("cannot init");
		}
	}
	
	public static void build(Searcher searcher){
		IndexReader reader = searcher.getIndexReader();
		int numofdoc = reader.maxDoc();
		cache = new byte[numofdoc];
		norm = new byte[numofdoc];
		for(int i =0; i < numofdoc; i++){
			TermFreqVector tfv;
			try {
				tfv = reader.getTermFreqVector(
						i, field);
				if (tfv == null){
//					throw new RuntimeException("run time error: " + i + ":" + numofdoc);
					logger.warn("run time error: " + i + ":" + numofdoc) ;
					cache[i] = SmallFloat.floatToByte315(0);
				}else {
					String strterms[] = tfv.getTerms();
					int freqs[] = tfv.getTermFrequencies();
					int docLength = Arrays.sum(freqs);
					float atf = docLength/(float) strterms.length;
					float sumRITF = 0f;
					float denominator = Idf.log(1 + atf);
					for(int j=0; j < freqs.length; j++){
//						float AEF = termFrequency/df;
//						float IDF = getIDF(strterms[j], numofdoc, searcher);
//						sumRITF += IDF * Idf.log(1 + freqs[j])/denominator ;
						float RITF = Idf.log(1 + freqs[j])/denominator ;
						RITF = RITF/(1+RITF);
						sumRITF += RITF;
					}
					cache[i] = SmallFloat.floatToByte315(atf);
					norm[i] = SmallFloat.floatToByte315(sumRITF);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		//save
		File file = new File(path);
		try {
			FileOutputStream fos = new FileOutputStream(file);
			fos.write(cache);
			fos.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		file = new File(normpath);
		try {
			FileOutputStream fos = new FileOutputStream(file);
			fos.write(norm);
			fos.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
}
