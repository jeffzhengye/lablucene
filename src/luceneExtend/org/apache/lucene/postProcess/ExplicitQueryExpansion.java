//package org.apache.lucene.postProcess;
//
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
// * The Original Code is ExplicitQueryExpansion.java.
// *
// * The Original Code is Copyright (C) 2004-2008 the University of Glasgow.
// * All Rights Reserved.
// *
// * Contributor(s):
// *   Ben He <ben{a.}dcs.gla.ac.uk>
// */
//import gnu.trove.THashMap;
//import gnu.trove.TIntHashSet;
//import gnu.trove.TIntObjectHashMap;
//
//import java.io.BufferedReader;
//import java.io.IOException;
//import java.util.Arrays;
//import java.util.StringTokenizer;
//
//import org.apache.log4j.Logger;
//import org.apache.lucene.search.BooleanQuery;
//import org.apache.lucene.search.Searcher;
//import org.apache.lucene.search.TopDocCollector;
//import org.dutir.lucene.util.ApplicationSetup;
//import org.dutir.lucene.util.Files;
//import org.dutir.lucene.util.ExpansionTerms.ExpansionTerm;
//
///**
// * Implements automatic query expansion as PostFilter that is applied to the resultset
// * after 1st-time matching using explicit relevance information.
// * <B>Controls</B>
// * <ul><li><tt>qemodel</tt> : The query expansion model used for Query Expansion. 
// * Defauls to Bo1.</li></ul>
// * <B>Properties</B>
// * <ul><li><tt>expansion.terms</tt> : The maximum number of most weighted terms in the 
// * pseudo relevance set to be added to the original query. The system performs a conservative
// * query expansion if this property is set to 0. A conservation query expansion only reweighs
// * the original query terms without adding new terms to the query.</li>
// * <li><tt>expansion.documents</tt> : The number of top documents from the 1st pass 
// * retrieval to use for QE. The query is expanded from this set of docuemnts, also 
// * known as the relevance set.</li>
// * </ul>
// * @version $Revision: 1.1 $
// * @author Ben He
// */
//public class ExplicitQueryExpansion extends QueryExpansionAdap {
//	protected static Logger logger = Logger.getRootLogger();
//	/**
//	 * Mapping from query id to identifiers of the positive feedback documents.
//	 */
//	protected THashMap<String, TIntHashSet> queryidRelDocumentMap;
//	
//	protected String[] queryids;
//	/**
//	* The default constructor of QueryExpansion.
//	*/
//	public ExplicitQueryExpansion() {
//		super();
//		String feedbackFilename = ApplicationSetup.getProperty("feedback.filename",
//				ApplicationSetup.LUCENE_ETC+
//				ApplicationSetup.FILE_SEPARATOR+"feedback");
//		this.loadFeedbackInformation(feedbackFilename);
//	}
//	
//	protected void loadFeedbackInformation(String filename){
//		try{
//			queryidRelDocumentMap = new THashMap<String, TIntHashSet>();
//			BufferedReader br = Files.openFileReader(filename);
//			//THashSet<String> queryids = new THashSet<String>();
//			String line = null;
//			String currentQueryid = "1st";
//			TIntHashSet docidSet = new TIntHashSet();
//			while ((line=br.readLine())!=null){
//				line=line.trim();
//				if (line.length()==0)
//					continue;
//				StringTokenizer stk = new StringTokenizer(line);
//				int[] relDocids = new int[stk.countTokens()-1];
//				String queryid = stk.nextToken();
//				stk.nextToken();// skip 0
//				int docid = Integer.parseInt(stk.nextToken());
//				int relevance = Integer.parseInt(stk.nextToken());
//				
//				if (currentQueryid.equals("1st")){
//					currentQueryid = queryid;
//				}else if (!currentQueryid.equals(queryid)){
//					if (!queryidRelDocumentMap.containsKey(currentQueryid)){
//						queryidRelDocumentMap.put(currentQueryid, new TIntHashSet(docidSet.toArray()));
//					}
//					currentQueryid = queryid;
//					docidSet = new TIntHashSet();
//				}
//				if (relevance > 0) {docidSet.add(docid);}
//			}
//			if (!queryidRelDocumentMap.containsKey(currentQueryid)){
//				queryidRelDocumentMap.put(currentQueryid, new TIntHashSet(docidSet.toArray()));
//			}
//			br.close();
//		}catch(IOException ioe){
//			ioe.printStackTrace();
//			System.exit(1);
//		}
//	}
//	
//	
//	
//	
//	/**
// 	* This method implements the functionality of expanding a query.
// 	* @param query MatchingQueryTerms the query terms of 
// 	*		the original query.
// 	* @param resultSet CollectionResultSet the set of retrieved 
// 	*		documents from the first pass retrieval.
// 	*/
//	public TopDocCollector postProcess(BooleanQuery query,
//			TopDocCollector topDoc, Searcher seacher) {
//		setup(query, topDoc, seacher); // it is necessary 
//		
//		int numberOfTermsToReweight = Math.max(
//				ApplicationSetup.EXPANSION_TERMS, bclause.length);
//		
//		if (ApplicationSetup.EXPANSION_TERMS == 0)
//			numberOfTermsToReweight = 0;
//
//		// If no document retrieved, keep the original query.
//		if (ScoreDoc.length == 0) {
//			return new TopDocCollector(0);
//		}
//
//		if (queryidRelDocumentMap.get(queryid)==null){
//			logger.info("No relevant document found for feedback.");
//			return;
//			
//			/**
//			 * An alternate option is to do psuedo relevance feedback
//			 */
//			//int[] docids = resultSet.getDocids();
//			//relDocids = new int[ApplicationSetup.EXPANSION_DOCUMENTS];
//			//for (int i=0; i<relDocnos.length; i++)
//				//relDocnos[i] = documentIndex.getDocumentNumber(docids[i]);
//		}
//		int[] relDocids = queryidRelDocumentMap.get(queryid).toArray();
//		int relDocidsCount = relDocids.length;
//		if (relDocidsCount == 0)
//			return;
//		int[] docIDs = relDocids;
//		
//		ExpansionTerm[] expTerms = null;
//		
//		expTerms = this.expandFromDocuments(docIDs, query, numberOfTermsToReweight, directIndex, documentIndex, collStats, lexicon, QEModel);	
//		this.mergeWithExpandedTerms(expTerms, query);
//	}
//}
