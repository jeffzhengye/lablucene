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

import java.io.File;
import java.util.ArrayList;

import org.apache.log4j.Logger;
import org.apache.lucene.search.model.Statistics;
import org.dutir.lucene.evaluation.AdhocEvaluation;
import org.dutir.lucene.evaluation.Evaluation;
import org.dutir.lucene.evaluation.NamedPageEvaluation;
import org.dutir.lucene.util.ApplicationSetup;
import org.dutir.lucene.util.Files;
import org.dutir.util.Strings;

/**
 * The text-based application that handles querying with Terrier, for TREC-like
 * test collections. <code>
TrecTerrier, indexing TREC collections with Terrier.<br>
usage: java TrecTerrier [flags in any order]<br>
<br>
  -h --help		print this message<br>
  -V --version	 print version information<br>
  -i --index	   index a collection<br>
  -r --retrieve	retrieve from an indexed collection<br>
  -e --evaluate	evaluates the results in the directory<br>
				   var/results with the specified qrels file<br>
				   in the file etc/trec.qrels<br>
<br>
If invoked with '-i', then both the direct and<br>
inverted files are build, unless it is specified which<br>
of the structures to build.<br>
  -d --direct	  creates the direct file<br>
  -v --inverted	creates the inverted file, from an already existing direct<br>
  -l --langmodel   creates additional structures for language modelling<br>
<br>
If invoked with '-r', there are the following options.<br>
  -c value		 parameter value for term frequency normalisation.<br>
				   If it is not specified, then the default value for each<br>
					weighting model is used, eg PL2 =&gt; c=1, BM25 b=&gt; 0.75<br>
  -q --queryexpand applies query expansion<br>
  -l --langmodel   applies language modelling<br>
<br>
If invoked with '-e', there is the following option.<br>
  -p --perquery	reports the average precision for each query separately.<br>
  filename.res	 restrict evaluation to filename.res only.<br>
<br>
If invoked with one of the following options, then the contents of the<br>
corresponding data structure are shown in the standard output.<br>
  --printdocid	 prints the contents of the document index<br>
  --printlexicon   prints the contents of the lexicon<br>
  --printinverted  prints the contents of the inverted file<br>
  --printdirect	prints the contents of the direct file<br>
  --printstats	 prints statistics about the indexed collection<br>
</code
 * 
 * @author Vassilis Plachouras
 * @version $Revision: 1.57 $
 */


public class TrecLucene {
	/** The logger used */
	Logger logger = null;
	/** The unkown option */
	protected String unknownOption;
	/** The file to evaluation, if any */
	protected ArrayList<String> evaluationFilename = null;

	/** Specifies whether to apply query expansion */
	protected boolean queryexpand;

	/** Specifies whether language modelling is applied */
	protected boolean languagemodel;

	/** Specifies whether a help message is printed */
	protected boolean printHelp;
	/** Specified whether a version message is printed */
	protected boolean printVersion;

	/** Specifies whether to index a collection */
	protected boolean indexing;

	/**
	 * Specifies whether to build the inverted file from scrach, sigle pass
	 * method
	 */
	protected boolean singlePass = false;

	/** use Hadoop indexing */
	protected boolean hadoop = false;

	/** Specifies whether to retrieve from an indexed collection */
	protected boolean retrieving;

	/** Specifies whether to print the document index */
	protected boolean printdocid;

	/** Specifies whether to print the lexicon */
	protected boolean printlexicon;

	/** Specifies whether to print the inverted file */
	protected boolean printinverted;

	/** Specifies whether to print the direct file */
	protected boolean printdirect;

	/** Specifies whether to print the statistics file */
	protected boolean printstats;

	/**
	 * Specifies whether to perform trec_eval like evaluation, reporting only
	 * average precision for each query.
	 */
	protected boolean evaluation_per_query;
	/**
	 * Specifies if the evaluation is done for adhoc or named-page finding
	 * retrieval task. adhoc by default.
	 */
	protected String evaluation_type = "adhoc";

	/**
	 * Specifies whether to build the inverted file from an already created
	 * direct file.
	 */
	protected boolean inverted;

	/**
	 * Specifies whether to build the direct file only.
	 */
	protected boolean direct;

	/**
	 * The value of the term frequency normalisation parameter.
	 */
	protected float c;

	/**
	 * Specifies whether to perform trec_eval like evaluation.
	 */
	protected boolean evaluation;

	/**
	 * Indicates whether there is a specified value for the term frequency
	 * normalisation parameter.
	 */
	protected boolean isParameterValueSpecified;

	/**
	 * Prints the version information about Terrier
	 */
	protected void version() {
		System.out
				.println("TrecLucene, indexing TREC collections with SableLucene. Version "
						+ ApplicationSetup.Lucene_VERSION);
		// System.out.println("Built on ");
	}

	/**
	 * Prints a help message that explains the possible options.
	 */
	protected void usage() {
		System.out
				.println("TrecTerrier, indexing TREC collections with Terrier. Version "
						+ ApplicationSetup.Lucene_VERSION);
		System.out.println("usage: java TrecTerrier [flags in any order]");
		System.out.println("");
		System.out.println("  -h --help		print this message");
		System.out.println("  -V --version	 print version information");
		System.out.println("  -i --index	   index a collection");
		System.out
				.println("  -r --retrieve	retrieve from an indexed collection");
		System.out
				.println("  -e --evaluate	evaluates the results in the directory");
		System.out.println("				   var/results with the specified qrels file");
		System.out.println("				   in the file etc/trec.qrels");
		System.out.println("");
		System.out.println("If invoked with \'-i\', then both the direct and");
		System.out
				.println("inverted files are build, unless it is specified which");
		System.out.println("of the structures to build.");
		System.out.println("  -d --direct	  creates the direct file");
		System.out
				.println("  -v --inverted	creates the inverted file, from an already existing direct");
		System.out
				.println("  -j --ifile	   creates the inverted file, from scratch, single pass");
		System.out
				.println("  -H --hadoop	   creates the inverted file, from scratch, using Hadoop MapReduce indexing");
		System.out
				.println("  -l --langmodel   creates additional structures for language modelling");
		System.out.println("");
		System.out
				.println("If invoked with \'-r\', there are the following options.");
		System.out
				.println("  -c value		 parameter value for term frequency normalisation.");
		System.out
				.println("				   If it is not specified, then the default value for each");
		System.out
				.println("				   weighting model is used, eg PL2 => c=1, BM25 b=> 0.75");
		System.out.println("  -q --queryexpand applies query expansion");
		System.out.println("  -l --langmodel   applies language modelling");
		System.out.println("");
		System.out
				.println("If invoked with \'-e\', there is the following options.");
		System.out
				.println("  -p --perquery	reports the average precision for each query separately.");
		System.out
				.println("  -n --named		evaluates for the named-page finding task.");
		System.out
				.println("  filename.res	 restrict evaluation to filename.res only.");
		System.out.println("");
		System.out
				.println("If invoked with one of the following options, then the contents of the ");
		System.out
				.println("corresponding data structure are shown in the standard output.");
		System.out
				.println("  --printdocid	 prints the contents of the document index");
		System.out
				.println("  --printlexicon   prints the contents of the lexicon");
		System.out
				.println("  --printinverted  prints the contents of the inverted file");
		System.out
				.println("  --printdirect	prints the contents of the direct file");
		System.out
				.println("  --printstats	 prints statistics about the indexed collection");
	}



	String paras = "";
	/**
	 * Processes the command line arguments and sets the corresponding
	 * properties accordingly.
	 * 
	 * @param args
	 *            the command line arguments.
	 * @return int zero if the command line arguments are processed
	 *         successfully, otherwise it returns an error code.
	 */
	protected int processOptions(String[] args) {
		if (args.length == 0)
			return ERROR_NO_ARGUMENTS;
		paras = Strings.concatenate(args);
		boolean reloadTag = false;
		int pos = 0;
		while (pos < args.length) {
			if(args[pos].matches("-D.+=.+$")){ //context variable
				int p = args[pos].indexOf("=");
				String key = args[pos].substring(2, p);
				String value = args[pos].substring(p+1);
				System.setProperty(key, value);
				ApplicationSetup.setProperty(key, value);
			}else if (args[pos].equals("-h") || args[pos].equals("--help"))
				printHelp = true;
			else if (args[pos].equals("-i") || args[pos].equals("--index"))
				indexing = true;
			else if (args[pos].equals("-j") || args[pos].equals("--ifile"))
				singlePass = true;
			else if (args[pos].equals("-H") || args[pos].equals("--hadoop"))
				hadoop = true;
			else if (args[pos].equals("-r") || args[pos].equals("--retrieve")) {
				retrieving = true;
			}

			else if (args[pos].equals("-v") || args[pos].equals("--inverted"))
				inverted = true;
			else if (args[pos].equals("-d") || args[pos].equals("--direct"))
				direct = true;
			else if (args[pos].equals("-q")
					|| args[pos].equals("--queryexpand"))
				queryexpand = true;
			else if (args[pos].equals("-l") || args[pos].equals("--langmodel"))
				languagemodel = true;
			else if (args[pos].equals("--printdocid"))
				printdocid = true;
			else if (args[pos].equals("-p") || args[pos].equals("--perquery"))
				evaluation_per_query = true;
			else if (args[pos].equals("--printlexicon"))
				printlexicon = true;
			else if (args[pos].equals("--printinverted"))
				printinverted = true;
			else if (args[pos].equals("--printdirect"))
				printdirect = true;
			else if (args[pos].equals("--printstats"))
				printstats = true;
			else if (args[pos].equals("-e") || args[pos].equals("--evaluate")) {
				evaluation = true;
			} else if (args[pos].equals("-n") || args[pos].equals("--named")) {
				evaluation_type = "named";
			} else if (args[pos].startsWith("-c")) {
				isParameterValueSpecified = true;
				if (args[pos].length() == 2) { // the next argument is the value
					if (pos + 1 < args.length) { // there is another argument
						pos++;
						c = Float.parseFloat(args[pos]);
					} else
						return ERROR_NO_C_VALUE;
				} else { // the value is in the same argument
					c = Float.parseFloat(args[pos].substring(2));
				}
			} else if (args[pos].startsWith("--")) {
				String pname = args[pos].substring(2);
				String pvalue = args[++pos];
				ApplicationSetup.setProperty(pname, pvalue);
				reloadTag = true;
			} 
			else if (evaluation) {
				if (evaluationFilename == null) {
					evaluationFilename = new ArrayList<String>();
				}
				evaluationFilename.add(args[pos]);
			} else {
				unknownOption = args[pos];
				System.out.println("error opt:" + unknownOption);
				return ERROR_UNKNOWN_OPTION;
			}
			pos++;
		}
		// /////////yezheng//////////////
//		if (reloadTag) {
			ApplicationSetup.loadCommonProperties();
//		}

		if(logger == null) logger = Logger.getLogger(TrecLucene.class);
		if(logger.isInfoEnabled())logger.info("Run Parameters: " + Strings.concatenate(args));
		if (isParameterValueSpecified && !retrieving)
			return ERROR_GIVEN_C_NOT_RETRIEVING;

		if ((retrieving || queryexpand || c != 0)
				&& (direct || inverted || indexing))
			return ERROR_CONFLICTING_ARGUMENTS;

		if (printdocid
				&& !Files.exists(ApplicationSetup.DOCUMENT_INDEX_FILENAME))
			return ERROR_PRINT_DOCINDEX_FILE_NOT_EXISTS;

		if (printdirect && !Files.exists(ApplicationSetup.DIRECT_FILENAME))
			return ERROR_PRINT_DIRECT_FILE_NOT_EXISTS;

		if (printstats && !Files.exists(ApplicationSetup.LOG_FILENAME))
			return ERROR_PRINT_STATS_FILE_NOT_EXISTS;

		if (hadoop && !indexing)
			return ERROR_HADOOP_NOT_RETRIEVAL;

		if (hadoop && (languagemodel || direct || inverted))
			return ERROR_HADOOP_ONLY_INDEX;

		if (direct && !indexing)
			return ERROR_DIRECT_NOT_INDEXING;

		if (inverted && !indexing)
			return ERROR_INVERTED_NOT_INDEXING;

		if (queryexpand && !retrieving)
			return ERROR_EXPAND_NOT_RETRIEVE;

		return ARGUMENTS_OK;
	}

	/**
	 * Calls the required classes from Terrier.
	 */
	public void run() {
		if (printVersion) {
			version();
			return;
		}
		if (printHelp) {
			usage();
			return;
		}

		long startTime = System.currentTimeMillis();
		if (languagemodel && indexing) {
			// TRECLMIndexing LMIndexing = new TRECLMIndexing();
			// LMIndexing.createLMIndex();
		} else if (languagemodel && retrieving) {
			// TRECLMQuerying trecLMQuerying = new TRECLMQuerying();
			// trecLMQuerying.processQueries();
		} else if (indexing) {
			if (hadoop) {
				try {
					// HadoopIndexing.main(new String[]{});
				} catch (Exception e) {
					logger.warn(e);
					e.printStackTrace();
					return;
				}
			} else {
				GeneralIndexer.main(null);
			}
		} else if (retrieving) {
			// if no value is given, then we use a default value
			if (queryexpand) {
				ApplicationSetup.PostProcessTag = true;
			} 
			TRECQuerying trecQuerying = new TRECQuerying();
			trecQuerying.retrievalPara = this.paras;
			trecQuerying.processQueries(c, isParameterValueSpecified);
		} else if (printdocid) {
			// Index i = Index.createIndex();
			// DocumentIndexInputStream docIndex =
			// (DocumentIndexInputStream)(i.getIndexStructureInputStream("document"));
			// docIndex.print();
			// docIndex.close();
			// i.close();
		} else if (printlexicon) {
			// Index i = Index.createIndex();
			// LexiconUtil.printLexicon(i, "lexicon");
		} else if (printdirect) {
			// Index i = Index.createIndex();
			// if (! i.hasIndexStructureInputStream("direct"))
			// {
			// logger.warn("Sorry, no direct index structure in index");
			// }
			// else
			// {
			// DirectIndexInputStream dirIndex =
			// (DirectIndexInputStream)(i.getIndexStructureInputStream("direct"));
			// dirIndex.print();
			// dirIndex.close();
			// i.close();
			// }
		} else if (printinverted) {
			// Index i = Index.createIndex();
			// if (i.hasIndexStructureInputStream("inverted"))
			// {
			// InvertedIndexInputStream invIndex =
			// (InvertedIndexInputStream)(i.getIndexStructureInputStream("inverted"));
			// invIndex.print();
			// invIndex.close();
			// }
			// else
			// {
			// logger.warn("Sorry, no inverted index inputstream structure in index");
			// }
			// i.close();
		} else if (printstats) {
			try {
				Statistics.main(null);
			} catch (Exception e) {
				e.printStackTrace();
			}
			// Index i = Index.createIndex();
			// if (i == null)
			// {
			// logger.error("No such index : "+ Index.getLastIndexLoadError());
			//				
			// }
			// else if(logger.isInfoEnabled()){
			// logger.info("Collection statistics:");
			// logger.info("number of indexed documents: " +
			// i.getCollectionStatistics().getNumberOfDocuments());
			// logger.info("size of vocabulary: " +
			// i.getCollectionStatistics().getNumberOfUniqueTerms());
			// logger.info("number of tokens: " +
			// i.getCollectionStatistics().getNumberOfTokens());
			// logger.info("number of pointers: " +
			// i.getCollectionStatistics().getNumberOfPointers());
			// }
			// i.close();
		} else if (evaluation) {
			Evaluation te = null;
			if (evaluation_type.equals("adhoc"))
				te = new AdhocEvaluation();
			else if (evaluation_type.equals("named"))
				te = new NamedPageEvaluation();
			String[] nomefile = null;
			if (evaluationFilename == null) {
				/* list all the result files and then evaluate them */
				File fresdirectory = new File(ApplicationSetup.TREC_RESULTS);
				nomefile = fresdirectory.list();
			} else {
				nomefile = evaluationFilename.toArray(new String[0]);
			}
			for (int i = 0; i < nomefile.length; i++) {
				if (evaluationFilename != null || nomefile[i].endsWith(".res") || nomefile[i].endsWith(".gz")) {
					String resultFilename = ApplicationSetup.TREC_RESULTS + "/"
							+ nomefile[i];
					if (nomefile[i].indexOf("/") >= 0)
						resultFilename = nomefile[i];
					// String evaluationResultFilename =
					// resultFilename.substring(
					// 0,
					// resultFilename.lastIndexOf('.'))
					// + ".eval";
					String evaluationResultFilename = resultFilename + ".eval";
					File file = new File(evaluationResultFilename);
					if (file.exists()) {
						continue;
					}
					if(logger.isDebugEnabled()) logger.debug("evaluating: " + resultFilename);
					try {
						te.evaluate(resultFilename);
						if (evaluation_per_query)
							te.writeEvaluationResultOfEachQuery(evaluationResultFilename);
						else
							te
									.writeDetailEvaluationResult(evaluationResultFilename);
					} catch (Exception e) {
						logger.warn("file broken: " + resultFilename, e);
					}
				}
			}
		}

		long endTime = System.currentTimeMillis();
		if(logger.isInfoEnabled()) logger.info("Time elapsed: " + (endTime - startTime) / 1000.0d
				+ " seconds.");
	}

	public void applyOptions(int status) {
		switch (status) {
		case ERROR_NO_ARGUMENTS:
			usage();
			break;
		case ERROR_NO_C_VALUE:
			logger
					.warn("A value for the term frequency normalisation parameter");
			logger
					.warn("is required. Please specify it with the option '-c value'");
			break;
		case ERROR_CONFLICTING_ARGUMENTS:
			logger
					.warn("There is a conclict between the specified options. For example,");
			logger
					.warn("option '-c' is used only in conjuction with option '-r'.");
			logger
					.warn("In addition, options '-v' or '-d' are used only in conjuction");
			logger.warn("with option '-i'");
			break;
		case ERROR_PRINT_DOCINDEX_FILE_NOT_EXISTS:
			logger.warn("The specified document index file does not exist.");
			break;
		case ERROR_PRINT_DIRECT_FILE_NOT_EXISTS:
			logger.warn("The specified direct index does not exist.");
			break;
		case ERROR_UNKNOWN_OPTION:
			logger.warn("The option '" + unknownOption + "' is not recognised");
			break;
		case ERROR_DIRECT_NOT_INDEXING:
			logger
					.warn("The option '-d' or '--direct' can be used only while indexing with option '-i'.");
			break;
		case ERROR_INVERTED_NOT_INDEXING:
			logger
					.warn("The option '-i' or '--inverted' can be used only while indexing with option '-i'.");
			break;
		case ERROR_EXPAND_NOT_RETRIEVE:
			logger
					.warn("The option '-q' or '--queryexpand' can be used only while retrieving with option '-r'.");
			break;
		case ERROR_LANGUAGEMODEL_NOT_RETRIEVE:
			logger
					.warn("The option '-l' or '--langmodel' can be used only while retrieving with option '-r'.");
			break;
		case ERROR_GIVEN_C_NOT_RETRIEVING:
			logger
					.warn("A value for the parameter c can be specified only while retrieving with option '-r'.");
			break;
		case ERROR_HADOOP_NOT_RETRIEVAL:
			logger.warn("Hadoop mode '-H' can only be used for indexing");
			break;
		case ERROR_HADOOP_ONLY_INDEX:
			logger
					.warn("Hadoop mode '-H' can only be used for straightforward indexing");
			break;
		case ARGUMENTS_OK:
		default:
			run();
		}
	}

	protected static final int ARGUMENTS_OK = 0;
	protected static final int ERROR_NO_ARGUMENTS = 1;
	protected static final int ERROR_NO_C_VALUE = 2;
	protected static final int ERROR_CONFLICTING_ARGUMENTS = 3;
	protected static final int ERROR_DIRECT_FILE_EXISTS = 4;
	protected static final int ERROR_DIRECT_FILE_NOT_EXISTS = 6;
	protected static final int ERROR_PRINT_DOCINDEX_FILE_NOT_EXISTS = 7;
	protected static final int ERROR_PRINT_LEXICON_FILE_NOT_EXISTS = 8;
	protected static final int ERROR_PRINT_INVERTED_FILE_NOT_EXISTS = 9;
	protected static final int ERROR_PRINT_STATS_FILE_NOT_EXISTS = 10;
	protected static final int ERROR_PRINT_DIRECT_FILE_NOT_EXISTS = 11;
	protected static final int ERROR_UNKNOWN_OPTION = 12;
	protected static final int ERROR_DIRECT_NOT_INDEXING = 13;
	protected static final int ERROR_INVERTED_NOT_INDEXING = 14;
	protected static final int ERROR_EXPAND_NOT_RETRIEVE = 15;
	protected static final int ERROR_GIVEN_C_NOT_RETRIEVING = 16;
	protected static final int ERROR_LANGUAGEMODEL_NOT_RETRIEVE = 17;
	protected static final int ERROR_HADOOP_NOT_RETRIEVAL = 18;
	protected static final int ERROR_HADOOP_ONLY_INDEX = 19;
	
	/**
	 * The main method that starts the application
	 * 
	 * @param args
	 *            the command line arguments
	 */
	public static void main(String[] args) {
		try {
			TrecLucene trecTerrier = new TrecLucene();
			int status = trecTerrier.processOptions(args);
			trecTerrier.applyOptions(status);
		} catch (java.lang.OutOfMemoryError oome) {
			oome.printStackTrace();
			System.exit(1);
		}
	}
}
