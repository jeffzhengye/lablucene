/**
 * 
 */
package org.apache.lucene.postProcess;

import gnu.trove.TObjectFloatHashMap;
import gnu.trove.TObjectIntHashMap;

import java.io.IOException;
import java.util.Arrays;

import org.apache.log4j.Logger;
import org.apache.lucene.postProcess.termselector.DFRTermSelector;
import org.apache.lucene.postProcess.termselector.RMTermSelector;
import org.apache.lucene.postProcess.termselector.RocchioTermSelector;
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
public class QueryExpansionLM extends QueryExpansion {
	static Logger logger = Logger.getLogger(QueryExpansionLM.class);
	static TRECQrelsInMemory trecR = null;
	float alpha = Float.parseFloat(ApplicationSetup.getProperty("lm.alpha",
			"0.5"));
	String feedbackStrategy = "";

	@Override
	public String getInfo() {
		int n_doc = ApplicationSetup.EXPANSION_DOCUMENTS;
		int n_term = ApplicationSetup.EXPANSION_TERMS;
		return (Relevance ? "TrueQELM" : "QELM") + "_" + feedbackStrategy
				+ "_LMalpha=" + alpha + "_" + n_doc + "_" + n_term;
	}

	/**
	 * when using QEModel.PARAMETER_FREE==true, we normalize the term by max
	 * when using QEModel.PARAMETER_FREE==false, we normalize the term by sum()
	 */
	public TopDocCollector postProcess(RBooleanQuery query,
			TopDocCollector topDoc, Searcher seacher) {
		setup(query, topDoc, seacher); // it is necessary
		int numberOfTermsToReweight = Math.max(
				ApplicationSetup.EXPANSION_TERMS, bclause.length);
		if (ApplicationSetup.EXPANSION_TERMS == 0)
			numberOfTermsToReweight = 0;
		// If no document retrieved, keep the original query.
		if (ScoreDoc.length == 0) {
			return new TopDocCollector(0);
		}
		FeedbackSelector fselector = this.getFeedbackSelector(seacher);
		FeedbackDocuments fdocs = fselector.getFeedbackDocuments(topicId);
		if(fdocs.docid.length < 1){
			return topDoc;
		}
		int docIds[] = fdocs.docid;
		float scores[] = fdocs.score;
		TermSelector selector = TermSelector
				.getDefaultTermSelector(this.searcher);
		selector.setResultSet(topDoc);
		selector.setOriginalQueryTerms(termSet);
		selector.setField(field);
		
		if (selector instanceof RMTermSelector) {
			((RMTermSelector) selector).setScores(scores);
		}
		feedbackStrategy = selector.getInfo();
		feedbackStrategy += fselector.getInfo();
		ExpansionTerm[] expandedTerms = // expandPerDoc(docIds,
		// numberOfTermsToReweight, QEModel,
		// selector);
		expandFromDocuments(docIds, scores, numberOfTermsToReweight, QEModel,
				selector);

		float testTotal = 0;
		float total = 0;

		/**
		 * initial the term weight in case that the term in query is not
		 * included in the top expansion terms. This is important for LM model.
		 */
		for (RBooleanClause tmpClause : this.cluaseSet.keySet()) {
			RQuery qry = tmpClause.getQuery();
			float newQueryWeight = (1 - QEModel.LM_ALPHA) * qry.getOccurNum();
			qry.setOccurNum(newQueryWeight);
			total += newQueryWeight;
		}
		for (int i = 0; i < expandedTerms.length; i++) {
			ExpansionTerm expandedTerm = expandedTerms[i];

			testTotal += expandedTerm.getWeightExpansion();

			RBooleanClause clause = generateClause(expandedTerm);
			RBooleanClause tmpClause = this.cluaseSet.get(clause);
			if (tmpClause != null) { // expansion term in original query
				Query qry = tmpClause.getQuery();
				// total += qry.getOccurNum();
				// qry.setBoost(qry.getOccurNum() +
				// expandedTerm.getWeightExpansion());
				float weight = 0;
				// Warn1 : do not multiple (1 - QEModel.LM_ALPHA) against
				// (already done previously)
				// Warn2 : total should only plus QEModel.LM_ALPHA *
				// expandedTerm.getWeightExpansion()
				weight = qry.getOccurNum() + QEModel.LM_ALPHA
						* expandedTerm.getWeightExpansion();
				total += QEModel.LM_ALPHA * expandedTerm.getWeightExpansion();
				expandedTerm.setWeightExpansion(weight);
				qry.setOccurNum(weight); // not boost
			} else { // expansion term is not in original query
				Query qry = clause.getQuery();
				this.cluaseSet.put(clause, clause);
				float weight = 0;
				weight = QEModel.LM_ALPHA * expandedTerm.getWeightExpansion();
				expandedTerm.setWeightExpansion(weight);
				qry.setOccurNum(weight); // not boost
				total += weight;
			}
		}

		if(logger.isDebugEnabled()) logger.debug("total weight = " + total);
		for (RBooleanClause clause : this.cluaseSet.values()) {
			RQuery qry = clause.getQuery();
			float weight = (float) qry.getOccurNum();
			qry.setOccurNum(weight / total);
		}
		if (logger.isDebugEnabled()) {
			for (int i = 0; i < expandedTerms.length; i++) {
				expandedTerms[i].setWeightExpansion(expandedTerms[i]
						.getWeightExpansion()
						/ total);
			}
		}

		RBooleanClause clauses[] = this.cluaseSet.values().toArray(
				new RBooleanClause[0]);
		RBooleanQuery fquery = generateNewQuery(clauses);
		if(logger.isDebugEnabled()) logger.debug("Expansion Info:\n"
				+ getExpansionInfo(expandedTerms, true));
		// logger.debug(fquery);
		int num = Integer.parseInt(ApplicationSetup.getProperty(
				"TRECQuerying.endFeedback", "1000"));
		TopDocCollector cls = new TopDocCollector(num);
		cls.setInfo(topDoc.getInfo());
		cls.setInfo_add(this.getInfo());
		if (selector instanceof RocchioTermSelector
				| selector instanceof DFRTermSelector) {
			cls.setInfo_add(QEModel.getInfo());
		}
		try {
			this.searcher.search(fquery, cls);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return cls;
	}

	/**
	 * @param docIDs
	 * @param query
	 * @param numberOfTermsToReweight
	 * @param index
	 * @param QEModel
	 * @param selector
	 * @return
	 */
	public ExpansionTerm[] expandPerDoc(int[] docIDs, float scores[],
			int numberOfTermsToReweight, QueryExpansionModel QEModel,
			TermSelector selector) {
		// selector.setMetaInfo("normalize.weights", "false");
		int effDocuments = docIDs.length;
		ExpansionTerm[][] expTerms = new ExpansionTerm[effDocuments][];

		// for each of the top-ranked documents
		for (int i = 0; i < effDocuments; i++) {
			// obtain the weighted terms
			int[] oneDocid = { docIDs[i] };
			float[] oneScore = { scores[i] };
			expTerms[i] = expandFromDocuments(oneDocid, oneScore,
					numberOfTermsToReweight, QEModel, selector);
			// logger.debug("term get from doc " + docIDs[i] + " is " +
			// expTerms[i].length);
		}

		// merge expansion terms: compute mean term weight for each term, sort
		// again
		TObjectFloatHashMap<String> termidWeightMap = new TObjectFloatHashMap<String>();
		TObjectIntHashMap<String> termidDFMap = new TObjectIntHashMap<String>();
		for (int i = 0; i < effDocuments; i++) {
			for (int j = 0; j < expTerms[i].length; j++) {
				float weight = expTerms[i][j].getWeightExpansion();
				String term = expTerms[i][j].getTerm();
				termidWeightMap.adjustOrPutValue(term, weight, weight);
				termidDFMap.adjustOrPutValue(term, 1, 1);
			}
		}

		ExpansionTerm[] candidateTerms = new ExpansionTerm[termidWeightMap
				.size()];
		// expansion term should appear in at least half of the feedback
		// documents
		// int minDF = (effDocuments%2==0)?(effDocuments/2):(effDocuments/2+1);
		int minDF = 2; // this creteria is dropped
		int counter = 0;
		for (String termid : termidWeightMap.keys(new String[0])) {
			candidateTerms[counter] = new ExpansionTerm(termid, 0);
			if (docIDs.length > minDF && termidDFMap.get(termid) < minDF)
				candidateTerms[counter].setWeightExpansion(0);
			else
				candidateTerms[counter].setWeightExpansion(termidWeightMap
						.get(termid)
						/ termidDFMap.get(termid));
			counter++;
		}

		Arrays.sort(candidateTerms);
		numberOfTermsToReweight = Math.min(numberOfTermsToReweight,
				candidateTerms.length);
		if (numberOfTermsToReweight < 1) {
			logger.error("the candidate list is empty");
			return new ExpansionTerm[0];
		}
		ExpansionTerm[] expandedTerms = new ExpansionTerm[numberOfTermsToReweight];
		// normalise the expansion weights by the maximum weight among the
		// expansion terms
		float normaliser = candidateTerms[0].getWeightExpansion();
		for (int i = 0; i < numberOfTermsToReweight; i++) {
			expandedTerms[i] = candidateTerms[i];
			expandedTerms[i].setWeightExpansion(candidateTerms[i]
					.getWeightExpansion()
					/ normaliser);
		}
		
		return expandedTerms;
	}

	public ExpansionTerm[] expandFromDocuments(int[] docIDs, float scores[],
			int numberOfTermsToReweight, QueryExpansionModel QEModel,
			TermSelector selector) {
		if (this.termSet != null)		
			selector.setOriginalQueryTerms(termSet);
		selector.assignTermWeights(docIDs, scores, QEModel);

		if(logger.isDebugEnabled()) logger.debug("Number of unique terms in the feedback document set: "
				+ selector.getNumberOfUniqueTerms());
		ExpansionTerm[] expTerms = selector
				.getMostWeightedTerms(numberOfTermsToReweight);
		if(logger.isDebugEnabled()) logger.debug("Number of terms returned from TermSelector: "
				+ numberOfTermsToReweight + ", " + expTerms.length);
		// ExpansionTerm[] expTerms = new ExpansionTerm[queryTerms.size()];
		// queryTerms.values().toArray(expTerms);
		
		Arrays.sort(expTerms);
		truncateNorm(expTerms);
		return expTerms;
	}

	public void truncateNorm(ExpansionTerm[] expTerms) {
		float sum = 0;
		for (int i = 0; i < expTerms.length; i++) {
			sum += expTerms[i].getWeightExpansion();
		}
		for (int i = 0; i < expTerms.length; i++) {
			expTerms[i].setWeightExpansion(expTerms[i].getWeightExpansion()
					/ sum);
		}
	}

}
