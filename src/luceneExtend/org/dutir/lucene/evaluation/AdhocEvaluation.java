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
/*
 * Terrier - Terabyte Retriever 
 * Webpage: http://ir.dcs.gla.ac.uk/terrier 
 * Contact: terrier{a.}dcs.gla.ac.uk
 * University of Glasgow - Department of Computing Science
 * http://www.gla.ac.uk/
 * 
 * The contents of this file are subject to the Mozilla Public License
 * Version 1.1 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
 * the License for the specific language governing rights and limitations
 * under the License.
 *
 * The Original Code is AdhocEvaluation.java.
 *
 * The Original Code is Copyright (C) 2004-2008 the University of Glasgow.
 * All Rights Reserved.
 *
 * Contributor(s):
 *   Gianni Amati <gba{a.}fub.it> (original author)
 *   Ben He <ben{a.}dcs.gla.ac.uk> 
 *   Vassilis Plachouras <vassilis{a.}dcs.gla.ac.uk>
 */
package org.dutir.lucene.evaluation;
import java.io.*;
import java.util.*;
import org.apache.log4j.Logger;
import org.dutir.lucene.util.ApplicationSetup;
import org.dutir.lucene.util.Files;
import org.dutir.lucene.util.Rounding;

/**
 * Performs the evaluation for TREC's tasks, except the named page task.
 * The evaluation measures include the mean average precision and other measures
 * such as precision at 10, precision at 10%, and so on....
 * @author Gianni Amati, Ben He
 * @version $Revision: 1.32 $ 
 */
public class AdhocEvaluation extends Evaluation {
	protected Logger logger = Logger.getLogger(this.getClass());
	/** The maximum number of documents retrieved for a query. */
	protected int maxNumberRetrieved;
	/** The number of effective queries. An effective query is a
	*	query that has corresponding relevant documents in the qrels
	*	file.
	*/
	protected int numberOfEffQuery;
	/** The total number of documents retrieved in the task. */
	protected int totalNumberOfRetrieved;
	/** The total number of relevant documents in the qrels file
	* 	for the queries processed in the task. 
	*/
	protected int totalNumberOfRelevant;
	/** The total number of relevant documents retrieved in the task. */
	protected int totalNumberOfRelevantRetrieved;
	/** Precision at 1 document. */
	protected double PrecAt1;
	/** Precision at 2 documents. */
	protected double PrecAt2;
	/** Precision at 3 documents. */
	protected double PrecAt3;
	/** Precision at 4 documents. */
	protected double PrecAt4;
	/** Precision at 5 documents. */
	protected double PrecAt5;
	/** Precision at 10 documents. */
	protected double PrecAt10;
	/** Precision at 15 documents. */
	protected double PrecAt15;
	/** Precision at 20 documents. */
	protected double PrecAt20;
	/** Precision at 30 documents. */
	protected double PrecAt30;
	/** Precision at 50 documents. */
	protected double PrecAt50;
	/** Precision at 100 documents. */
	protected double PrecAt100;
	/** Precision at 200 documents. */
	protected double PrecAt200;
	/** Precision at 500 documents. */
	protected double PrecAt500;
	/** Precision at 1000 documents. */
	protected double PrecAt1000;
	/** Precision at 0%. */
	protected double PrecAt0Percent;
	/** Precision at 10%. */
	protected double PrecAt10Percent;
	/** Precision at 20%. */
	protected double PrecAt20Percent;
	/** Precision at 30%. */
	protected double PrecAt30Percent;
	/** Precision at 40%. */
	protected double PrecAt40Percent;
	/** Precision at 50%. */
	protected double PrecAt50Percent;
	/** Precision at 60%. */
	protected double PrecAt60Percent;
	/** Precision at 70%. */
	protected double PrecAt70Percent;
	/** Precision at 80%. */
	protected double PrecAt80Percent;
	/** Precision at 90%. */
	protected double PrecAt90Percent;
	/** Precision at 100%. */
	protected double PrecAt100Percent;
	/** Average Precision. */
	public double AveragePrecision;
	/** Relevant Precision. */
	protected double RelevantPrecision;
	/** The average precision of each query. */
	protected double[] AveragePrecisionOfEachQuery;
	/** The query number of each query. */
	protected String[] queryNo;
	
	/** Initialise variables. */
	public void initialise() {
		this.maxNumberRetrieved = 
				Integer.parseInt(ApplicationSetup.getProperty("max.number.retrieved", 
						"1000"));
	}
	
	public void evaluateStr(String content){
		String lines[] = content.split("\n");
		evaluate(lines);
	}
	
	/**
	 * Evaluates the given result file.
	 * @param resultFilename String the filename of 
	 *        the result file to evaluate.
	 */
	public void evaluate(String resultFilename) {
		if(logger.isInfoEnabled()) logger.info("Evaluating result file: " + resultFilename);
		//int retrievedQueryCounter = 0;
		//int releventQueryCounter = 0; 
		String lines[] = null; 
		try {
			lines = org.dutir.util.Files.readLinesFromFile(
					new File( resultFilename), ApplicationSetup.getProperty("file.encoding", "utf8"));
			evaluate(lines);
		} catch (Exception e) {
			logger.error("Probably file broken: " + resultFilename, e);
		}
	}
	

	public void evaluate(String lines[]) {
//		if(logger.isInfoEnabled()) logger.info("Evaluating result file: " + resultFilename);
//		//int retrievedQueryCounter = 0;
//		//int releventQueryCounter = 0; 
//		String lines[] = null; 
//		try {
//			lines = org.dutir.util.Files.readLinesFromFile(
//					new File( resultFilename), ApplicationSetup.getProperty("file.encoding", "utf8"));
//		} catch (Exception e) {
//			logger.error("Probably file broken: " + resultFilename, e);
//		}
		
		int effQueryCounter = 0;
		
		int[] numberOfRelevantRetrieved = null;
		int[] numberOfRelevant = null;
		int[] numberOfRetrieved = null;
		Vector<Record[]> listOfRetrieved = new Vector<Record[]>();
		Vector<Record[]> listOfRelevantRetrieved = new Vector<Record[]>();
		Vector<Integer> vecNumberOfRelevant = new Vector<Integer>();
		Vector<Integer> vecNumberOfRetrieved = new Vector<Integer>();
		Vector<Integer> vecNumberOfRelevantRetrieved = new Vector<Integer>();
		Vector<String> vecQueryNo = new Vector<String>();
		
		/** Read records from the result file */
		try {
			String previous = ""; // the previous query number
			int numberOfRetrievedCounter = 0;
			int numberOfRelevantRetrievedCounter = 0;
			Vector<Record> relevantRetrieved = new Vector<Record>();
			Vector<Record> retrieved = new Vector<Record>();
			int rank =0; //yezheng
		
			for (String str : lines) {
				StringTokenizer stk = new StringTokenizer(str);
				String queryid = stk.nextToken();
				
				//remove non-numeric letters in the queryNo
				StringBuilder queryNoTmp = new StringBuilder();
				boolean firstNumericChar = false;
				for (int i = queryid.length()-1; i >=0; i--){
					if (queryid.charAt(i) >= '0' && queryid.charAt(i) <= '9'){
						queryNoTmp.append(queryid.charAt(i));
						firstNumericChar = true;
					}
					else if (firstNumericChar)
						break;
				}
				queryid = ""+ Integer.parseInt(queryNoTmp.reverse().toString()); 
				if (!qrels.queryExistInQrels(queryid))
					continue;
				
				stk.nextToken();
				String docID = stk.nextToken();
				
//				int rank = Integer.parseInt(stk.nextToken());
				stk.nextToken(); //yezheng
				
				if (!previous.equals(queryid)) {
					rank = 0;
					if (effQueryCounter != 0) {
						vecNumberOfRetrieved.addElement(Integer.valueOf(numberOfRetrievedCounter));
						vecNumberOfRelevantRetrieved.addElement(Integer.valueOf(numberOfRelevantRetrievedCounter));
						listOfRetrieved.addElement((Record[])retrieved.toArray(new Record[retrieved.size()]));
						listOfRelevantRetrieved.addElement((Record[])relevantRetrieved.toArray(new Record[relevantRetrieved.size()]));
						numberOfRetrievedCounter = 0;
						numberOfRelevantRetrievedCounter = 0;
						retrieved = new Vector<Record>();
						relevantRetrieved = new Vector<Record>();
					}
					effQueryCounter++;
					vecQueryNo.addElement(queryid);
					vecNumberOfRelevant.addElement(Integer.valueOf(qrels.getNumberOfRelevant(queryid)));
//					System.out.println(queryid + ": " + Integer.valueOf(qrels.getNumberOfRelevant(queryid)));
				}
				previous = queryid;
				numberOfRetrievedCounter++;
				totalNumberOfRetrieved++;
				
//				System.out.println("" + queryid + " , " + docID + ", " + rank + ", " + effQueryCounter);
				retrieved.addElement(new Record(queryid, docID, rank)); //yezheng
				
				if (qrels.isRelevant(queryid, docID)){
					relevantRetrieved.addElement(new Record(queryid, docID, rank));
					numberOfRelevantRetrievedCounter++;
				}
				rank++;
			}
//			System.out.println( "Q Number: " + effQueryCounter);
			
			
			listOfRelevantRetrieved.addElement(relevantRetrieved.toArray(new Record[relevantRetrieved.size()]));
			listOfRetrieved.addElement(retrieved.toArray(new Record[retrieved.size()]));
			vecNumberOfRetrieved.addElement(Integer.valueOf(numberOfRetrievedCounter));
			vecNumberOfRelevantRetrieved.addElement(Integer.valueOf(numberOfRelevantRetrievedCounter));
			this.queryNo = vecQueryNo.toArray(new String[vecQueryNo.size()]);
			numberOfRelevantRetrieved = new int[effQueryCounter];
			numberOfRelevant = new int[effQueryCounter];
			numberOfRetrieved = new int[effQueryCounter];
			this.totalNumberOfRelevant = 0;
			this.totalNumberOfRelevantRetrieved = 0;
			this.totalNumberOfRetrieved = 0;
			for (int i = 0; i < effQueryCounter; i++){
				numberOfRelevantRetrieved[i] = 
					((Integer)vecNumberOfRelevantRetrieved.get(i)).intValue();
				numberOfRelevant[i] = ((Integer)vecNumberOfRelevant.get(i)).intValue();
				numberOfRetrieved[i] = ((Integer)vecNumberOfRetrieved.get(i)).intValue();
				this.totalNumberOfRetrieved += numberOfRetrieved[i];
				this.totalNumberOfRelevant += numberOfRelevant[i];
				this.totalNumberOfRelevantRetrieved += numberOfRelevantRetrieved[i];
			}
			
		} catch (Exception e) {
			logger.error("Exception while evaluating", e);
		}
		
		this.AveragePrecisionOfEachQuery = new double[effQueryCounter];
		
		int[] PrecisionAt1 = new int[effQueryCounter];
		//Modified by G.AMATI 7th May 2002
		int[] PrecisionAt2 = new int[effQueryCounter];
		int[] PrecisionAt3 = new int[effQueryCounter];
		int[] PrecisionAt4 = new int[effQueryCounter];
		//END of modification
		int[] PrecisionAt5 = new int[effQueryCounter];
		int[] PrecisionAt10 = new int[effQueryCounter];
		int[] PrecisionAt15 = new int[effQueryCounter];
		int[] PrecisionAt20 = new int[effQueryCounter];
		int[] PrecisionAt30 = new int[effQueryCounter];
		int[] PrecisionAt50 = new int[effQueryCounter];
		int[] PrecisionAt100 = new int[effQueryCounter];
		int[] PrecisionAt200 = new int[effQueryCounter];
		int[] PrecisionAt500 = new int[effQueryCounter];
		int[] PrecisionAt1000 = new int[effQueryCounter];
		double[] ExactPrecision = new double[effQueryCounter];
		double[] RPrecision = new double[effQueryCounter];
		double[] PrecisionAt0_0 = new double[effQueryCounter];
		double[] PrecisionAt0_1 = new double[effQueryCounter];
		double[] PrecisionAt0_2 = new double[effQueryCounter];
		double[] PrecisionAt0_3 = new double[effQueryCounter];
		double[] PrecisionAt0_4 = new double[effQueryCounter];
		double[] PrecisionAt0_5 = new double[effQueryCounter];
		double[] PrecisionAt0_6 = new double[effQueryCounter];
		double[] PrecisionAt0_7 = new double[effQueryCounter];
		double[] PrecisionAt0_8 = new double[effQueryCounter];
		double[] PrecisionAt0_9 = new double[effQueryCounter];
		double[] PrecisionAt1_0 = new double[effQueryCounter];
		//computing the precision-recall measures
		for (int i = 0; i < effQueryCounter; i++) {
			PrecisionAt0_0[i] = 0d;
			PrecisionAt0_1[i] = 0d;
			PrecisionAt0_2[i] = 0d;
			PrecisionAt0_3[i] = 0d;
			PrecisionAt0_4[i] = 0d;
			PrecisionAt0_5[i] = 0d;
			PrecisionAt0_6[i] = 0d;
			PrecisionAt0_7[i] = 0d;
			PrecisionAt0_8[i] = 0d;
			PrecisionAt0_9[i] = 0d;
			PrecisionAt1_0[i] = 0d;
			PrecisionAt1[i] = 0;
			//Modified by G.AMATI 7th May 2002
			PrecisionAt2[i] = 0;
			PrecisionAt3[i] = 0;
			PrecisionAt4[i] = 0;
			//END of modification
			PrecisionAt5[i] = 0;
			PrecisionAt10[i] = 0;
			PrecisionAt15[i] = 0;
			PrecisionAt20[i] = 0;
			PrecisionAt30[i] = 0;
			PrecisionAt50[i] = 0;
			PrecisionAt100[i] = 0;
			PrecisionAt200[i] = 0;
			PrecisionAt500[i] = 0;
			PrecisionAt1000[i] = 0;
			ExactPrecision[i] = 0d;
			RPrecision[i] = 0d;
		}
		PrecAt1 = 0d;
		//Modified by G.AMATI 7th May 2002
		PrecAt2 = 0d;
		PrecAt3 = 0d;
		PrecAt4 = 0d;
		//END of modification
		PrecAt5 = 0d;
		PrecAt10 = 0d;
		PrecAt15 = 0d;
		PrecAt20 = 0d;
		PrecAt30 = 0d;
		PrecAt50 = 0d;
		PrecAt100 = 0d;
		PrecAt200 = 0d;
		PrecAt500 = 0d;
		PrecAt1000 = 0d;
		PrecAt0Percent = 0d;
		PrecAt10Percent = 0d;
		PrecAt20Percent = 0d;
		PrecAt30Percent = 0d;
		PrecAt40Percent = 0d;
		PrecAt50Percent = 0d;
		PrecAt60Percent = 0d;
		PrecAt70Percent = 0d;
		PrecAt80Percent = 0d;
		PrecAt90Percent = 0d;
		PrecAt100Percent = 0d;
		AveragePrecision = 0d;
		RelevantPrecision = 0d;
		numberOfEffQuery = effQueryCounter;
		for (int i = 0; i < effQueryCounter; i++) {
			Record[] relevantRetrieved = (Record[])listOfRelevantRetrieved.get(i);
			for (int j = 0; j < relevantRetrieved.length; j++) {
				if (relevantRetrieved[j].rank < numberOfRelevant[i]) {
					RPrecision[i] += 1d;
				}
				if (relevantRetrieved[j].rank < 1) {
					PrecisionAt1[i]++;
					PrecisionAt2[i]++;
					PrecisionAt3[i]++;
					PrecisionAt4[i]++;
					PrecisionAt5[i]++;
					PrecisionAt10[i]++;
					PrecisionAt15[i]++;
					PrecisionAt20[i]++;
					PrecisionAt30[i]++;
					PrecisionAt50[i]++;
					PrecisionAt100[i]++;
					PrecisionAt200[i]++;
					PrecisionAt500[i]++;
					PrecisionAt1000[i]++;
					/**			Modified by G.AMATI 7th May 2002 */
				} else if (relevantRetrieved[j].rank < 2) {
					PrecisionAt2[i]++;
					PrecisionAt3[i]++;
					PrecisionAt4[i]++;
					PrecisionAt5[i]++;
					PrecisionAt10[i]++;
					PrecisionAt15[i]++;
					PrecisionAt20[i]++;
					PrecisionAt30[i]++;
					PrecisionAt50[i]++;
					PrecisionAt100[i]++;
					PrecisionAt200[i]++;
					PrecisionAt500[i]++;
					PrecisionAt1000[i]++;
				} else if (relevantRetrieved[j].rank < 3) {
					PrecisionAt3[i]++;
					PrecisionAt4[i]++;
					PrecisionAt5[i]++;
					PrecisionAt10[i]++;
					PrecisionAt15[i]++;
					PrecisionAt20[i]++;
					PrecisionAt30[i]++;
					PrecisionAt50[i]++;
					PrecisionAt100[i]++;
					PrecisionAt200[i]++;
					PrecisionAt500[i]++;
					PrecisionAt1000[i]++;
				} else if (relevantRetrieved[j].rank < 4) {
					PrecisionAt4[i]++;
					PrecisionAt5[i]++;
					PrecisionAt10[i]++;
					PrecisionAt15[i]++;
					PrecisionAt20[i]++;
					PrecisionAt30[i]++;
					PrecisionAt50[i]++;
					PrecisionAt100[i]++;
					PrecisionAt200[i]++;
					PrecisionAt500[i]++;
					PrecisionAt1000[i]++;
					//END of modification
				} else if (relevantRetrieved[j].rank < 5) {
					PrecisionAt5[i]++;
					PrecisionAt10[i]++;
					PrecisionAt15[i]++;
					PrecisionAt20[i]++;
					PrecisionAt30[i]++;
					PrecisionAt50[i]++;
					PrecisionAt100[i]++;
					PrecisionAt200[i]++;
					PrecisionAt500[i]++;
					PrecisionAt1000[i]++;
				} else if (relevantRetrieved[j].rank < 10) {
					PrecisionAt10[i]++;
					PrecisionAt15[i]++;
					PrecisionAt20[i]++;
					PrecisionAt30[i]++;
					PrecisionAt50[i]++;
					PrecisionAt100[i]++;
					PrecisionAt200[i]++;
					PrecisionAt500[i]++;
					PrecisionAt1000[i]++;
				} else if (relevantRetrieved[j].rank < 15) {
					PrecisionAt15[i]++;
					PrecisionAt20[i]++;
					PrecisionAt30[i]++;
					PrecisionAt50[i]++;
					PrecisionAt100[i]++;
					PrecisionAt200[i]++;
					PrecisionAt500[i]++;
					PrecisionAt1000[i]++;
				} else if (relevantRetrieved[j].rank < 20) {
					PrecisionAt20[i]++;
					PrecisionAt30[i]++;
					PrecisionAt50[i]++;
					PrecisionAt100[i]++;
					PrecisionAt200[i]++;
					PrecisionAt500[i]++;
					PrecisionAt1000[i]++;
				} else if (relevantRetrieved[j].rank < 30) {
					PrecisionAt30[i]++;
					PrecisionAt50[i]++;
					PrecisionAt100[i]++;
					PrecisionAt200[i]++;
					PrecisionAt500[i]++;
					PrecisionAt1000[i]++;
				} else if (relevantRetrieved[j].rank < 50) {
					PrecisionAt50[i]++;
					PrecisionAt100[i]++;
					PrecisionAt200[i]++;
					PrecisionAt500[i]++;
					PrecisionAt1000[i]++;
				} else if (relevantRetrieved[j].rank < 100) {
					PrecisionAt100[i]++;
					PrecisionAt200[i]++;
					PrecisionAt500[i]++;
					PrecisionAt1000[i]++;
				} else if (relevantRetrieved[j].rank < 200) {
					PrecisionAt200[i]++;
					PrecisionAt500[i]++;
					PrecisionAt1000[i]++;
				} else if (relevantRetrieved[j].rank < 500) {
					PrecisionAt500[i]++;
					PrecisionAt1000[i]++;
				} else
					PrecisionAt1000[i]++;
				ExactPrecision[i] += (double)(j+1)
					/ (1d + relevantRetrieved[j].rank);
				relevantRetrieved[j].precision =
					(double)(j+1)
						/ (1d + relevantRetrieved[j].rank);
				relevantRetrieved[j].recall =
					(double)(j+1) / numberOfRelevant[i];
			}
			for (int j = 0; j < relevantRetrieved.length; j++) {
				if (relevantRetrieved[j].recall
					>= 0d && relevantRetrieved[j].precision
					>= PrecisionAt0_0[i])
					PrecisionAt0_0[i] = relevantRetrieved[j].precision;
				if (relevantRetrieved[j].recall
					>= 0.1d && relevantRetrieved[j].precision
					>= PrecisionAt0_1[i])
					PrecisionAt0_1[i] = relevantRetrieved[j].precision;
				if (relevantRetrieved[j].recall
					>= 0.2d && relevantRetrieved[j].precision
					>= PrecisionAt0_2[i])
					PrecisionAt0_2[i] = relevantRetrieved[j].precision;
				if (relevantRetrieved[j].recall
					>= 0.3d && relevantRetrieved[j].precision
					>= PrecisionAt0_3[i])
					PrecisionAt0_3[i] = relevantRetrieved[j].precision;
				if (relevantRetrieved[j].recall
					>= 0.4d && relevantRetrieved[j].precision
					>= PrecisionAt0_4[i])
					PrecisionAt0_4[i] = relevantRetrieved[j].precision;
				if (relevantRetrieved[j].recall
					>= 0.5d && relevantRetrieved[j].precision
					>= PrecisionAt0_5[i])
					PrecisionAt0_5[i] = relevantRetrieved[j].precision;
				if (relevantRetrieved[j].recall
					>= 0.6d && relevantRetrieved[j].precision
					>= PrecisionAt0_6[i])
					PrecisionAt0_6[i] = relevantRetrieved[j].precision;
				if (relevantRetrieved[j].recall
					>= 0.7d && relevantRetrieved[j].precision
					>= PrecisionAt0_7[i])
					PrecisionAt0_7[i] = relevantRetrieved[j].precision;
				if (relevantRetrieved[j].recall
					>= 0.8d && relevantRetrieved[j].precision
					>= PrecisionAt0_8[i])
					PrecisionAt0_8[i] = relevantRetrieved[j].precision;
				if (relevantRetrieved[j].recall
					>= 0.9d && relevantRetrieved[j].precision
					>= PrecisionAt0_9[i])
					PrecisionAt0_9[i] = relevantRetrieved[j].precision;
				if (relevantRetrieved[j].recall
					>= 1.0d && relevantRetrieved[j].precision
					>= PrecisionAt1_0[i])
					PrecisionAt1_0[i] = relevantRetrieved[j].precision;
			}
			//Modified by G.AMATI 7th May 2002
			if (numberOfRelevant[i] > 0)
				ExactPrecision[i] /= ((double) numberOfRelevant[i]);
			else{
//				System.out.println("been --");
//				numberOfEffQuery--;
			}
				
			
			if (numberOfRelevant[i] > 0)
				RPrecision[i] /= ((double) numberOfRelevant[i]);
			AveragePrecision += ExactPrecision[i];
			this.AveragePrecisionOfEachQuery[i] = ExactPrecision[i];
			RelevantPrecision += RPrecision[i];
			PrecAt1 += (double) PrecisionAt1[i];
			PrecAt2 += ((double) PrecisionAt2[i]) / ((double) 2);
			PrecAt3 += ((double) PrecisionAt3[i]) / ((double) 3);
			PrecAt4 += ((double) PrecisionAt4[i]) / ((double) 4);
			//END of modification
			PrecAt5 += ((double) PrecisionAt5[i]) / ((double) 5);
			PrecAt10 += ((double) PrecisionAt10[i]) / ((double) 10);
			PrecAt15 += ((double) PrecisionAt15[i]) / ((double) 15);
			PrecAt20 += ((double) PrecisionAt20[i]) / ((double) 20);
			PrecAt30 += ((double) PrecisionAt30[i]) / ((double) 30);
			PrecAt50 += ((double) PrecisionAt50[i]) / ((double) 50);
			PrecAt100 += ((double) PrecisionAt100[i]) / ((double) 100);
			PrecAt200 += ((double) PrecisionAt200[i]) / ((double) 200);
			PrecAt500 += ((double) PrecisionAt500[i]) / ((double) 500);
			PrecAt1000 += ((double) PrecisionAt1000[i]) / ((double) 1000);
		}
		for (int i = 0; i < effQueryCounter; i++) {
			PrecAt0Percent += (double) PrecisionAt0_0[i];
			PrecAt10Percent += (double) PrecisionAt0_1[i];
			PrecAt20Percent += (double) PrecisionAt0_2[i];
			PrecAt30Percent += (double) PrecisionAt0_3[i];
			PrecAt40Percent += (double) PrecisionAt0_4[i];
			PrecAt50Percent += (double) PrecisionAt0_5[i];
			PrecAt60Percent += (double) PrecisionAt0_6[i];
			PrecAt70Percent += (double) PrecisionAt0_7[i];
			PrecAt80Percent += (double) PrecisionAt0_8[i];
			PrecAt90Percent += (double) PrecisionAt0_9[i];
			PrecAt100Percent += (double) PrecisionAt1_0[i];
		}
		PrecAt0Percent /= (double) numberOfEffQuery;
		PrecAt10Percent /= (double) numberOfEffQuery;
		PrecAt20Percent /= (double) numberOfEffQuery;
		PrecAt30Percent /= (double) numberOfEffQuery;
		PrecAt40Percent /= (double) numberOfEffQuery;
		PrecAt50Percent /= (double) numberOfEffQuery;
		PrecAt60Percent /= (double) numberOfEffQuery;
		PrecAt70Percent /= (double) numberOfEffQuery;
		PrecAt80Percent /= (double) numberOfEffQuery;
		PrecAt90Percent /= (double) numberOfEffQuery;
		PrecAt100Percent /= (double) numberOfEffQuery;
		PrecAt1 /= (double) numberOfEffQuery;
		//Modified by G.AMATI 7th May 2002
		PrecAt2 /= (double) numberOfEffQuery;
		PrecAt3 /= (double) numberOfEffQuery;
		PrecAt4 /= (double) numberOfEffQuery;
		//END of modification
		PrecAt5 /= (double) numberOfEffQuery;
		PrecAt10 /= (double) numberOfEffQuery;
		PrecAt15 /= (double) numberOfEffQuery;
		PrecAt20 /= (double) numberOfEffQuery;
		PrecAt30 /= (double) numberOfEffQuery;
		PrecAt50 /= (double) numberOfEffQuery;
		PrecAt100 /= (double) numberOfEffQuery;
		PrecAt200 /= (double) numberOfEffQuery;
		PrecAt500 /= (double) numberOfEffQuery;
		PrecAt1000 /= (double) numberOfEffQuery;
		AveragePrecision /= (double) numberOfEffQuery;
		RelevantPrecision /= (double) numberOfEffQuery;
	}
	/**
	 * Output the evaluation result of each query to the specific file.
	 * @param resultEvalFilename String the name of the file in which to 
	 *        save the evaluation results.
	 */
	public void writeEvaluationResultOfEachQuery(String resultEvalFilename) {
		try {
			final PrintWriter out = new PrintWriter(Files.writeFileWriter(resultEvalFilename));
			final StringBuilder sb = new StringBuilder();
			for (int i = 0; i < this.queryNo.length; i++)
				sb.append(
					queryNo[i]
						+ " "
						+ Rounding.toString(
							this.AveragePrecisionOfEachQuery[i],
							4)
						+ ApplicationSetup.EOL);
			out.print(sb.toString());
			out.close();
		} catch (IOException fnfe) {
			logger.error("Couldn't create evaluation file "+ resultEvalFilename , fnfe);
		}
	}
	
	public void writeEvaluationResultOfEachQuery(PrintWriter out) {
		try {
			final StringBuilder sb = new StringBuilder();
			for (int i = 0; i < this.queryNo.length; i++)
				sb.append(
					queryNo[i]
						+ " "
						+ Rounding.toString(
							this.AveragePrecisionOfEachQuery[i],
							4)
						+ ApplicationSetup.EOL);
			out.print(sb.toString());
		} catch (Exception fnfe) {
			logger.error("Couldn't create evaluation file "+ out.toString() , fnfe);
		}
	}
	/**
	 * Output the evaluation result to the specific file.
	 * @param out java.io.PrintWriter the stream to which the results are printed.
	 */
	public void writeEvaluationResult(PrintWriter out) {
		out.println("____________________________________");
		out.println("Number of queries  = " + numberOfEffQuery);
		out.println("Retrieved          = " + totalNumberOfRetrieved);
		out.println("Relevant           = " + totalNumberOfRelevant);
		out.println("Relevant retrieved = " + totalNumberOfRelevantRetrieved);
		out.println("____________________________________");
		out.println(
			"Average Precision: " + Rounding.toString(AveragePrecision, 4));
		out.println(
			"R Precision      : " + Rounding.toString(RelevantPrecision, 4));
		out.println("____________________________________");
		out.println("Precision at    1: " + Rounding.toString(PrecAt1, 4));
		out.println("Precision at    2: " + Rounding.toString(PrecAt2, 4));
		out.println("Precision at    3: " + Rounding.toString(PrecAt3, 4));
		out.println("Precision at    4: " + Rounding.toString(PrecAt4, 4));
		out.println("Precision at    5: " + Rounding.toString(PrecAt5, 4));
		out.println("Precision at   10: " + Rounding.toString(PrecAt10, 4));
		out.println("Precision at   15: " + Rounding.toString(PrecAt15, 4));
		out.println("Precision at   20: " + Rounding.toString(PrecAt20, 4));
		out.println("Precision at   30: " + Rounding.toString(PrecAt30, 4));
		out.println("Precision at   50: " + Rounding.toString(PrecAt50, 4));
		out.println("Precision at  100: " + Rounding.toString(PrecAt100, 4));
		out.println("Precision at  200: " + Rounding.toString(PrecAt200, 4));
		out.println("Precision at  500: " + Rounding.toString(PrecAt500, 4));
		out.println("Precision at 1000: " + Rounding.toString(PrecAt1000, 4));
		out.println("____________________________________");
		out.println(
			"Precision at   0%: " + Rounding.toString(PrecAt0Percent, 4));
		out.println(
			"Precision at  10%: " + Rounding.toString(PrecAt10Percent, 4));
		out.println(
			"Precision at  20%: " + Rounding.toString(PrecAt20Percent, 4));
		out.println(
			"Precision at  30%: " + Rounding.toString(PrecAt30Percent, 4));
		out.println(
			"Precision at  40%: " + Rounding.toString(PrecAt40Percent, 4));
		out.println(
			"Precision at  50%: " + Rounding.toString(PrecAt50Percent, 4));
		out.println(
			"Precision at  60%: " + Rounding.toString(PrecAt60Percent, 4));
		out.println(
			"Precision at  70%: " + Rounding.toString(PrecAt70Percent, 4));
		out.println(
			"Precision at  80%: " + Rounding.toString(PrecAt80Percent, 4));
		out.println(
			"Precision at  90%: " + Rounding.toString(PrecAt90Percent, 4));
		out.println(
			"Precision at 100%: " + Rounding.toString(PrecAt100Percent, 4));
		out.println("____________________________________");
		out.println(
			"Average Precision: " + Rounding.toString(AveragePrecision, 4));
		if(logger.isInfoEnabled()) logger.info("AP: "
				+ Rounding.toString(AveragePrecision, 4)
				+ ", P@20: " + Rounding.toString(PrecAt20, 4) 
				+", qNum: " + numberOfEffQuery);
		// System.out.println("Average Precision: " +
		// Rounding.toString(AveragePrecision, 4));
	}
}
