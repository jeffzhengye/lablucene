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
//package uk.ac.gla.terrier.querying;
//
//import gnu.trove.THashMap;
//import gnu.trove.TIntArrayList;
//import gnu.trove.TIntHashSet;
//import gnu.trove.TIntObjectHashMap;
//
//import java.io.BufferedReader;
//import java.io.IOException;
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.Collections;
//import java.util.StringTokenizer;
//
//import uk.ac.gla.terrier.matching.MatchingQueryTerms;
//import uk.ac.gla.terrier.matching.ResultSet;
//import uk.ac.gla.terrier.matching.dsms.TupleProximityScoreModifier;
//import uk.ac.gla.terrier.statistics.GammaFunction;
//import uk.ac.gla.terrier.structures.ExpansionTerm;
//import uk.ac.gla.terrier.structures.ExpansionTerms;
//import uk.ac.gla.terrier.structures.Tuple;
//import uk.ac.gla.terrier.utility.ApplicationSetup;
//import uk.ac.gla.terrier.utility.Distance;
//import uk.ac.gla.terrier.utility.Files;
//
//public class TupleQueryExpansion extends QueryExpansion {
//
//	/** list of positive feedback documents */
//	protected THashMap<String, int[]> queryidRelDocumentMap;
//	private int windowSize = Integer.parseInt(ApplicationSetup.getProperty("proximity.ngram.length", "2"));
//	private double ngramC = Double.parseDouble(ApplicationSetup.getProperty("proximity.ngram.c","1.0d"));
//	protected boolean CACHE_POSTINGS = Boolean.parseBoolean(ApplicationSetup.getProperty("cache.postings", "false"));
//	private int combFunction = Integer.parseInt(ApplicationSetup
//			.getProperty("proximity.qtw.fnid", "1"));
//	private static final double REC_LOG_2 = 1.0d / Math.log(2.0d);
//	private GammaFunction gf = new GammaFunction();
//
//	private TIntObjectHashMap<int[][]> postingsCache = new TIntObjectHashMap<int[][]>();
//	
//	public TupleQueryExpansion() {
//	}
//	
//	private void loadRelevanceInformation(String filename){
//		try{
//			queryidRelDocumentMap = new THashMap<String, int[]>();
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
//					queryidRelDocumentMap.put(currentQueryid, docidSet.toArray());
//					currentQueryid = queryid;
//					docidSet = new TIntHashSet();
//				}
//				if (relevance > 0) {docidSet.add(docid);}
//			}
//			queryidRelDocumentMap.put(currentQueryid, docidSet.toArray());
//			br.close();
//		}catch(IOException ioe){
//			ioe.printStackTrace();
//			System.exit(1);
//		}
//	}
//
//	public void expandQuery(MatchingQueryTerms query, ResultSet resultSet) {
//		
//		String feedbackFilename = ApplicationSetup.getProperty("feedback.filename", null); 
//		boolean pseudo = (feedbackFilename == null);
//		
//		if (!pseudo && this.queryidRelDocumentMap == null) {
//			this.loadRelevanceInformation(feedbackFilename);
//		}
//
//		// the number of term to re-weight (i.e. to do relevance feedback) is
//		// the maximum between the system setting and the actual query length.
//		// if the query length is larger than the system setting, it does not
//		// make sense to do relevance feedback for a portion of the query. Therefore, 
//		// we re-weight the number of query length of terms.
//		int termLimit = Math.max(ApplicationSetup.EXPANSION_TERMS, query.length());
//		if (ApplicationSetup.EXPANSION_TERMS == 0) {
//			termLimit = 0;
//		}
//
//		//TIntArrayList docIdList;
//		int docCount = 0;
//		int[] docIds = null;
//		if (pseudo) {
//			docCount = Math.min(ApplicationSetup.EXPANSION_DOCUMENTS, resultSet.getResultSize());
//			//docIdList = new TIntArrayList();
//			docIds = new int[docCount];
//			for (int i=0; i<docCount; i++)
//				docIds[i] = resultSet.getDocids()[i];
//			//docIdList.add(resultSet.getDocids(), 0, docCount);
//		}
//		else {
//			// current topic id
//			String topId = query.getQueryId();
//			//docIdList = this.queryidRelDocumentMap.get(topId);
//			
//			//docCount = docIdList.size();
//			docIds = this.queryidRelDocumentMap.get(topId);
//			docCount = docIds.length;
//		}
//		
//		// array of (pseudo-)relevant documents doc ids
//		//int[] docIds = docIdList.toNativeArray();
//		
////		System.out.println("# FEEDBACK DOCS: " + docCount);
//		
//		// return in case there is no (pseudo-)relevance feedback evidence for this query
//		if (docCount == 0) {
//			return;
//		}
//
//		// COMPUTATION OF SINGLE EXPANSION TERM WEIGHTS
//		
//		// get total number of tokens in the feedback set
//		double docLength = 0;
//		for (int i = 0; i < docCount; i++){
//			docLength += documentIndex.getDocumentLength(docIds[i]);
//		}
//
//		// set of all expansion terms in the feedback set
//		ExpansionTerms expansionTerms = new ExpansionTerms(collStats, docLength, lexicon);
//
//		// inserts all terms in feedback documents as candidate expansion terms
//		
//		// for each feedback document
//		for (int i = 0; i < docCount; i++) {
//			int[][] terms = directIndex.getTerms(docIds[i]);
//			if (terms == null) {
//				logger.warn("document "+documentIndex.getDocumentLength(docIds[i]) + "("+docIds[i]+") not found");
//			}
//			else {
//				// for each term in the document
//				for (int j = 0; j < terms[0].length; j++) {
//					// add term id, term frequency to the list of candidate expansion terms
//					expansionTerms.insertTerm(terms[0][j], (double)terms[1][j]);
//				}
//			}
//		}
//		
////		System.out.println("# UNIQUE TERMS IN POSITIVE DOCS: " + expansionTerms.getNumberOfUniqueTerms());
//		
//		// mark original query terms in the set of candidate expansion terms
//		expansionTerms.setOriginalQueryTerms(query);
//		
//		TIntArrayList original = new TIntArrayList();
//		for (String token : query.getTerms()) {
//			original.add(query.getTermCode(token));
//		}
//
//		// map of weighted expansion terms mapped to their term ids
//		TIntObjectHashMap<ExpansionTerm> expansionTermsMap = expansionTerms.getExpandedTermHashSet(expansionTerms.getNumberOfUniqueTerms(), QEModel);
//		// array of weighted expansion terms
//		
//		ExpansionTerm[] termArr = new ExpansionTerm[expansionTermsMap.size()];
//		int counter = 0;
//		for (int i : expansionTermsMap.keys())
//			termArr[counter++] = expansionTermsMap.get(i);
//		
//		//ExpansionTerm[] termArr = (ExpansionTerm[])expansionTermsMap.getValues();
//		// sorts array by expansion weights (ExpansionTerm implements Comparable)
//		Arrays.sort(termArr);
//		
//		// adds expanded terms to the query
//		for (int i = 0; i < termLimit; i++) {
//			//query.setTermProperty(termArr[i].getToken(), termArr[i].getTermID());
//			String token = lexicon.getLexiconEntry(termArr[i].getTermID()).term;
//			termArr[i].setToken(token);
//			query.setTermProperty(token, termArr[i].getTermID());
//			query.addTermPropertyWeight(token, termArr[i].getWeightExpansion());
//		}		
//
//		boolean queryRestricted = Boolean.parseBoolean(ApplicationSetup.getProperty("expansion.tuples.query.restricted", "true"));
//
//		// list of weighted tuples of expansion terms
//		ArrayList<Tuple> tuples = new ArrayList<Tuple>();
//		
//		// for each tuple <i,j> of expansion terms, with term(i) != term(j)
//		for (int i = 0; i < termLimit - 1; i++) {
//			// A term should appear in no more than 20% of documents in the collection. This is to reduce memory cost of proximity search.
//			if (lexicon.getLexiconEntry(termArr[i].getTermID()).n_t > this.collStats.getNumberOfDocuments()/5)
//				continue;
//			for (int j = i + 1; j < termLimit; j++) {
//				ExpansionTerm ti = termArr[i];
//				ExpansionTerm tj = termArr[j];
//				// A term should appear in no more than 20% of documents in the collection. This is to reduce memory cost of proximity search.
//				if (lexicon.getLexiconEntry(termArr[j].getTermID()).n_t > this.collStats.getNumberOfDocuments()/5)
//					continue;
//
//				// only considers tuples with at least one of the original query terms
//	            if (queryRestricted && !original.contains(ti.getTermID()) && !original.contains(tj.getTermID())) {
//    		        continue;
//	            }
//
//				Tuple tuple = new Tuple(new ExpansionTerm[]{ ti, tj });
//				// computes the tuple FD weight in the (pseudo-)relevance set
//				tuple.setWeight(computeFDScore(tuple, docIds));
//				// only stores tuples with positive weights
//				if (tuple.getWeight() > 0) {
//					tuples.add(tuple);
//				}
//			}
//		}
//		
//		// sorts tuples in decreasing order of their weights (Tuple implements Comparable)
//		Collections.sort(tuples);
//		
//		// maximum number of tuples to pick from the most informative tuples 
//		// generated from the expanded single query terms
//		int tupleLimit = Integer.parseInt(ApplicationSetup.getProperty("expansion.tuples", "10"));		
//		
//		// updates tuple limit as the minimum between the configuration value
//		// and the actual number of selected tuples
//		tupleLimit = Math.min(tupleLimit, tuples.size());
//		
//		if (tupleLimit > 0) {
//			StringBuilder info = new StringBuilder();
//			info.append("TupleProximityScoreModifier [");
//			
//			// builds a tuple array
//			Tuple[] tupleArr = new Tuple[tupleLimit];
//			for (int i = 0; i < tupleLimit; i++) {
//				tupleArr[i] = tuples.get(i);
//				info.append(" " + tupleArr[i]);
//			}
//			
//			info.append(" ]");
//			
//			if (logger.isInfoEnabled()) {
//				logger.info("NEWDSM " + info);
//			}
//	
//			// adds a TupleProximityScoreModifier DSM to the query with the
//			// selected tuples as arguments
//			query.addDocumentScoreModifier(new TupleProximityScoreModifier(tupleArr, 1));
//			
//			// TODO: instead of simply doing like this, alter query tree with:
//			//   TupleProximityScoreModifier as FunctionQuery
//			//   tuples as MultiTermQuery arguments for the FunctionQuery
//	//		FunctionQuery fq = new FunctionQuery();
//	//		fq.setFName("TupleProximityScoreModifier");
//	//		
//	//		// builds a tuple array
//	//		for (int i = 0; i < tupleLimit; i++) {
//	//			ExpansionTerm[] terms = tuples.get(i).getTerms();
//	//
//	//			MultiTermQuery mtq = new MultiTermQuery();
//	//			
//	//			for (ExpansionTerm term : terms) {
//	//				SingleTermQuery sqt = new SingleTermQuery();
//	//				sqt.setTerm(term.getToken());
//	//				sqt.setWeight(term.getWeightExpansion());
//	//			
//	//				mtq.add(sqt);
//	//			}
//	//			
//	//			mtq.setWeight(tuples.get(i).getWeight());
//	//
//	//			fq.add(mtq);
//	//		}
//	//		
//	//		((MultiTermQuery) query.getQuery()).add(fq);
//			
//
//		}
//	}
//
//	/**
//	 * Computes the score of a given tuple in the (pseudo-)relevance set
//	 * using the full dependence model.
//	 * 
//	 * @param tuple
//	 * @param docIds
//	 * @return tupleWeight * tupleScore
//	 */
//	private double computeFDScore(Tuple tuple, int[] docIds) {
//
//		long numTokens = collStats.getNumberOfTokens();
//		long numDocs = (long)(collStats.getNumberOfDocuments());
//		double avgDocLen = ((double)(numTokens - numDocs * (windowSize - 1))) / (double)numDocs;		
//		
//		// tuple score 
//		double score = 0;
//		
//		ExpansionTerm term1 = tuple.getTerms()[0];
//		// id of term1
//		int term1Id = term1.getTermID();
//		// weight of term1
//		double term1Weight = term1.getWeightExpansion();
//		
//		ExpansionTerm term2 = tuple.getTerms()[1];
//		// id of term2
//		int term2Id = term2.getTermID();
//		// weight of term2
//		double term2Weight = term2.getWeightExpansion();
//		
//		// combination of individual weights of tuple terms
//		double tupleWeight = 0;
//		
//		switch (combFunction) {
//		case 1:
//			tupleWeight = 0.5 * term1Weight + 0.5 * term2Weight;
//			break;
//		case 2:
//			tupleWeight = term1Weight * term2Weight;
//			break;
//		case 3:
//			tupleWeight = Math.min(term1Weight, term2Weight);
//			break;
//		case 4:
//			tupleWeight = Math.max(term1Weight, term2Weight);
//			break;
//		default:
//			tupleWeight = 1.0d;
//		}
//		
//		// list of postings for terms 1 and 2
//		int[][] postings1 = getPostings(term1Id);
//		int[][] postings2 = getPostings(term2Id);
//
//		// number of postings for terms 1 and 2
//		final int postings1Length = postings1[0].length;
//		final int postings2Length = postings2[0].length;
//
//		// for each document k in the (pseudo-)relevance set
//		for (int docId : docIds) {
//
//			// looks for indices of doc k in postings of terms 1 and 2
//			int index1 = Arrays.binarySearch(postings1[0], docId);
//			int index2 = Arrays.binarySearch(postings2[0], docId);
//			// skips doc k if it does not contain both terms 1 and 2
//			if (index1 < 0 || index2 < 0)
//				continue;
//
//			// determines start and end boundaries for the list of blocks in
//			// which term1 occurs in doc k
//			int start1 = postings1[3][index1];
//			int end1 = index1 == postings1Length - 1 ? postings1[4].length
//					: postings1[3][index1 + 1];
//
//			// determines start and end boundaries for the list of blocks in
//			// which term2 occurs in doc k
//			int start2 = postings2[3][index2];
//			int end2 = index2 == postings2Length - 1 ? postings2[4].length
//					: postings2[3][index2 + 1];
//
//			// read number of tokens for doc k
//			final int docLength = documentIndex.getDocumentLength(docId); 
//				
//			// count number of co-occurences of terms with the given window size
//			final int tupleFreq = Distance.noTimes(postings1[4], start1, end1, // blocks of term1
//					                               postings2[4], start2, end2, // blocks of term2
//					                               windowSize, // window size
//					                               docLength);
//
//			// if we found matching ngrams, we score them
//			boolean applyNorm2 = Boolean.parseBoolean(ApplicationSetup.getProperty("proximity.norm2.enabled", "false"));
//			if (tupleFreq > 0) {
//
//				final int numWindows = (docLength > 0 && docLength < windowSize) ? 1
//						: docLength - windowSize + 1;
//				final double background = applyNorm2 ? avgDocLen : numWindows;
//				
//				// apply Norm2 to pf
//				final double matchingNGramsNormalised = applyNorm2
//					? tupleFreq
//						* Math.log(1 + ngramC * avgDocLen / numWindows)
//						* REC_LOG_2
//					: tupleFreq;
//
//				double p = 1.0D / background;
//				double q = 1.0d - p;
//				double tmpScore = -gf
//						.compute_log(background+ 1.0d)
//						* REC_LOG_2 // /Math.log(2.0d)
//						+ gf.compute_log(matchingNGramsNormalised + 1.0d)
//						* REC_LOG_2 // /Math.log(2.0d)
//						+ gf.compute_log(background
//								- matchingNGramsNormalised + 1.0d)
//						* REC_LOG_2// /Math.log(2.0d)
//						- matchingNGramsNormalised
//						* Math.log(p)
//						* REC_LOG_2 // /Math.log(2.0d)
//						- (background - matchingNGramsNormalised)
//						* Math.log(q) * REC_LOG_2; // /Math.log(2.0d);
//
//				tmpScore = tmpScore / (1.0d + matchingNGramsNormalised);
//
//				if (Double.isInfinite(tmpScore) || Double.isNaN(tmpScore)) {
//					System.err.println("docid: " + docId + ", docLength:"
//							+ docLength + ", matchingNGrams: " + tupleFreq
//							+ "matchingNGramsNormalised="
//							+ matchingNGramsNormalised + ", avgdoclen = " + avgDocLen);
//				}
//				else {
//					score += tupleWeight * tmpScore;
//				}
//			}
//		}
//		
//		return score;
//	}
//			
//	public int[][] getPostings(int termId) {
//		int[][] postings = null;
//
//		if (this.CACHE_POSTINGS && postingsCache.contains(termId)) {
//			postings = (int[][])postingsCache.get(termId);
//		} else {
//			postings = invertedIndex.getDocuments(termId);
//
//			//replace the block frequencies with the index of the blocks in the array
//			final int docFrequency = postings[0].length;
//			int blockFrequencySum = 0;
//			int tmp;
//			for (int i = 0; i<docFrequency; i++) {
//				tmp = postings[3][i];
//				postings[3][i] = blockFrequencySum;
//				blockFrequencySum += tmp;
//			}
//			if (this.CACHE_POSTINGS)
//				postingsCache.put(termId, postings);
//		}
//		return postings;
//	}
//	
//}
