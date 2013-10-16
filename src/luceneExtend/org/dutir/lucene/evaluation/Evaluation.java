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
 * The Original Code is Evaluation.java.
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
import java.io.IOException;
import java.io.PrintWriter;
import java.io.OutputStreamWriter;
import org.apache.log4j.Logger;
import org.dutir.lucene.util.Files;
/**
 * An abstract class for evaluating the retrieval results.
 * @author Gianni Amati, Ben He, Vassilis Plachouras
 * @version $Revision: 1.25 $
 */
public abstract class Evaluation {
	protected Logger logger = Logger.getLogger(this.getClass());
	/**
	 * A structure of a record of retrieved document.
	 */
	static public class Record {
		/** The topic number. */
		String queryNo;
		/** The rank of the document. */
		int rank;
		/** The document ID. */
		String docID;
		/** The precision at this document. */
		double precision;
		/** The recall at this document. */
		double recall;
		public Record(String queryNo, String docID, int rank) {
			this.queryNo = queryNo;
			this.rank = rank;
			this.docID = docID;
		}
	}
	
	/**
	 * A structure of all the records in the qrels files.
	 */
	public TRECQrelsInMemory qrels = new TRECQrelsInMemory();
	
	/**
	 * Evaluates the given result file for the given qrels file.
	 * All subclasses must implement this method.
	 * @param resultFilename java.lang.String the filename of the result 
	 *        file to evaluate.
	 */
	abstract public void evaluate(String resultFilename);
	
	/**
	 * Output the evaluation result to standard output
	 */
	public void writeEvaluationResult() {
		writeEvaluationResult(new PrintWriter(new OutputStreamWriter(System.out)));
	}
	/**
	 * The abstract method that evaluates and prints
	 * the results. All the subclasses of Evaluation
	 * must implement this method.
	 * @param out java.io.PrintWriter
	 */
	abstract public void writeEvaluationResult(PrintWriter out);
	
	abstract public void writeEvaluationResultOfEachQuery(PrintWriter out);
	/**
	 * Output the evaluation result of each query to the specific file.
	 * @param evaluationResultFilename String the name of the file in which to 
	 *        save the evaluation results.
	 */
	abstract public void writeEvaluationResultOfEachQuery(String evaluationResultFilename);

	
	/**
	 * Output the evaluation result to the specific file.
	 * @param resultEvalFilename java.lang.String the filename of 
	 *        the file to output the result.
	 */
	public void writeEvaluationResult(String resultEvalFilename) {
		try {
			final PrintWriter out = new PrintWriter(Files.writeFileWriter(resultEvalFilename));
			writeEvaluationResult(out);
			out.close();
		} catch (IOException fnfe) {
			logger.fatal(
				"File not found exception occurred when trying to write to file" +resultEvalFilename,
					fnfe);
		}
	}
	
	public void writeDetailEvaluationResult(String resultEvalFilename) {
		try {
			final PrintWriter out = new PrintWriter(Files.writeFileWriter(resultEvalFilename));
			writeEvaluationResult(out);
			writeEvaluationResultOfEachQuery(out);
			out.close();
		} catch (IOException fnfe) {
			logger.fatal(
				"File not found exception occurred when trying to write to file" +resultEvalFilename,
					fnfe);
		}
	}
	
}
