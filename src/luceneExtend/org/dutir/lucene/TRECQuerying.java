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
package org.dutir.lucene;

import gnu.trove.THashSet;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Properties;
import java.util.Random;
import java.util.zip.GZIPOutputStream;

import org.apache.log4j.Logger;
import org.apache.lucene.OutputFormat;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.postProcess.PostProcess;
import org.apache.lucene.queryParser.AnalyzerManager;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.RBooleanClause;
import org.apache.lucene.search.RBooleanQuery;
import org.apache.lucene.search.RTermQuery;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.search.TopDocCollector;
import org.apache.lucene.search.TopDocs;
import org.dutir.lucene.evaluation.TRECQrelsInMemory;
import org.dutir.lucene.query.LuceneQueryParser;
import org.dutir.lucene.util.ApplicationSetup;
import org.dutir.lucene.util.TermsCache;

public class TRECQuerying {

	/** The logger used */
	protected static final Logger logger = Logger.getLogger(TRECQuerying.class);
	protected static TermsCache termCache = TermsCache.getInstance();
	static boolean LanguageModel = Boolean.parseBoolean(ApplicationSetup
			.getProperty("Lucene.Search.LanguageModel", "false"));
	protected static boolean removeQueryPeriods = false;

	protected static boolean normalizeTage = Boolean
			.parseBoolean(ApplicationSetup.getProperty(
					"Lucene.TRECQuerying.NormalizeQueryTag", "false"));

	/** random number generator */
	protected static final Random random = new Random();

	/** The number of matched queries. */
	protected int matchingCount = 0;

	/** The file to store the output to. */
	protected PrintWriter resultFile = null;

	/** The filename of the last file results were output to. */
	protected String resultsFilename;

	static int start = Integer.parseInt(ApplicationSetup.getProperty(
			"TRECQuerying.start", "0"));
	static int end = Integer.parseInt(ApplicationSetup.getProperty(
			"TRECQuerying.end", "1000"));
	ArrayList<String> postList = new ArrayList<String>();

	private boolean firstRound = Boolean.parseBoolean(ApplicationSetup
			.getProperty("Lucene.TRECQuerying.firstRound", "true"));

	public String retrievalPara = "";
	protected OutputFormat printer;

	/**
	 * The name of the weighting model that is used for retrieval. Defaults to
	 * PL2
	 */
	protected String wModel = "PL2";

	/** A TREC specific output field. */
	protected static String ITERATION = ApplicationSetup.getProperty(
			"trec.iteration", "Q");

	/**
	 * The method - ie the weighting model and parameters. Examples:
	 * <tt>TF_IDF</tt>, <tt>PL2c1.0</tt>
	 */
	protected String method = ApplicationSetup.getProperty(
			"TRECQuerying.runname", "LabLucene");
	protected boolean method_stag = Boolean.parseBoolean(ApplicationSetup
			.getProperty("TRECQuerying.runname.specified", "false"));
	/**
	 * What parse to parse the batch topic files. Configured by property
	 * <tt>trec.topics.parser</tt>
	 */
	protected String TopicsParser = ApplicationSetup.getProperty(
			"Lucene.topics.parser", "TRECQuery");

	protected LuceneQueryParser querySource;

	// protected RMultiFieldQueryParser queryParser;
	protected Searcher searcher = null;
	protected String searchFeilds[] = null;
	protected Analyzer analyzer = null;
	private static int buf_size = 1024 * 1024;

	private void setup() {
		String fieldstr = ApplicationSetup.getProperty("Lucene.SearchField",
				"title,content");

		this.searchFeilds = fieldstr.split("\\s*,\\s*");
		analyzer = AnalyzerManager.getFromPropertyFile();

		String postProcess = ApplicationSetup.getProperty("Lucene.PostProcess",
				"");
		String postProcesses[] = postProcess.split("\\s*,\\s*");
		this.searcher = ISManager.getSearcheFromPropertyFile();

		for (int i = 0; i < postProcesses.length; i++) {
			String pp = postProcesses[i];
			if (pp.length() > 0) {
				if (pp.indexOf(".") == -1) {
					pp = "org.apache.lucene.postProcess." + pp;
				}
				this.addPostPrcessor(pp);
			}
		}
	}

	/**
	 * TRECQuerying default constructor initialises the inverted index, the
	 * lexicon and the document index structures.
	 */
	public TRECQuerying() {
		this.querySource = this.getQueryParser();
		setup();
		this.printer = getOutputFormat(this.searcher);
	}

	/**
	 * TRECQuerying constructor initialises the specified inverted index, the
	 * lexicon and the document index structures.
	 * 
	 * @param i
	 *            The specified index.
	 */
	public TRECQuerying(Searcher searcher) {

		this.querySource = this.getQueryParser();
		this.searcher = searcher;
		this.printer = getOutputFormat(this.searcher);
	}

	protected OutputFormat getOutputFormat(Searcher searcher) {
		OutputFormat rtr = null;
		try {
			String className = ApplicationSetup.getProperty(
					"Lucene.TRECQuerying.outputformat",
					TRECDocnoOutputFormat.class.getName());
			if (!className.contains("."))
				className = "org.dutir.lucene." + className;
			
			rtr = Class.forName(className).asSubclass(OutputFormat.class)
					.getConstructor(Searcher.class).newInstance(searcher);
			if (logger.isInfoEnabled())
				logger.info("using " + className);
		} catch (Exception e) {
			logger.error(e);
			throw new IllegalArgumentException(
					"Could not load TREC OutputFormat class", e);
		}
		return rtr;
	}

	/**
	 * Get the sequential number of the next result file in the results folder.
	 * 
	 * @param resultsFolder
	 *            The path of the results folder.
	 * @return The sequential number of the next result file in the results
	 *         folder.
	 */
	protected String getNextQueryCounter(String resultsFolder) {
		String type = ApplicationSetup.getProperty("trec.querycounter.type",
				"sequential").toLowerCase();
		if (type.equals("sequential"))
			return getSequentialQueryCounter(resultsFolder);
		// else if (type.equals("random"))
		// {
		return getRandomQueryCounter();
		// }
	}

	/**
	 * Get a random number between 0 and 1000.
	 * 
	 * @return A random number between 0 and 1000.
	 */
	protected String getRandomQueryCounter() {
		return ""
		/* seconds since epoch */
		+ (System.currentTimeMillis() / 1000) + "-"
		/* random number in range 0-1000 */
		+ random.nextInt(1000);
	}

	/**
	 * Get the sequential number of the current result file in the results
	 * folder.
	 * 
	 * @param resultsFolder
	 *            The path of the results folder.
	 * @return The sequential number of the current result file in the results
	 *         folder.
	 */
	protected String getSequentialQueryCounter(String resultsFolder) {
		/* TODO: NFS safe locking */
		File fx = new File(resultsFolder, "querycounter");
		int counter = 0;
		if (!fx.exists()) {
			try {
				BufferedWriter bufw = new BufferedWriter(new FileWriter(fx));
				bufw.write(counter + ApplicationSetup.EOL);
				bufw.close();
			} catch (IOException ioe) {
				logger
						.fatal(
								"Input/Output exception while creating querycounter. Stack trace follows.",
								ioe);
			}
		} else
			try {
				BufferedReader buf = new BufferedReader(new FileReader(fx));
				String s = buf.readLine();
				counter = (new Integer(s)).intValue();
				counter++;
				buf.close();
				BufferedWriter bufw = new BufferedWriter(new FileWriter(fx));
				bufw.write(counter + ApplicationSetup.EOL);
				bufw.close();
			} catch (Exception e) {
				logger
						.fatal("Exception occurred when defining querycounter",
								e);
			}
		return "" + counter;
	}

	public String getInfo() {
		return null;
	}

	/**
	 * Returns a PrintWriter used to store the results.
	 * 
	 * @param predefinedName
	 *            java.lang.String a non-standard prefix for the result file.
	 * @return a handle used as a destination for storing results.
	 */
	public PrintWriter getResultFile(String predefinedName) {
		final String PREDEFINED_RESULT_PREFIX = "prob";
		PrintWriter resultFile = null;
		File fx = new File(ApplicationSetup.TREC_RESULTS);
		if (!fx.exists())
			if (!fx.mkdir()) {
				logger.error("Could not create results directory ("
						+ ApplicationSetup.TREC_RESULTS
						+ ") - permissions problem?");
				return null;
			}
		String querycounter = getNextQueryCounter(ApplicationSetup.TREC_RESULTS);
		try {
			String prefix = null;
			if (predefinedName == null || predefinedName.equals(""))
				prefix = PREDEFINED_RESULT_PREFIX;
			else
				prefix = predefinedName;

			resultsFilename = ApplicationSetup.TREC_RESULTS + "/" + prefix
					+ "_" + querycounter
					+ ApplicationSetup.TREC_GZ_RESULTS_SUFFIX;
			// resultFile = new PrintWriter(new BufferedWriter(new FileWriter(
			// new File(resultsFilename))));
			resultFile = new PrintWriter(new GZIPOutputStream(
					new BufferedOutputStream(new FileOutputStream(new File(
							resultsFilename)), buf_size)));

			// ***********write setting file *************//
			String settingFileName = ApplicationSetup.TREC_RESULTS + "/"
					+ prefix + "_" + querycounter
					+ ApplicationSetup.TREC_RESULTS_SETTING_SUFFIX;

			PrintWriter settingWriter = new PrintWriter(new BufferedWriter(
					new FileWriter(new File(settingFileName))));
			settingWriter
					.write("Retrieval Paras: " + this.retrievalPara + "\n");
			settingWriter.write("*****************************************\n");
			list(settingWriter, ApplicationSetup.getProperties());
			settingWriter
					.write("******************System Properties***********************\n");
			list(settingWriter, System.getProperties());
			settingWriter.close();
			if (logger.isInfoEnabled())
				logger.info("Writing results to " + resultsFilename);
		} catch (IOException e) {
			logger
					.error(
							"Input/Output exception while creating the result file. Stack trace follows.",
							e);
		}
		return resultFile;
	}

	private void list(PrintWriter out, Properties properties) {
		Hashtable h = new Hashtable();
		for (Enumeration e = properties.keys(); e.hasMoreElements();) {
			String key = (String) e.nextElement();
			out.println(key + "=" + properties.get(key));
		}
	}

	public void addPostPrcessor(String postName) {
		postList.add(postName);
	}

	/**
	 * 
	 * @param collector
	 * @return Description of this run name in the result file.
	 */
	private String getMethodDes(TopDocCollector collector) {
		if (method_stag) {
			return method;
		} else if (collector != null) {
			method = collector.getInfo();
			return querySource.getInfo() + method;
		}

		String model = ApplicationSetup.getProperty(
				"Lucene.Search.WeightingModel", "UnkownModel");
		String ppModel = ApplicationSetup.getProperty("Lucene.PostProcess",
				"UnkownPPModel");
		String qModel = ApplicationSetup.getProperty(
				"Lucene.QueryExpansion.Model", "UnkownQEModel");
		int n_doc = ApplicationSetup.EXPANSION_DOCUMENTS;
		int n_term = ApplicationSetup.EXPANSION_TERMS;
		method = model + "_" + ppModel + "_" + qModel + "_" + n_doc + "_"
				+ n_term;

		return method;
	}

	
	
	protected void processQueryAndWrite(RBooleanQuery bquery) {

		//this is not for speeding up QE process. 
		boolean shortFirsPass = Boolean.parseBoolean(ApplicationSetup.getProperty("trec.shortFirsPass", "false"));
		int colNum = end;
		if (shortFirsPass &&  postList.size() > 0) {
			colNum = ApplicationSetup.EXPANSION_DOCUMENTS + 1;
			if (logger.isDebugEnabled())
				logger.debug("retrieved " + colNum
						+ " results in the first round");
		}
		TopDocCollector collector = new TopDocCollector(colNum);
		// BooleanQuery query;
		try {
			if (logger.isDebugEnabled())
				logger.debug(bquery.getTopicId() + " LQuery: "
						+ bquery.toString());
			long start = System.currentTimeMillis();
			if (firstRound) {
				searcher.search(bquery, collector);
				if (logger.isDebugEnabled())
					logger.debug("first round time: "
							+ (System.currentTimeMillis() - start) / 1000f
							+ " S");
			}
			collector.setInfo(getRTermQueryInfo(bquery)); // setup the returning
															// info of basic
															// retrieval model.
			if(ApplicationSetup.PostProcessTag) setExpansionFileName(collector);
			for (int i = 0; ApplicationSetup.PostProcessTag && i < postList.size(); i++) {
				// change TopDocCollector according to the post processing
				// algorithm
				PostProcess pp = (PostProcess) Class.forName(postList.get(i))
						.newInstance();
				collector = pp.postProcess(bquery, collector, this.searcher);
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		if (resultFile == null) {
			method = getMethodDes(collector);
			resultFile = getResultFile(method);
		}
		String queryId = bquery.getTopicId();
		this.printer.printResults(queryId, resultFile, collector);
	}

	private void setExpansionFileName(TopDocCollector collector) {
		String prefix = collector.getInfo();
		String feedbackname = ApplicationSetup.getProperty(
				"Rocchio.Feedback.filename", "");
		if (feedbackname.matches(ApplicationSetup.TREC_RESULTS + "/" + prefix
				+ "_[0-9]{1,5}\\.gz"))
			return;

		File resultDir = new File(ApplicationSetup.TREC_RESULTS);
		String[] filenames = resultDir.list(gzfilter);
		boolean tag = false;
		for (int i = 0; filenames !=null && i < filenames.length; i++) {
			if (filenames[i].matches(prefix + "_[0-9]{1,5}\\.gz")) {
				ApplicationSetup.setProperty("Rocchio.Feedback.filename",
						ApplicationSetup.TREC_RESULTS + "/" + filenames[i]);
				if (logger.isInfoEnabled() && !firstRound)
					logger.info("feedback from file: " + filenames[i]);
				tag = true;
				break;
			}
		}
		if (!tag && !firstRound)
			logger
					.warn("no feedback file found, the filename is supposed to be: "
							+ prefix + "???.gz");
	}

	static GZFilter gzfilter = new GZFilter();

	static class GZFilter implements FilenameFilter {
		public boolean accept(File arg0, String arg1) {
			if (arg1.endsWith("\\.gz"))
				;
			return true;
		}

	}

	/**
	 * 
	 * @param bquery
	 * @return the description of a RTermQuery, that is the basic retrieval
	 *         (without QE) description
	 */
	protected String getRTermQueryInfo(RBooleanQuery bquery) {
		RBooleanClause[] bclause = bquery.getClauses();
		try {
			for (int i = 0; i < bclause.length; i++) {
				Query rtq = bclause[i].getQuery();

				if (rtq instanceof RTermQuery) {
					((RTermQuery) rtq).createWeight(searcher);
					return ((RTermQuery) rtq).getInfo();
				} else if (rtq instanceof BooleanQuery) {
					RTermQuery tmp = getRTermQuery((BooleanQuery) rtq);
					tmp.createWeight(searcher);
					if (tmp != null)
						return tmp.getInfo();
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return "";
	}

	private RTermQuery getRTermQuery(BooleanQuery bquery) {
		BooleanClause[] bclause = bquery.getClauses();
		for (int i = 0; i < bclause.length; i++) {
			Query rtq = bclause[i].getQuery();

			if (rtq instanceof RTermQuery) {
				return ((RTermQuery) rtq);
			} else if (rtq instanceof BooleanQuery) {
				return getRTermQuery((BooleanQuery) rtq);
			}
		}
		return null;
	}

	/**
	 * Get the query parser that is being used.
	 * 
	 * @return The query parser that is being used.
	 */
	protected LuceneQueryParser getQueryParser() {
		String topicsFile = null;
		LuceneQueryParser rtr = null;
		try {
			Class queryingClass = Class
					.forName(TopicsParser.indexOf('.') > 0 ? TopicsParser
							: "org.dutir.lucene.query." + TopicsParser);

			if ((topicsFile = ApplicationSetup.getProperty("trec.topics", null)) != null) {
				Class[] types = { String.class };
				Object[] params = { topicsFile };
				rtr = (LuceneQueryParser) queryingClass.getConstructor(types)
						.newInstance(params);
			} else {
				rtr = (LuceneQueryParser) queryingClass.newInstance();
			}

		} catch (Exception e) {
			logger.error("Error instantiating topic file tokeniser called "
					+ TopicsParser, e);
		}
		return rtr;
	}

	protected void normalise(RBooleanQuery query) {
		RBooleanClause[] bclause = query.getClauses();
		if (bclause.length == 1) {
			RBooleanClause bc = bclause[0];
			Query q = bc.getQuery();
			if (q instanceof RBooleanQuery) {
				bclause = ((RBooleanQuery) q).getClauses();

			}
		}
		// normalize term frequency
		float max = 0;
		float total = 0;
		for (int i = 0; i < bclause.length; i++) {
			Query rtq = bclause[i].getQuery();
			if (rtq instanceof RTermQuery) {
				float occur = rtq.getOccurNum();
				total += occur;
				if (max < occur) {
					max = occur;
				}
			}
		}

		for (int i = 0; i < bclause.length; i++) {
			Query rtq = bclause[i].getQuery();
			if (LanguageModel) {
				if (rtq instanceof RTermQuery) {
					rtq.setOccurNum(rtq.getOccurNum() / total);
				} else {
					rtq.setOccurNum(rtq.getOccurNum() / total);
					logger.warn("the query " + rtq + " is not expected for QE");
				}
			} else {
				if (rtq instanceof RTermQuery) {
					rtq.setOccurNum(rtq.getOccurNum() / max);
				} else {
					rtq.setOccurNum(rtq.getOccurNum() / max);
					logger.warn("the query " + rtq + " is not expected for QE");
				}
			}
		}

	}

	/**
	 * Performs the matching using the specified weighting model from the setup
	 * and possibly a combination of evidence mechanism. It parses the file with
	 * the queries creates the file of results, and for each query, gets the
	 * relevant documents, scores them, and outputs the results to the result
	 * file.
	 * <p>
	 * <b>Queries</b><br />
	 * Queries are parse from a file. The filename can be expressed in the
	 * <tt>trec.topics</tt> property, or else the file named in the property
	 * <tt>trec.topics.list</tt> property is read, and the each file in that is
	 * used for queries.
	 * 
	 * @param c
	 *            the value of c.
	 * @param c_set
	 *            specifies whether a value for c has been specified.
	 * @return String the filename that the results have been written to
	 */
	public String processQueries(double c, boolean c_set) {
		final long startTime = System.currentTimeMillis();
		matchingCount = 0;
		querySource.reset();
		boolean doneSomeMethods = false;
		boolean doneSomeTopics = false;
		try {
			String methodName = null;
			if ((methodName = ApplicationSetup.getProperty(
					"Lucene.Search.WeightingModel", null)) != null) {
				wModel = methodName;
				// iterating through the queries
				while (querySource.hasMoreQueries()) {

					// process the query
					long processingStart = System.currentTimeMillis();
					RBooleanQuery bquery = querySource.getNextQuery(
							this.searchFeilds, analyzer);
					if (bquery == null)
						break;
					if (normalizeTage || LanguageModel) {
						normalise(bquery);
					}
					processQueryAndWrite(bquery);
					matchingCount++;
					long processingEnd = System.currentTimeMillis();
					if (logger.isInfoEnabled())
						logger.info("Time to process query: "
								+ bquery.getTopicId() + " -- "
								+ ((processingEnd - processingStart) / 1000.0D)
								+ " S");
					doneSomeTopics = true;
				}
				querySource.reset();
				this.finishedQueries();
				// after finishing with a batch of queries, close the result
				// file
				doneSomeMethods = true;
			} else {
				BufferedReader methodsFile = new BufferedReader(new FileReader(
						ApplicationSetup.TREC_MODELS));
				while ((methodName = methodsFile.readLine()) != null) {
					// ignore empty lines, or lines starting with # from the
					// methods file.
					if (methodName.startsWith("#") || methodName.equals(""))
						continue;
					wModel = methodName;
					// iterating through the queries
					while (querySource.hasMoreQueries()) {
						// process the query
						long processingStart = System.currentTimeMillis();
						RBooleanQuery bquery = querySource.getNextQuery(
								this.searchFeilds, analyzer);
						if (bquery == null)
							break;
						processQueryAndWrite(bquery);
						long processingEnd = System.currentTimeMillis();
						if (logger.isInfoEnabled())
							logger
									.info("Time to process query: "
											+ bquery.getTopicId()
											+ " -- "
											+ +((processingEnd - processingStart) / 1000.0D));
						doneSomeTopics = true;
					}
					querySource.reset();
					this.finishedQueries();
					doneSomeMethods = true;
				}
				methodsFile.close();
			}
			termCache.save();
		} catch (IOException ioe) {
			logger
					.fatal(
							"Input/Output exception while performing the matching. Stack trace follows.",
							ioe);
		}
		if (!doneSomeTopics)
			logger.error("No queries were processed. Please check the file "
					+ ApplicationSetup.TREC_TOPICS_LIST);
		if (!doneSomeMethods)
			logger.error("No models were specified. Please check the file "
					+ ApplicationSetup.TREC_MODELS);
		if (doneSomeTopics && doneSomeMethods)
			if (logger.isInfoEnabled())
				logger.info("Finished topics, executed " + matchingCount
						+ " queries in "
						+ ((System.currentTimeMillis() - startTime) / 1000)
						+ " seconds, results written to " + resultsFilename);
		return resultsFilename;
	}

	/**
	 * After finishing with a batch of queries, close the result file
	 * 
	 */
	protected void finishedQueries() {
		if (resultFile != null) {
			resultFile.flush();
			resultFile.close();
		}

		resultFile = null;
	}

//	/** interface for adjusting the output of TRECQuerying */
//	public static interface OutputFormat {
//		public void printResults(String queryID, final PrintWriter pw,
//				final TopDocCollector collector);
//	}

	String docidField = null;

	private String getIdFieldName() {
		if (docidField == null) {
			docidField = ApplicationSetup.getProperty("TrecDocTags.idtag",
					"DOCNO");
		}
		return docidField;
	}

//	static class TRECDocnoOutputFormat implements OutputFormat {
//		Searcher searcher;
//
//		public TRECDocnoOutputFormat(Searcher searcher) {
//			this.searcher = searcher;
//		}
//
//		String docidField = null;
//
//		private String getIdFieldName() {
//			if (docidField == null) {
//				docidField = ApplicationSetup.getProperty("TrecDocTags.idtag",
//						"DOCNO");
//			}
//			return docidField;
//		}
//
//		/**
//		 * Prints the results for the given search request, using the specified
//		 * destination.
//		 * 
//		 * @param pw
//		 *            PrintWriter the destination where to save the results.
//		 */
//		public void printResults(String queryID, final PrintWriter pw,
//				final TopDocCollector collector) {
//			TopDocs topDocs = collector.topDocs();
//
//			int len = topDocs.totalHits;
//			int maximum = Math.min(topDocs.scoreDocs.length, end);
//
//			// if (minimum > set.getResultSize())
//			// minimum = set.getResultSize();
//			final String iteration = ITERATION + "0";
//			final String queryIdExpanded = queryID + " " + iteration + " ";
//			final String methodExpanded = " " + "LabLucene"
//					+ ApplicationSetup.EOL;
//			StringBuilder sbuffer = new StringBuilder();
//			// the results are ordered in descending order
//			// with respect to the score.
//			int limit = 10000;
//			int counter = 0;
//			for (int i = start; i < maximum; i++) {
//				int docid = topDocs.scoreDocs[i].doc;
//
//				Document doc = null;
//				String filename = null;
//				try {
//					doc = searcher.doc(docid);
//					filename = doc.get(getIdFieldName());
//				} catch (Exception e) {
//					e.printStackTrace();
//				}
//				float score = topDocs.scoreDocs[i].score;
//
//				if (filename != null && !filename.equals(filename.trim())) {
//					if (logger.isDebugEnabled())
//						logger.debug("orginal doc name not trimmed: |"
//								+ filename + "|");
//				} else if (filename == null) {
//					logger.error("docno does not exist: " + doc.toString());
//					logger.error("inner docid: " + docid + ", score:" + score);
//					if (docid > 0) {
//						try {
//							logger.error("previous docno: "
//									+ this.searcher.doc(docid - 1).toString());
//						} catch (Exception e) {
//							e.printStackTrace();
//						}
//					}
//					continue;
//				}
//				sbuffer.append(queryIdExpanded);
//				sbuffer.append(filename.trim());
//				sbuffer.append(" ");
//
//				sbuffer.append(i);
//				sbuffer.append(" ");
//				sbuffer.append(score);
//
//				sbuffer.append(methodExpanded);
//			}
//			pw.write(sbuffer.toString());
//		}
//	}

//	static class TRECDocidOutputFormat implements OutputFormat {
//		Searcher searcher;
//
//		public TRECDocidOutputFormat(Searcher searcher) {
//			this.searcher = searcher;
//		}
//
//		/**
//		 * Prints the results for the given search request, using the specified
//		 * destination.
//		 * 
//		 * @param pw
//		 *            PrintWriter the destination where to save the results.
//		 * @param q
//		 *            SearchRequest the object encapsulating the query and the
//		 *            results.
//		 */
//		public void printResults(String queryID, final PrintWriter pw,
//				final TopDocCollector collector) {
//			TopDocs topDocs = collector.topDocs();
//			int len = topDocs.totalHits;
//
//			int maximum = Math.min(topDocs.scoreDocs.length, end);
//
//			// if (minimum > set.getResultSize())
//			// minimum = set.getResultSize();
//			final String iteration = ITERATION + "0";
//			final String queryIdExpanded = queryID + " " + iteration + " ";
//			final String methodExpanded = " " + "LabLucene"
//					+ ApplicationSetup.EOL;
//			StringBuilder sbuffer = new StringBuilder();
//			// the results are ordered in descending order
//			// with respect to the score.
//			for (int i = start; i < maximum; i++) {
//				int docid = topDocs.scoreDocs[i].doc;
//				String filename = "" + docid;
//				float score = topDocs.scoreDocs[i].score;
//
//				if (filename != null && !filename.equals(filename.trim())) {
//					if (logger.isDebugEnabled())
//						logger.debug("orginal doc name not trimmed: |"
//								+ filename + "|");
//				} else if (filename == null) {
//					logger.error("inner docid does not exist: " + docid
//							+ ", score:" + score);
//					if (docid > 0) {
//						try {
//							logger.error("previous docno: "
//									+ this.searcher.doc(docid - 1).toString());
//						} catch (Exception e) {
//							e.printStackTrace();
//						}
//					}
//					continue;
//				}
//				sbuffer.append(queryIdExpanded);
//				sbuffer.append(filename);
//				sbuffer.append(" ");
//				sbuffer.append(i);
//				sbuffer.append(" ");
//				sbuffer.append(score);
//				sbuffer.append(methodExpanded);
//			}
//			pw.write(sbuffer.toString());
//		}
//	}

}
