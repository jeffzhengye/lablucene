/**
 * 
 */
package org.apache.lucene.postProcess;

import java.io.IOException;
import java.net.URL;
import java.util.HashSet;

import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.search.RBooleanQuery;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.search.TopDocCollector;
import org.dutir.lucene.util.ApplicationSetup;

/**
 * @author yezheng
 *
 */
public class TrecFB09PP extends QueryExpansion {

	
	public TopDocCollector postProcess(RBooleanQuery query, TopDocCollector topDoc, Searcher seacher){
		setup(query, topDoc, seacher); // it is necessary 
		
		int numberOfTermsToReweight = Math.max(
				ApplicationSetup.EXPANSION_TERMS, bclause.length);
		
		if (ApplicationSetup.EXPANSION_TERMS == 0)
			numberOfTermsToReweight = 0;

		// If no document retrieved, keep the original query.
		if (ScoreDoc.length == 0) {
			return new TopDocCollector(0);
		}
		TopDocCollector cls = new TopDocCollector(5);
		post1(cls);
		return cls;
	}
	
	void post1(TopDocCollector cls) {
		HashSet<String> urlSet = new HashSet<String>();
		try {
			for (int i = 0; i < ScoreDoc.length; i++) {
				int docid = ScoreDoc[i].doc;
				float score = ScoreDoc[i].score;

				org.apache.lucene.document.Document doc = this.searcher
						.doc(docid);
				String url = null;
				try {
					url = doc.get("url");
					URL u = new URL(url);
					String host = u.getHost();
					if(!urlSet.contains(host)){
						urlSet.add(host);
						cls.collect(docid, score);
					}
				} catch (Exception e) {
					logger.info(url + " is not uri path");
					cls.collect(docid, score);
//					e.printStackTrace();
				}
			}
		} catch (CorruptIndexException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	void post2(TopDocCollector cls) {
		HashSet<String> urlSet = new HashSet<String>();
		try {
			for (int i = 0; i < ScoreDoc.length; i++) {
				int docid = ScoreDoc[i].doc;
				float score = ScoreDoc[i].score;

				org.apache.lucene.document.Document doc = this.searcher
						.doc(docid);
				String url = doc.get("url");
				URL u = new URL(url);
				String host = u.getHost();
				if(!urlSet.contains(host)){
					urlSet.add(host);
					cls.collect(docid, score);
				}
			}
		} catch (CorruptIndexException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
