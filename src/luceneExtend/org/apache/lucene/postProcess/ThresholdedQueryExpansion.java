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
//import gnu.trove.TIntHashSet;
//
//import java.io.BufferedReader;
//import java.io.IOException;
//import java.util.StringTokenizer;
//
//import org.apache.log4j.Logger;
//
//import uk.ac.gla.terrier.utility.ApplicationSetup;
//import uk.ac.gla.terrier.utility.Files;
//import uk.ac.gla.terrier.utility.Rounding;
//
//public class ThresholdedQueryExpansion extends ExplicitQueryExpansion {
//	protected static Logger logger = Logger.getRootLogger();
//	
//	protected double threshold;
//	
//	public ThresholdedQueryExpansion() {
//		super();
//	}
//	protected void loadFeedbackInformation(String filename){
//		try{
//			logger.debug("Loading feedback document "+filename+"...");
//			threshold = Double.parseDouble(ApplicationSetup.getProperty("qe.threshold", ""));
//			queryidRelDocumentMap = new THashMap<String, TIntHashSet>();
//			BufferedReader br = Files.openFileReader(filename);
//			//THashSet<String> queryids = new THashSet<String>();
//			String line = null;
//			while ((line=br.readLine())!=null){
//				line=line.trim();
//				if (line.length()==0)
//					continue;
//				StringTokenizer stk = new StringTokenizer(line);
//				int docid = Integer.parseInt(stk.nextToken());
//				String qid = stk.nextToken();
//				double featureValue = Double.parseDouble(stk.nextToken());
//				int relevance = Integer.parseInt(stk.nextToken());
//				if (featureValue>=threshold){
//					//logger.debug("docid "+docid+" is used for QE. value="+Rounding.toString(featureValue, 4)+
//							//", threhold="+Rounding.toString(threshold, 4)+", relevance="+relevance);
//					if (queryidRelDocumentMap.contains(qid))
//						queryidRelDocumentMap.get(qid).add(docid);
//					else
//						queryidRelDocumentMap.put(qid, new TIntHashSet(docid));
//				}else
//					logger.debug("docid "+docid+" ignored from QE due to low feature value. value="+Rounding.toString(featureValue, 4)+
//							", threhold="+Rounding.toString(threshold, 4)+", relevance="+relevance);
//			}
//			br.close();
//		}catch(IOException ioe){
//			ioe.printStackTrace();
//			System.exit(1);
//		}
//	}
//}
