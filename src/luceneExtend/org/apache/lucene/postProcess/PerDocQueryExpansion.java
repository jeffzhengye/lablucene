/*
 * Terrier - Terabyte Retriever
 * Webpage: http://terrier.org
 * Contact: terrier{a.}dcs.gla.ac.uk
 * University of Glasgow - School of Computing Science
 * http://www.ac.gla.uk
 *
 * The contents of this file are subject to the Mozilla Public License
 * Version 1.1 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
 * the LiCense for the specific language governing rights and limitations
 * under the License.
 *
 * The Original Code is TRECQuerying.java.
 *
 * The Original Code is Copyright (C) 2004-2011 the University of Glasgow.
 * All Rights Reserved.
 *
*/

/*
* This file is probably based on a class with the same name from Terrier, 
* so we keep the copyright head here. If you have any question, please notify me first.
* Thanks. 
*/
///*
// * Terrier - Terabyte Retriever
// * Webpage: http://ir.dcs.gla.ac.uk/terrier
// * Contact: terrier{a.}dcs.gla.ac.uk
// * University of Glasgow - Department of Computing Science
// * http://www.gla.ac.uk/
// *
// * The contents of this file are subject to the Mozilla Public License
// * Version 1.1 (the "License"); you may not use this file except in
// * compliance with the License. You may obtain a copy of the License at
// * http://www.mozilla.org/MPL/
// *
// * Software distributed under the License is distributed on an "AS IS"
// * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
// * the License for the specific language governing rights and limitations
// * under the License.
// *
// * The Original Code is PerDocQueryExpansion.java.
// *
// * The Original Code is Copyright (C) 2004-2008 the University of Glasgow.
// * All Rights Reserved.
// *
// * Contributor(s):
// *   Ben He <ben{a.}dcs.gla.ac.uk>
// */
//package uk.ac.gla.terrier.querying;
//import java.util.Arrays;
//
//import gnu.trove.TIntDoubleHashMap;
//import gnu.trove.TIntIntHashMap;
//import gnu.trove.TIntObjectHashMap;
//
//import org.apache.log4j.Logger;
//
//import uk.ac.gla.terrier.matching.MatchingQueryTerms;
//import uk.ac.gla.terrier.matching.ResultSet;
//import uk.ac.gla.terrier.structures.ExpansionTerm;
//import uk.ac.gla.terrier.structures.ExpansionTerms;
//import uk.ac.gla.terrier.structures.Index;
//import uk.ac.gla.terrier.utility.ApplicationSetup;
//import uk.ac.gla.terrier.utility.Rounding;
///**
// * Implement query expansion where term weights are averaged over all feedback documents, instead of
// * considering all feedback documents as a whole.
// * @version $Revision: 1.1 $
// * @author Ben He
// */
//public class PerDocQueryExpansion extends QueryExpansion {
//	protected static Logger logger = Logger.getRootLogger();
//	
//	/**
//	* The default constructor of PerDocQueryExpansion.
//	*/
//	public PerDocQueryExpansion() {}
//	/**
// 	* This method implements the functionality of expanding a query.
// 	* @param query MatchingQueryTerms the query terms of 
// 	*		the original query.
// 	* @param resultSet CollectionResultSet the set of retrieved 
// 	*		documents from the first pass retrieval.
// 	*/
//	public void expandQuery(MatchingQueryTerms query, ResultSet resultSet) {
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
//		if (resultSet.getResultSize() == 0){			
//			return;
//		}
//
//		int[] docIDs = resultSet.getDocids();
//		double[] scores = resultSet.getScores();
//	
//		
//		double totalDocumentLength = 0;
//		
//		// if the number of retrieved documents is lower than the parameter
//		// EXPANSION_DOCUMENTS, reduce the number of documents for expansion
//		// to the number of retrieved documents.
//		int effDocuments = Math.min(docIDs.length, ApplicationSetup.EXPANSION_DOCUMENTS);
//		
//		ExpansionTerm[][] expTerms = new ExpansionTerm[effDocuments][];
//		
//		// for each of the top-ranked documents
//		for (int i=0; i<effDocuments; i++){
//			// obtain the weighted terms
//			int[] oneDocid = {docIDs[i]};
//			expTerms[i] = this.expandFromDocuments(oneDocid, query, directIndex.getTerms(docIDs[i]).length);
//		}
//		// merge expansion terms: compute mean term weight for each term, sort again
//		TIntDoubleHashMap termidWeightMap = new TIntDoubleHashMap();
//		TIntIntHashMap termidDFMap = new TIntIntHashMap();
//		for (int i=0; i<effDocuments; i++){
//			for (int j=0; j<expTerms[i].length; j++){
//				termidWeightMap.put(expTerms[i][j].getTermID(), expTerms[i][j].getWeightExpansion());
//				termidDFMap.adjustOrPutValue(expTerms[i][j].getTermID(), 1, 1);
//			}
//		}
//		
//		ExpansionTerm[] candidateTerms = new ExpansionTerm[termidWeightMap.size()];
//		// expansion term should appear in at least half of the feedback documents
//		int minDF = (effDocuments%2==0)?(effDocuments/2):(effDocuments/2+1);
//		int counter = 0;
//		for (int termid : termidWeightMap.keys()){
//			candidateTerms[counter] = new ExpansionTerm(termid);
//			if (termidDFMap.get(termid)<minDF)
//				candidateTerms[counter].setWeightExpansion(0d);
//			else
//				candidateTerms[counter].setWeightExpansion(termidWeightMap.get(termid)/termidDFMap.get(termid));
//			counter++;
//		}
//		Arrays.sort(candidateTerms);
//		
//		for (int i = 0; i < expTerms.length; i++){
//			if (candidateTerms[i].getWeightExpansion()<=0)
//				break;
//			lexicon.findTerm(candidateTerms[i].getTermID());
//			double finalWeight = QEModel.ROCCHIO_ALPHA*query.getTermWeight(lexicon.getTerm())+QEModel.ROCCHIO_BETA*candidateTerms[i].getWeightExpansion();
//			query.setTermProperty(lexicon.getTerm(), finalWeight);
//			if(logger.isDebugEnabled()){
//				logger.debug("term " + lexicon.getTerm()
//				 	+ " appears in expanded query with normalised weight: "
//					+ Rounding.toString(query.getTermWeight(lexicon.getTerm()), 4));
//			}
//		}
//	}
//	
//	protected ExpansionTerm[] expandFromDocuments(int[] docIDs, MatchingQueryTerms query, int numberOfTermsToReweight){
//		double totalDocumentLength = 0;
//		
//		int effDocuments = docIDs.length;
//		for (int i = 0; i < effDocuments; i++){
//			totalDocumentLength += documentIndex.getDocumentLength(docIDs[i]);
//			if(logger.isDebugEnabled()){
//				logger.debug((i+1)+": " +
//						" ("+docIDs[i]+")");
//			}
//		}
//		
//		ExpansionTerms expansionTerms =
//			new ExpansionTerms(collStats, totalDocumentLength, lexicon);
//		expansionTerms.setNORMALISE_WEIGHTS(false);
//		expansionTerms.setNumberOfExpansionDocuments(effDocuments);
//		for (int i = 0; i < effDocuments; i++) {
//			int[][] terms = directIndex.getTerms(docIDs[i]);
//			if (terms == null)
//				logger.warn("document "+documentIndex.getDocumentLength(docIDs[i]) + "("+docIDs[i]+") not found");
//			else
//				for (int j = 0; j < terms[0].length; j++)
//					expansionTerms.insertTerm(terms[0][j], (double)terms[1][j]);
//		}
//		expansionTerms.setOriginalQueryTerms(query);
//		logger.debug(expansionTerms.getNumberOfUniqueTerms()+" unique terms in feedback document set.");
//		
//		TIntObjectHashMap<ExpansionTerm> queryTerms = 
//			expansionTerms.getExpandedTermHashSet(numberOfTermsToReweight, QEModel);
//		
//		ExpansionTerm[] expTerms = new ExpansionTerm[queryTerms.size()];
//		int counter = 0;
//		for (int i : queryTerms.keys())
//			expTerms[counter++] = queryTerms.get(i);
//		Arrays.sort(expTerms);
//		return expTerms;
//	}
//
//	/** For easier sub-classing of which index the query expansion comes from */
//	protected Index getIndex(Manager m)
//	{
//		return m.getIndex();
//	}
//
//	
//	/**
//	 * Returns the name of the used query expansion model.
//	 * @return String the name of the used query expansion model.
//	 */
//	public String getInfo() {
//		if (QEModel != null)
//			return QEModel.getInfo()+QEModel.getQEInfo();
//		return "";
//	}
//}
