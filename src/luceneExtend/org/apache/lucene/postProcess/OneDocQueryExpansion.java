///**
// * 
// */
//package uk.ac.gla.terrier.querying;
//
//import uk.ac.gla.terrier.matching.MatchingQueryTerms;
//import uk.ac.gla.terrier.matching.ResultSet;
//import uk.ac.gla.terrier.querying.parser.SingleTermQuery;
//import uk.ac.gla.terrier.structures.ExpansionTerms;
//import uk.ac.gla.terrier.utility.ApplicationSetup;
//import uk.ac.gla.terrier.utility.Rounding;
//
///**
// * @author ben
// *
// */
//public class OneDocQueryExpansion extends QueryExpansion {
//
//	/**
//	 * 
//	 */
//	public OneDocQueryExpansion() {
//		// TODO Auto-generated constructor stub
//	}
//
//	/**
// 	* This method implements the functionality of expanding a query.
// 	* @param query MatchingQueryTerms the query terms of 
// 	*		the original query.
// 	* @param resultSet CollectionResultSet the set of retrieved 
// 	*		documents from the first pass retrieval.
// 	*/
//	public void expandQuery(MatchingQueryTerms query, ResultSet resultSet) {
//		// only the ith returned doc is used for relevance feedback. i is 0-based ranking.
//		int feedbackDocRank = Integer.parseInt(ApplicationSetup.getProperty("feedback.document.rank", "0"));
//		// the number of term to re-weight (i.e. to do relevance feedback) is
//		// the maximum between the system setting and the actual query length.
//		// if the query length is larger than the system setting, it does not
//		// make sense to do relevance feedback for a portion of the query. Therefore, 
//		// we re-weight the number of query length of terms.
//		int numberOfTermsToReweight = Math.max(ApplicationSetup.EXPANSION_TERMS, 
//				query.length());
//		if (ApplicationSetup.EXPANSION_TERMS == 0)
//			numberOfTermsToReweight = 0;
//
//		// If no document retrieved, keep the original query.
//		if (resultSet.getResultSize() == 0 || resultSet.getExactResultSize() <= feedbackDocRank){			
//			return;
//		}
//
//		int[] docIDs = resultSet.getDocids();
//		double[] scores = resultSet.getScores();
//		double totalDocumentLength = 0;
//		
//		totalDocumentLength = documentIndex.getDocumentLength(docIDs[feedbackDocRank]);
//		if(logger.isDebugEnabled()){
//			logger.debug("Feedback document: " +
//					" ("+docIDs[feedbackDocRank]+") with "+scores[feedbackDocRank]);
//		}
//		ExpansionTerms expansionTerms =
//			new ExpansionTerms(collStats, totalDocumentLength, lexicon);
//		int[][] terms = directIndex.getTerms(docIDs[feedbackDocRank]);
//		if (terms == null){
//			logger.warn("document "+documentIndex.getDocumentLength(docIDs[feedbackDocRank]) + "("+docIDs[feedbackDocRank]+") not found");
//			return;
//		}
//		else
//			for (int j = 0; j < terms[0].length; j++)
//				expansionTerms.insertTerm(terms[0][j], (double)terms[1][j]);
//		expansionTerms.setOriginalQueryTerms(query);
//		SingleTermQuery[] expandedTerms =
//			expansionTerms.getExpandedTerms(numberOfTermsToReweight, QEModel);
//		for (int i = 0; i < expandedTerms.length; i++){
//			SingleTermQuery expandedTerm = expandedTerms[i];
//			double finalWeight = QEModel.ROCCHIO_ALPHA*query.getTermWeight(expandedTerm.getTerm())+expandedTerm.getWeight();
//			query.setTermProperty(expandedTerm.getTerm(), finalWeight);
//			if(logger.isDebugEnabled()){
//				logger.debug("term " + expandedTerms[i].getTerm()
//				 	+ " appears in expanded query with normalised weight: "
//					+ Rounding.toString(query.getTermWeight(expandedTerms[i].getTerm()), 4));
//			}
//		}
//			
//
//	}
//	
//}
