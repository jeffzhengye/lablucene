package org.apache.lucene.postProcess;

import java.util.Arrays;

import gnu.trove.TIntArrayList;
import gnu.trove.TIntHashSet;

import org.apache.log4j.Logger;
import org.apache.lucene.search.ScoreDoc;
import org.dutir.lucene.evaluation.TRECQrelsInMemory;
import org.dutir.lucene.util.ApplicationSetup;
/** A feedback selector for *Both* pseudo-relevance feedback and real relevance feedback. 
 * Selects the top ApplicationSetup.EXPANSION_DOCUMENTS
  * documents from the ResultSet attached to the specified request.
  */
public class MB12FeedbackSelector extends FeedbackSelector
{
	Logger logger = Logger.getLogger(MB12FeedbackSelector.class);
	private ScoreDoc[] ScoreDoc;
	static boolean Relevance = Boolean.parseBoolean(ApplicationSetup
			.getProperty("QueryExpansion.RelevanceFeedback", "false"));
	static TRECQrelsInMemory trecR = null;
	
	public static TRECQrelsInMemory getTRECQerls() {
		if (trecR == null) {
			trecR = new TRECQrelsInMemory();
		}
		return trecR;
	}
	
	public MB12FeedbackSelector(){}
	

	public FeedbackDocuments getFeedbackDocuments(String topicId)
	{
		long  querydate = Long.parseLong(System.getProperty("BEFOREDATE"));
		long newest_querydate = Long.parseLong(System.getProperty("NEWEST_BEFOREDATE"));
		
		int docIds[] = new int[0];
		float scores[] = new float[0];
		int maxPos = 0; 
//		if (Relevance) {
			
			if (trecR == null) {
				trecR = new TRECQrelsInMemory();
			}
			int pos = 0;
			try {
				String relDocs[] = trecR
						.getRelevantDocumentsToArray(getTrimID(topicId));
				if (relDocs == null) {
					logger.warn("no relevance doc for query: " + topicId);
					maxPos = 0;
				}else{
//					maxPos = Math.min(effDocuments, relDocs.length);
//					maxPos = relDocs.length;
					TIntArrayList docIdList = new TIntArrayList();
					
					
						for (int i = 0; i < relDocs.length; i++) {
							int _id = 0;
							try {
								if(relDocs[i].startsWith("unknown")){
									continue;
								}
								_id = Integer.parseInt(relDocs[i]);
								String docno = getdocno(_id);
								long tweetdate = Long.parseLong(docno);
								if(tweetdate > querydate) {
									logger.info(topicId + " has " + tweetdate +">" + querydate);
									continue;
								}
							} catch (Exception e) {
								logger.warn("false inner doc number: ", e);
								logger.warn ("false doc: " + relDocs[i]);
								continue;
							}
							
							docIdList.add(_id);
							
//							int docid = _id;
//							docIds[pos] = docid;
//							scores[pos] = 1;
//							pos++;
						}
						maxPos = docIdList.size();
						docIds = docIdList.toNativeArray();
						scores = new float[maxPos];
						Arrays.fill(scores, 1);
						logger.info(topicId + " has " + maxPos + " relevant tweet before " + querydate + ", and " + relDocs.length + " in total");
				}

			} catch (Exception e) {
				e.printStackTrace();
			}
			
//		} 
		
//		else {
//			maxPos = effDocuments;
//			if(maxPos > this.ScoreDoc.length){
//				logger.warn("there is no sufficient feedback docs for Query " + topicId + ", " + maxPos + ":"+ this.ScoreDoc.length);
//			}
//			maxPos = Math.min(effDocuments, this.ScoreDoc.length);
//			docIds = new int[maxPos];
//			scores = new float[maxPos];
//			for (int i = 0; i < maxPos; i++) {
//				docIds[i] = this.ScoreDoc[i].doc;
//				scores[i] = this.ScoreDoc[i].score;
//			}
//		}
		
		
		FeedbackDocuments fdocs = new FeedbackDocuments();
		fdocs.totalDocumentLength= 0;
		fdocs.docid = docIds;
		fdocs.score = scores;
		for (int i = 0; i < maxPos; i++) {
			fdocs.totalDocumentLength += searcher.getFieldLength(field,
					docIds[i]);
		}
		return fdocs;
	}
	
	public void setTopDocs(ScoreDoc[] ScoreDoc){
		this.ScoreDoc = ScoreDoc;
	}

	@Override
	public String getInfo() {
		return "";
	}
	
}
