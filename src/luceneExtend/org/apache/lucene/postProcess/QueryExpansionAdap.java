/**
 * 
 */
package org.apache.lucene.postProcess;

import java.io.IOException;
import java.util.Arrays;

import org.apache.log4j.Logger;
import org.apache.lucene.postProcess.termselector.TermSelector;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.RBooleanClause;
import org.apache.lucene.search.RBooleanQuery;
import org.apache.lucene.search.RQuery;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.search.TopDocCollector;
import org.dutir.lucene.evaluation.TRECQrelsInMemory;
import org.dutir.lucene.util.ApplicationSetup;
import org.dutir.lucene.util.ExpansionTerms.ExpansionTerm;


/**
 * @author yezheng
 * 
 */
public class QueryExpansionAdap extends QueryExpansion {

	static private Logger logger = Logger.getLogger(QueryExpansionAdap.class);
	static TRECQrelsInMemory trecR = null;
	static String idtag = ApplicationSetup.getProperty("TrecDocTags.idtag",
			"DOCNO");
	
	String feedbackStrategy = "";

	@Override
	public String getInfo() {
		int n_doc = ApplicationSetup.EXPANSION_DOCUMENTS;
		int n_term = ApplicationSetup.EXPANSION_TERMS;
		return (Relevance?"TrueQEAdap":"QEAdap") + "_" + feedbackStrategy + "_" + n_doc + "_" + n_term;
	}

	public TopDocCollector postProcess(RBooleanQuery query,
			TopDocCollector topDoc, Searcher seacher) {
		setup(query, topDoc, seacher); // it is necessary

		int numberOfTermsToReweight = Math.max(ApplicationSetup.EXPANSION_TERMS, bclause.length);

		if (ApplicationSetup.EXPANSION_TERMS == 0)
			numberOfTermsToReweight = 0;

		// If no document retrieved, keep the original query.
//		if (ScoreDoc.length == 0) {
//			return new TopDocCollector(0);
//		}

		FeedbackSelector fselector = this.getFeedbackSelector(seacher);
		FeedbackDocuments fdocs = fselector.getFeedbackDocuments(topicId);

		TermSelector selector = TermSelector
				.getDefaultTermSelector(this.searcher);
		selector.setResultSet(topDoc);
		selector.setOriginalQueryTerms(termSet);
		selector.setField(field);
		
		ExpansionTerm[] expandedTerms = expandFromDocuments(fdocs.docid, fdocs.score, numberOfTermsToReweight, QEModel, selector);

		feedbackStrategy = selector.getInfo();
		feedbackStrategy += fselector.getInfo();
		float testTotal = 0;
		float total = 0;
		
		/**
		 *  initial the term weight in case that the term in query is 
		 *  not included in the top expansion terms. 
		 *  This is important for LM model.
		 */
		if(LanguageModel){
			 for(RBooleanClause tmpClause : this.cluaseSet.keySet() ){
				 Query qry = tmpClause.getQuery();
				 float newQueryWeight = (1 - QEModel.LM_ALPHA)* qry.getOccurNum();
				 qry.setOccurNum(newQueryWeight);
				 total += newQueryWeight;
			 }
		}
		for (int i = 0; i < expandedTerms.length; i++) {
			ExpansionTerm expandedTerm = expandedTerms[i];
			
			testTotal += expandedTerm.getWeightExpansion();
			
			RBooleanClause clause = generateClause(expandedTerm);
			RBooleanClause tmpClause = this.cluaseSet.get(clause);
			if (tmpClause != null) { // expansion term in original query
				Query qry = tmpClause.getQuery();
//				total += qry.getOccurNum();
				// qry.setBoost(qry.getOccurNum() +
				// expandedTerm.getWeightExpansion());
				float weight = 0;
				if (LanguageModel){
					// Warn1 : do not multiple (1 - QEModel.LM_ALPHA) against (already done)
					// Warn2 : total should only plus  QEModel.LM_ALPHA * expandedTerm.getWeightExpansion()
//					weight = (1 - QEModel.LM_ALPHA)* qry.getOccurNum() + QEModel.LM_ALPHA * expandedTerm.getWeightExpansion();
					weight = qry.getOccurNum() + QEModel.LM_ALPHA * expandedTerm.getWeightExpansion();
					total += QEModel.LM_ALPHA * expandedTerm.getWeightExpansion();
				}
				else if (QEModel.PARAMETER_FREE) {
					weight = (float) (qry.getOccurNum() + expandedTerm
							.getWeightExpansion());
					total += weight;
				} else {
					weight = (qry.getOccurNum() + QEModel.ROCCHIO_BETA
							* expandedTerm.getWeightExpansion());
					total += weight;
//					weight = (float) (qry.getOccurNum() + QEModel.ROCCHIO_BETA);
//					total += QEModel.ROCCHIO_BETA
//					;
				}
				expandedTerm.setWeightExpansion(weight);
				qry.setOccurNum(weight); // not boost
				
			} else { //expansion term is not in original query
				Query qry = clause.getQuery();
				this.cluaseSet.put(clause, clause);
				float weight = 0;
				if (LanguageModel ){
						weight = QEModel.LM_ALPHA * expandedTerm.getWeightExpansion();
				}
				else if (QEModel.PARAMETER_FREE) {
					weight = expandedTerm.getWeightExpansion();
				} else {
					weight = QEModel.ROCCHIO_BETA* expandedTerm.getWeightExpansion();
//					weight = QEModel.ROCCHIO_BETA;
				}
				expandedTerm.setWeightExpansion(weight);
				qry.setOccurNum(weight); // not boost
				total += weight;
			}
		}

//		assert total == 1.0; 
		if(logger.isDebugEnabled()) logger.debug("total weight = " + total);
		if (LanguageModel) {
			for (RBooleanClause clause : this.cluaseSet.values()) {
				RQuery qry = clause.getQuery();
				float weight = (float) qry.getOccurNum();
				qry.setOccurNum(weight / total);
			}
			if(logger.isDebugEnabled()){
				for (int i = 0; i < expandedTerms.length; i++) {
					expandedTerms[i].setWeightExpansion(expandedTerms[i].getWeightExpansion()/ total);
				}
			}
		}else{
			
		}

		RBooleanClause clauses[] = this.cluaseSet.values().toArray(new RBooleanClause[0]);
		RBooleanQuery fquery = generateNewQuery(clauses);
		
		fquery.setqueryLen(ApplicationSetup.EXPANSION_TERMS); //change this for MATF. if you want to change this value, also change it in QueryExansion.java
//		fquery.setqueryLen(this.originalQueryLength);
		
		if(logger.isDebugEnabled())  logger.debug(query.getTopicId() + " Expansion Info:\n" + getExpansionInfo(expandedTerms, true));
		int num = Integer.parseInt(ApplicationSetup.getProperty(
				"TRECQuerying.endFeedback", "1000"));
		TopDocCollector cls = new TopDocCollector(num);
		try {
			this.searcher.search(fquery, cls);
		} catch (IOException e) {
			e.printStackTrace();
		}
		cls.setInfo(topDoc.getInfo());
		cls.setInfo_add(this.getInfo());
		cls.setInfo_add(QEModel.getInfo());
		return cls;
	}

	public ExpansionTerm[] expandFromDocuments(int[] docIDs, float scores[], 
			int numberOfTermsToReweight, QueryExpansionModel QEModel,
			TermSelector selector) {

		if(logger.isDebugEnabled())  logger.debug("Number of feedback documents: "
				+ docIDs.length);
		if (this.termSet != null)
			selector.setOriginalQueryTerms(termSet);
		selector.assignTermWeights(docIDs, scores, QEModel);

		// for (int i=0; i<docIDs.length; i++)
		// if(logger.isDebugEnabled())  logger.debug("doc "+(i+1)+": "+docIDs[i]);
		if(logger.isDebugEnabled())  logger.debug("Number of unique terms in the feedback document set: "
				+ selector.getNumberOfUniqueTerms());
		ExpansionTerm[] expTerms = selector
				.getMostWeightedTerms(numberOfTermsToReweight);
		if(logger.isDebugEnabled())  logger.debug("Number of terms returned from TermSelector: "
				+ numberOfTermsToReweight + ", " + expTerms.length);
		// ExpansionTerm[] expTerms = new ExpansionTerm[queryTerms.size()];
		// queryTerms.values().toArray(expTerms);

		Arrays.sort(expTerms);
		
		try {
			if(!QEModel.PARAMETER_FREE){
				float normlizer = expTerms[0].getWeightExpansion();
				if(normlizer !=1){
					for(int i=0; i < expTerms.length; i++){
						expTerms[i].setWeightExpansion(expTerms[i].getWeightExpansion()/ normlizer);
					}
				}
			}
		} catch (Exception e) {
			logger.warn("Query " + this.topicId + " did get feedback docs");
		}
		
		return expTerms;
	}

}
