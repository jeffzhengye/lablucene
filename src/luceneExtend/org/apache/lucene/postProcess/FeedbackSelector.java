
package org.apache.lucene.postProcess;

import java.io.IOException;
import java.util.HashMap;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermDocs;
import org.apache.lucene.search.Searcher;
import org.dutir.lucene.util.ApplicationSetup;


/**
 * Implements of this class can be used to select feedback documents. Feedback
 * documents are represented by the FeedbackDocument instances.
 */
public abstract class FeedbackSelector {

	protected Searcher searcher;
	protected int effDocuments;
	static String field = ApplicationSetup.getProperty(
			"Lucene.QueryExpansion.FieldName", "content");
	/**
	 * docidField should be the same as idtag -- check it out when having time.
	 */
	static String docidField = ApplicationSetup.getProperty("TrecDocTags.idtag",
			"DOCNO");

	String idtag = ApplicationSetup.getProperty("TrecDocTags.idtag",
			"DOCNO");
	
	/**
	 * used for dynamically change feedback process: 1. specify feedback doc in the post-processing process, like in FeatureExtract13pp.java
	 * 2. load feedback docs from a static file, like in
	 */
	static HashMap<String, FeedbackDocuments> feedbackMap = new HashMap<String, FeedbackDocuments>();
	public static void putFeedbackDocs(String topicId, int docids[], float scores[], float totalFeedbackLen){
		FeedbackDocuments fdocs = new FeedbackDocuments();
		fdocs.totalDocumentLength= 0;
		fdocs.docid = docids;
		fdocs.score = scores;
		fdocs.totalDocumentLength = totalFeedbackLen;
		feedbackMap.put(topicId, fdocs);
	}
	
	public void putFeedbackDocs(String topicId, int docids[], float scores[]){
		FeedbackDocuments fdocs = new FeedbackDocuments();
		fdocs.totalDocumentLength= 0;
		fdocs.docid = docids;
		fdocs.score = scores;
		for (int i = 0; i < docids.length; i++) {
			fdocs.totalDocumentLength += searcher.getFieldLength(field,
					docids[i]);
		}
		feedbackMap.put(topicId, fdocs);
	}
	
	String getdocno(int innerid){
		String docno = null;
		try {
			Document doc = searcher.doc(innerid);
//			tweet = doc.get("content"); 
			docno = doc.get(docidField);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return docno;
	}
	
	public int getInnerDocid(String docno){
		try {
			TermDocs tdocs = searcher.getIndexReader().termDocs(new Term(idtag, docno));
			if(tdocs.next()){
				return tdocs.doc();
			}else {
				System.out.print("doc |" + docno + "| do not exist.");
			} 
		} catch (IOException e) {
			e.printStackTrace();
			
		}
		return -1;
	}
	
	/** Set the index to be used */
	public void setIndex(Searcher searcher) {
		this.searcher = searcher;
	}
	
	public void setField(String field){
		this.field = field;
	}
	
	public static String getTrimID(String queryid) {
		boolean firstNumericChar = false;
		StringBuilder queryNoTmp = new StringBuilder();
		for (int i = queryid.length() - 1; i >= 0; i--) {
			char ch = queryid.charAt(i);
			if (Character.isDigit(ch)) {
				queryNoTmp.append(queryid.charAt(i));
				firstNumericChar = true;
			} else if (firstNumericChar)
				break;
		}
		return "" + Integer.parseInt(queryNoTmp.reverse().toString());
	}
	
	/** Obtain feedback documents for the specified query request */
	public abstract FeedbackDocuments getFeedbackDocuments(String topicId);
	public abstract String getInfo();
	public void setExpDocuments(int effDocuments) {
		this.effDocuments = effDocuments;
		
	}
}
