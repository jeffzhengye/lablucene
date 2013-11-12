package org.dutir.lucene.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.log4j.Logger;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.TermFreqVector;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.util.SmallFloat;

/**
 * @author zheng
 * Cache average tf in each document
 *
 */
public class ATFCache {
	static Logger logger = Logger.getLogger(ATFCache.class);
	static String field = ApplicationSetup.getProperty(	
			"Lucene.QueryExpansion.FieldName", "content");
	/**
	 * you should set a different path for every different collection.
	 */
	static String path = null;
//	static String lockpath = null;
	static {
		String indexPath = ApplicationSetup.getProperty(
				"Lucene.indexDirectory", ".");
		path = indexPath + "/averTF.cache";
//		lockpath = indexPath + "/termscache.lock";
	}
	public static byte[] cache = null;
	
	public static byte[] init(Searcher searcher){
		if(cache != null){
			return cache;
		}
		initCache(searcher);
		if (cache == null) {
			build(searcher);
		}
		return cache;
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
	
	public static void build(Searcher searcher){
		IndexReader reader = searcher.getIndexReader();
		int numofdoc = reader.maxDoc();
		cache = new byte[numofdoc];
		for(int i =0; i < numofdoc; i++){
			TermFreqVector tfv;
			try {
				tfv = reader.getTermFreqVector(
						i, field);
				float docLength = searcher.getFieldLength(field, i);
				if (tfv == null){
//					throw new RuntimeException("run time error: " + i + ":" + numofdoc);
					logger.warn("run time error: " + i + ":" + numofdoc + ":" + docLength);
					cache[i] = SmallFloat.floatToByte315(0);
				}else {
					String strterms[] = tfv.getTerms();
					float atf = docLength/strterms.length;
					cache[i] = SmallFloat.floatToByte315(atf);
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
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
		
	}
	
}
