package org.dutir.lucene;

import java.io.IOException;

import org.apache.log4j.Logger;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.search.TopDocs;
import org.dutir.lucene.parser.SocialTagParser;

public class IndexUtility {
	static Logger logger = Logger.getLogger(IndexUtility.class);
	Searcher searcher;
	IndexReader reader ;
	boolean initTag = false;
	
	int maxDoc;
	
	public IndexUtility(Searcher searcher){
		this.searcher = searcher;
		reader = this.searcher.getIndexReader();
	}
	
	public int maxDoc(){
		if(!initTag){
			try {
				maxDoc = searcher.maxDoc();
				initTag = true;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return maxDoc;
	}
	
	public float getDF(Term term){
		try {
			return reader.docFreq(term);
		} catch (IOException e) {
			logger.warn("io error", e);
			return 0;
		}
	}
	
	public float getDF(String sterm, String field){
		return getDF(new Term(field, sterm));
	}
	
	public float getPhraseDF(Term term1, Term term2, int slop){
		float df = 0;
		try {
			PhraseQuery pquery = new PhraseQuery();
			pquery.add(term1); pquery.add(term2);
			pquery.setSlop(slop);
			TopDocs tdocs = searcher.search(pquery, 2);
			df = tdocs.totalHits;
		} catch (IOException e) {
			logger.warn("io error", e);
		}
		return df;
	}
	
}
