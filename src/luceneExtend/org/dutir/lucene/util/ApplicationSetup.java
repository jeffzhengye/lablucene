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
 * The Original Code is ApplicationSetup.java.
 *
 * The Original Code is Copyright (C) 2004-2009 the University of Glasgow.
 * All Rights Reserved.
 *
 * Contributor(s):
 *   Gianni Amati <gba{a.}fub.it> (original author)
 *   Vassilis Plachouras <vassilis{a.}dcs.gla.ac.uk>
 *   Ben He <ben{a.}dcs.gla.ac.uk>
 *   Craig Macdonald <craigm{a.}dcs.gla.ac.uk>
 */
package org.dutir.lucene.util;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;

/**
 * <p>
 * This class retrieves and provides access to all the constants and parameters
 * for the system. When it is statically initialised, it loads the properties
 * file specified by the system property <tt>terrier.setup</tt>. If this is not
 * specified, then the default value is the value of the <tt>terrier.home</tt>
 * system property, appended by <tt>etc/terrier.properties</tt>. <BR/>
 * eg
 * <tt>java -D terrier.home=$TERRIER_HOME -Dterrier.setup=$TERRIER_HOME/etc/terrier.properties TrecTerrier </tt>
 * </p>
 * <p>
 * <b>System Properties used:</b>
 * <table>
 * <tr>
 * <td>
 * <tt>terrier.setup</tt></td>
 * <td>Specifies where the terrier.properties file can be found.</td>
 * </tr>
 * <tr>
 * <td><tt>terrier.home</tt></td>
 * <td>Specified where Terrier has been installed, if the terrier.properties
 * file cannot be found, or the terrier.properties file does not specify the
 * <tt>terrier.home</tt> in it. <br>
 * <b>NB:</b>In the future, this may further default to $TERRIER_HOME from the
 * environment.</td>
 * <tr>
 * <td><tt>file.separator</tt></td>
 * <td>What separates directory names in this platform. Set automatically by
 * Java</td>
 * </tr>
 * <tr>
 * <td><tt>line.separator</tt></td>
 * <td>What separates lines in a file on this platform. Set automatically by
 * Java</td>
 * </table>
 * </p>
 * <p>
 * In essence, for Terrier to function properly, you need to specify one of the
 * following on the command line:
 * <ul>
 * <li><tt>terrier.setup</tt> pointing to a terrier.properties file containing a
 * <tt>terrier.home</tt> value.</li>OR
 * <li><tt>terrier.home</tt>, and Terrier will use a properties file at
 * etc/terrier.properties, if it finds one.</li>
 * </ul>
 * </p>
 * <p>
 * Any property defined in the properties file can be overridden as follows:
 * </p>
 * <ul>
 * <li>If the system property <tt>terrier.usecontext</tt> is equal to
 * <tt>true</tt>, then a Context object is used to override the properties
 * defined in the file.</li>
 * <li>If the system property <tt>terrier.usecontext</tt> is equal to
 * <tt>false</tt>, then system properties are used to override the properties
 * defined in the file.</li>
 * </ul>
 * 
 * @author Gianni Amati, Vassilis Plachouras, Ben He, Craig Macdonald
 * @version $Revision: 1.71 $
 */
public class ApplicationSetup {

	public interface TerrierApplicationPlugin {
		public void initialise() throws Exception;
	}

	public static final String Lucene_VERSION = "2.4";
	static Logger logger = Logger.getLogger(ApplicationSetup.class);
	
	/**
	 * Default log4j config Terrier loads if no TERRIER_ETC/terrier-log.xml file
	 * exists
	 * 
	 * @since 1.1.0
	 */
	public static final String DEFAULT_LOG4J_CONFIG = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>"
			+ "<!DOCTYPE log4j:configuration SYSTEM \"log4j.dtd\">"
			+ "<log4j:configuration xmlns:log4j=\"http://jakarta.apache.org/log4j/\">"
			+ " <appender name=\"console\" class=\"org.apache.log4j.ConsoleAppender\">"
			+ "  <param name=\"Target\" value=\"System.err\"/>"
			+ "  <layout class=\"org.apache.log4j.SimpleLayout\"/>"
			+ " </appender>"
			+ " <root>"
			+ "  <priority value=\"info\" />"
			+ "  <appender-ref ref=\"console\" />"
			+ " </root>"
			+ "</log4j:configuration>";

	/**
	 * The properties object in which the properties from the file are read.
	 */
	protected static final Properties appProperties = new Properties();
	protected static final Properties UsedAppProperties = new Properties();;
	// Operating system dependent constants

	/**
	 * The file separator used by the operating system. Defaults to the system
	 * property <tt>file.separator</tt>.
	 */
	public static String FILE_SEPARATOR = System.getProperty("file.separator");

	/**
	 * The new line character used by the operating system. Defaults to the
	 * system property <tt>line.separator</tt>.
	 */
	public static String EOL = System.getProperty("line.separator");
	// Application specific constants. Should be specified in the properties
	// file.

	/**
	 * The directory under which the application is installed. It corresponds to
	 * the property <tt>terrier.home</tt> and it should be set in the properties
	 * file, or as a property on the command line.
	 */
	public static String LUCENE_HOME;

	/**
	 * The directory under which the configuration files of Terrier are stored.
	 * The corresponding property is <tt>terrier.etc</tt> and it should be set
	 * in the properties file. If a relative path is given, TERRIER_HOME will be
	 * prefixed.
	 */
	public static String LUCENE_ETC;
	/**
	 * The name of the directory where installation independent read-only data
	 * is stored. Files like stopword lists, and example and testing data are
	 * examples. The corresponding property is <tt>terrier.share</tt> and its
	 * default value is <tt>share</tt>. If a relative path is given, then
	 * TERRIER_HOME will be prefixed.
	 */
	public static String LUCENE_SHARE;
	/**
	 * The name of the directory where the data structures and the output of
	 * Terrier are stored. The corresponding property is <tt>terrier.var</tt>
	 * and its default value is <tt>var</tt>. If a relative path is given,
	 * TERRIER_HOME will be prefixed.
	 */
	public static String LUCENE_VAR;

	/**
	 * The name of the directory where the inverted file and other data
	 * structures are stored. The default value is InvFileCollection but it can
	 * be overridden with the property <tt>terrier.index.path</tt>. If a
	 * relative path is given, TERRIER_VAR will be prefixed.
	 */
	public static String LUCENE_INDEX_PATH;

	/**
	 * The name of the file that contains the list of resources to be processed
	 * during indexing. The contents of this file are collection implementation
	 * dependent. For example, for a TREC collection, this file must contain the
	 * list of files to index. The corresponding property is
	 * <tt>collection.spec</tt> and by default its value is
	 * <tt>collection.spec</tt>. If a relative path is given, TERRIER_ETC will
	 * be prefixed.
	 */
	public static String COLLECTION_SPEC;

	// TREC SPECIFIC setup
	/**
	 * The name of the directory where the results are stored. The corresponding
	 * property is <tt>trec.results</tt> and the default value is
	 * <tot>results</tt>. If a relative path is given, TERRIER_VAR will be
	 * prefixed.
	 */
	public static String TREC_RESULTS;
	/**
	 * The name of the file that contains a list of files where queries are
	 * stored. The corresponding property is <tt>trec.topics.list</tt> and the
	 * default value is <tt>trec.topics.list</tt>. If a relative path is given,
	 * TERRIER_ETC will be prefixed.
	 */
	public static String TREC_TOPICS_LIST;

	/**
	 * The name of the file that contains a list of qrels files to be used for
	 * evaluation. The corresponding property is <tt>trec.qrels</tt> and its
	 * default value is <tt>trec.qrels</tt>. If a relative path is given,
	 * TERRIER_ETC will be prefixed.
	 */
	public static String TREC_QRELS;

	/**
	 * The suffix of the files, where the results are stored. It corresponds to
	 * the property <tt>trec.results.suffix</tt> and the default value is
	 * <tt>.res</tt>.
	 */
	public static String TREC_RESULTS_SUFFIX;
	public static String TREC_GZ_RESULTS_SUFFIX = ".gz";
	public static String TREC_RESULTS_SETTING_SUFFIX;
	/**
	 * The filename of the file that contains the weighting models to be used.
	 * The corresponding property is <tt>trec.models</tt> and the default value
	 * is <tt>trec.models</tt>. If a relative path is given, then it is prefixed
	 * with TERRIER_ETC.
	 */
	public static String TREC_MODELS;
	// end of TREC specific section

	/**
	 * The suffix of the inverted file. The corresponding property is
	 * <tt>if.suffix</tt> and by default the value of this property is
	 * <tt>.if</tt>
	 */
	// public static String IFSUFFIX;
	/**
	 * The suffix of the file that contains the lexicon. The corresponding
	 * property is <tt>lexicon.suffix</tt> and by default the value of this
	 * property is <tt>.lex</tt>
	 */
	// public static String LEXICONSUFFIX;
	/**
	 * The suffix of the file that contains the document index. The
	 * corresponding property is <tt>doc.index.suffix</tt> and by default the
	 * value of this property is <tt>.docid</tt>
	 */
	public static String DOC_INDEX_SUFFIX;

	/**
	 * The suffix of the lexicon index file that contains the offset of each
	 * term in the lexicon. The corresponding property is
	 * <tt>lexicon.index.suffix</tt> and by default its value is .lexid.
	 */
	// public static String LEXICON_INDEX_SUFFIX;
	/**
	 * The suffix of the lexicon hash file. Corresponding property is
	 * <tt>lexicon.hash.suffix</tt>, default is ".lexhash".
	 */
	// public static String LEXICON_HASH_SUFFIX;

	/**
	 * The suffix of the file that contains the collection statistics. It
	 * corresponds to the property <tt>log.suffix</tt> and by default the value
	 * of this property is <tt>.log</tt>
	 */
	public static String LOG_SUFFIX;
	/**
	 * The suffix of the file that contains the index properties. It corresponds
	 * to the property <tt>indexproperties.suffix</tt> and by default the value
	 * of this property is <tt>.log</tt>
	 */
	public static String PROPERTIES_SUFFIX;
	/**
	 * The suffix of the direct index. It corresponds to the property
	 * <tt>df.suffix</tt> and by default the value of this property is
	 * <tt>.df</tt>
	 */
	public static String DF_SUFFIX;

	/**
	 * The prefix of the temporary merged files, which are created during
	 * merging the lexicon files. It corresponds to the property
	 * <tt>merge.prefix</tt> and the default value is <tt>MRG_</tt>.
	 */
	// public static String MERGE_PREFIX;
	/**
	 * A progressive number which is assigned to the temporary lexicon files
	 * built during the indexing. It is used to keep track of the order with
	 * which the temporary files were created. It corresponds to the property
	 * <tt>merge.temp.number</tt> and the default value is <tt>100000</tt>
	 */
	// public static int MERGE_TEMP_NUMBER;
	/**
	 * The number of documents to be processed as a group during indexing. For
	 * each such group of documents, a temporary lexicon is built, and after
	 * indexing, all temporary lexicons are merged in order to create a single
	 * lexicon. It corresponds to the property <tt>bundle.size</tt> and the
	 * default value is <tt>2000</tt>.
	 */
	// public static int BUNDLE_SIZE;
	/**
	 * The number of bytes used to store a term. Corresponds to MAX_TERM_LENGTH
	 * if not using UTF, and 3*MAX_TERM_LENGTH if using UTF. No property is
	 * associated. UTF support can be enabled by setting the property
	 * <tt>string.use_utf</tt> to true.
	 */
	public static int STRING_BYTE_LENGTH;

	/**
	 * The number of bytes used to store a document number. It corresponds to
	 * the property <tt>docno.byte.length</tt>, and the default value is 20.
	 * 
	 * @since 1.1.0
	 */
	public static int DOCNO_BYTE_LENGTH;

	/**
	 * The maximum size of a term. It corresponds to the the property
	 * <tt>max.term.length</tt>, and the default value is 20.
	 * 
	 * @since 1.1.0
	 */
	public static int MAX_TERM_LENGTH;

	/**
	 * Ignore or not empty documents. That is, if it is true, then a document
	 * that does not contain any terms will have a corresponding entry in the
	 * .docid file and the total number of documents in the statistics will be
	 * the total number of documents in the collection, even if some of them are
	 * empty. It corresponds to the property <tt>ignore.empty.documents</tt> and
	 * the default value is false.
	 */
	public static boolean IGNORE_EMPTY_DOCUMENTS;
	public static boolean FeedBack_Relevance;
	/**
	 * true, using inner id to evaluate, or using docno
	 */
	public static boolean Eval_ID; 
	/**
	 * The prefix of the data structures' filenames. It corresponds to the
	 * property <tt>terrier.index.prefix</tt> and the default value is
	 * <tt>data</tt>.
	 */
	
	public static String LUCENE_INDEX_PREFIX;

	public static boolean PostProcessTag = false;
	
	/** The filename of the inverted file. */
	// public static String INVERTED_FILENAME;
	/** The filename of the direct file. */
	public static String DIRECT_FILENAME;
	/** The filename of the document index. */
	public static String DOCUMENT_INDEX_FILENAME;
	/** The filename of the lexicon file. */
	// public static String LEXICON_FILENAME;
	/** The filename of the lexicon index file. */
	// public static String LEXICON_INDEX_FILENAME;
	/** The filename of the log (statistics) file. */
	public static String LOG_FILENAME;

	// query expansion properties
	/**
	 * The number of terms added to the original query. The corresponding
	 * property is <tt>expansion.terms</tt> and the default value is <tt>10</tt>
	 * .
	 */
	public static int EXPANSION_TERMS;

	/**
	 * The number of top ranked documents considered for expanding the query.
	 * The corresponding property is <tt>expansion.documents</tt> and the
	 * default value is <tt>3</tt>.
	 */
	public static int EXPANSION_DOCUMENTS;

	/**
	 * The name of the file which contains the query expansion methods used. The
	 * corresponding property is <tt>expansion.models</tt> and the default value
	 * is <tt>qemodels</tt>. If a relative path is given, it is prefixed with
	 * TERRIER_ETC.
	 */
	public static String EXPANSION_MODELS;
	// block related properties
	/**
	 * The size of a block of terms in a document. The corresponding property is
	 * block.size and the default value is 1.
	 */
	public static int BLOCK_SIZE;

	/**
	 * The maximum number of blocks in a document. The corresponding property is
	 * <tt>max.blocks</tt> and the default value is 100000.
	 */
	public static int MAX_BLOCKS;

	/**
	 * Specifies whether block information will be used for indexing. The
	 * corresponding property is <tt>block.indexing</tt> and the default value
	 * is false. The value of this property cannot be modified after the index
	 * of a collection has been built.
	 */
	public static boolean BLOCK_INDEXING = false;

	/**
	 * Specifies whether fields will be used for querying. The corresponding
	 * property is <tt>field.querying</tt> and the default value is false.
	 */
	public static boolean FIELD_QUERYING = false;

	// new
	/**
	 * Memory threshold in the single pass inversion method. If a memory check
	 * is below this value, the postings in memory are written to disk. The
	 * default value is 50M (100M for 64bit JVMs), and this can be configured
	 * using the property <tt>memory.reserved</tt>.
	 */
	public static int MEMORY_THRESHOLD_SINGLEPASS;

	/**
	 * Number of documents between each memory check in the single pass
	 * inversion method. The default value is 20, and this can be configured
	 * using the property <tt>docs.check</tt>.
	 */
	public static int DOCS_CHECK_SINGLEPASS;

	/** Checks whether a context is used instead of the properties file */
	private static boolean useContext = false;

	/** The configuration file used by log4j */
	public static String LOG4J_CONFIG = null;

//	public static String pDir = "";
	
	/**
	 * The context that replaces the properties file if the property
	 * <tt>terrier.usecontext</tt> is equal to <tt>true</tt>.
	 */
	private static Context envCtx = null;

	static {
		useContext = Boolean.parseBoolean(System.getProperty(
				"lucene.usecontext", "false"));

		String propertiesFile = null;
		String lucene_home = null;
		try {
			if (useContext) {
				//
				Context initCtx = null;
				try {
					initCtx = (Context) (new InitialContext());
					envCtx = (Context) initCtx.lookup("java:comp/env");

				} catch (NamingException ne) {
					logger
							.warn("NamingException loading an InitialContext or EnvironmentContext : "
									+ ne);
					System.exit(1);
				}
				try {
					lucene_home = (String) envCtx.lookup("lucene.home");
				} catch (NamingException ne) {
					logger
							.warn("NamingException finding terrier variables from envCtx : "
									+ ne);
				}
				try {
					propertiesFile = (String) envCtx.lookup("lucene.setup");
				} catch (NamingException ne) {
					logger
							.warn("NamingException finding terrier variables from envCtx : "
									+ ne);
				}
				if (propertiesFile == null)
					propertiesFile = lucene_home + FILE_SEPARATOR + "etc"
							+ FILE_SEPARATOR + "lucene.properties";

			} else {
				lucene_home = System.getProperty("lucene.home", "");
				propertiesFile = System.getProperty("lucene.setup",
						lucene_home + FILE_SEPARATOR + "etc" + FILE_SEPARATOR
								+ "lucene.properties");
				
				
			}

			// System.err.println("Properties file is "+propertiesFile);
			LUCENE_HOME = getProperty("lucene.home", lucene_home);
			FileInputStream in = new FileInputStream(propertiesFile);
			configure(new BufferedInputStream(in));
			in.close();
		} catch (java.io.FileNotFoundException fnfe) {
			logger
					.warn("WARNING: The file lucene.properties was not found at location "
							+ propertiesFile);
			logger
					.warn(" Assuming the value of terrier.home from the corresponding system property.");
		} catch (java.io.IOException ioe) {
			logger.warn("Input/Output Exception during initialization of ");
			logger.warn("uk.ac.gla.terrier.utility.ApplicationSetup: " + ioe);
			logger.warn("Stack trace follows.");
			ioe.printStackTrace();
		}
		/*
		 * The property terrier.home does not have a default value, so it has to
		 * be specified by the user in the terrier.properties file. If there is
		 * no terrier.properties specified, then we try to read a value from the
		 * system property terrier.home. Ideally, the value of terrier.home
		 * would be $ENV{TERRIER_HOME} but java geniuses, in their infinite
		 * wisdom have deprecated System.getEnv() in Java 1.4.
		 */
		LUCENE_HOME = getProperty("lucene.home", lucene_home);

		if (LUCENE_HOME.equals("")) {
			logger.warn("Please ensure that the property lucene.home");
			logger.warn("is specified in the file terrier.properties,");
			logger.warn("or as a system property in the command line.");
		}
	}

	public static void loadCommonProperties() {
		LUCENE_ETC = makeAbsolute(getProperty("lucene.etc", "etc"),
				LUCENE_HOME);
		LUCENE_VAR = makeAbsolute(getProperty("lucene.var", "var"),
				LUCENE_HOME);
		LUCENE_SHARE = makeAbsolute(getProperty("lucene.share", "share"),
				LUCENE_HOME);
		LUCENE_INDEX_PATH = makeAbsolute(getProperty("lucene.index.path",
				"index"), LUCENE_VAR);

		// TREC specific
		TREC_TOPICS_LIST = makeAbsolute(getProperty("trec.topics.list",
				"trec.topics.list"), LUCENE_ETC);
		TREC_QRELS = makeAbsolute(getProperty("trec.qrels", "trec.qrels"),
				LUCENE_ETC);
		TREC_RESULTS = makeAbsolute(getProperty("trec.results", "results"),
				LUCENE_VAR);
		TREC_MODELS = makeAbsolute(getProperty("trec.models", "trec.models"),
				LUCENE_ETC);
		TREC_RESULTS_SUFFIX = getProperty("trec.results.suffix", ".res");
		TREC_RESULTS_SETTING_SUFFIX = getProperty("trec.results.setting.suffix", ".setting");
		// The following properties specify the filenames and suffixes
		COLLECTION_SPEC = makeAbsolute(getProperty("collection.spec",
				"collection.spec"), LUCENE_ETC);

		// IFSUFFIX = getProperty("if.suffix", ".if");
		// LEXICONSUFFIX = getProperty("lexicon.suffix", ".lex");
		// LEXICON_INDEX_SUFFIX = getProperty("lexicon.index.suffix", ".lexid");
		// LEXICON_HASH_SUFFIX = getProperty("lexicon.hash.suffix",".lexhash");
//		DOC_INDEX_SUFFIX = getProperty("doc.index.suffix", ".docid");
		LOG_SUFFIX = getProperty("log.suffix", ".log");
		DF_SUFFIX = getProperty("df.suffix", ".df");
		PROPERTIES_SUFFIX = getProperty("indexproperties.suffix", ".properties");


		// if a document is empty, that is it does not contain any terms,
		// we have the option to add it to the index, or not. By default,
		// empty documents are added to the index.
		IGNORE_EMPTY_DOCUMENTS = Boolean.parseBoolean(getProperty(
				"ignore.empty.documents", "false"));
		FeedBack_Relevance = Boolean.parseBoolean(getProperty(
				"QueryExpansion.RelevanceFeedback", "false"));
		Eval_ID = getProperty("Lucene.TRECQuerying.outputformat", "TRECDocidOutputFormat").endsWith("TRECDocidOutputFormat");
			
			
		// During the indexing process, we process and create temporary
		// structures
		// for bundle.size files.
		// BUNDLE_SIZE = Integer.parseInt(getProperty("bundle.size", "2000"));

		// the maximum size of a term (string)
		MAX_TERM_LENGTH = Integer
				.parseInt(getProperty("max.term.length", "20"));

		// the maximum number of bytes used to store a term.
		if (Boolean.parseBoolean(ApplicationSetup.getProperty("string.use_utf",
				"false")))
			STRING_BYTE_LENGTH = MAX_TERM_LENGTH * 3;
		else
			STRING_BYTE_LENGTH = MAX_TERM_LENGTH;

		// the maximum number of bytes used to store a document number.
		DOCNO_BYTE_LENGTH = Integer.parseInt(getProperty("docno.byte.length",
				"20"));

		// query expansion properties
		EXPANSION_TERMS = Integer
				.parseInt(getProperty("expansion.terms", "10"));
		EXPANSION_DOCUMENTS = Integer.parseInt(getProperty(
				"expansion.documents", "3"));
		EXPANSION_MODELS = makeAbsolute(getProperty("expansion.models",
				"qemodels"), LUCENE_ETC);
		// html tags and proximity related properties
		BLOCK_INDEXING = Boolean.parseBoolean(getProperty("block.indexing",
				"false"));
		BLOCK_SIZE = Integer.parseInt(getProperty("blocks.size", "1"));
		MAX_BLOCKS = Integer.parseInt(getProperty("blocks.max", "100000"));
		FIELD_QUERYING = Boolean.parseBoolean(getProperty("field.querying",
				"false"));

		// double the amount of memory if using 64bit JVM.
		MEMORY_THRESHOLD_SINGLEPASS = Integer.parseInt(getProperty(
				"memory.reserved", System.getProperty("sun.arch.data.model",
						"32").equals("64") ? "100000000" : "50000000"));
		DOCS_CHECK_SINGLEPASS = Integer
				.parseInt(getProperty("docs.check", "20"));

		LOG4J_CONFIG = makeAbsolute(getProperty("log4j.config",
				"lucene-log.xml"), LUCENE_ETC);
		// setup log4j
		if (new File(LOG4J_CONFIG).exists()) {
			System.setProperty("log.file", getSequentialQueryCounter());
			DOMConfigurator.configure(LOG4J_CONFIG);
//			PropertyConfigurator.configure(LOG4J_CONFIG);
		} else {
			// slightly ugly hack: record a good default configuration as a
			// String in this class, and load that
			// emulating DOMConfigurator.configure(Reader)
			// see
			// http://svn.apache.org/viewvc/logging/log4j/branches/v1_2-branch/src/java/org/apache/log4j/xml/DOMConfigurator.java?revision=311462&view=markup
			new DOMConfigurator().doConfigure(new StringReader(
					DEFAULT_LOG4J_CONFIG), org.apache.log4j.LogManager
					.getLoggerRepository());
		}
		// setup the logger for this class
		logger = Logger.getLogger(ApplicationSetup.class);
		// setup default filename
		setupFilenames();
		// setup any plugins
		setupPlugins();
	}

	public static void resetLogFile(String path){
		System.setProperty("log.file", path);
		LogManager.resetConfiguration();
		DOMConfigurator.configure(LOG4J_CONFIG);
		
		logger = Logger.getLogger(ApplicationSetup.class);
	}
	
	public static void configure(InputStream propertiesStream)
			throws IOException {
		appProperties.load(propertiesStream);
		loadCommonProperties();
	}

	/**
	 * Returns the value for the specified property, given a default value, in
	 * case the property was not defined during the initialization of the
	 * system.
	 * 
	 * The property values are read from the properties file. If the value of
	 * the property <tt>terrier.usecontext</tt> is true, then the properties
	 * file is overridden by the context. If the value of the property
	 * <tt>terrier.usecontext</tt> is false, then the properties file is
	 * overridden
	 * 
	 * @param propertyKey
	 *            The property to be returned
	 * @param defaultValue
	 *            The default value used, in case it is not defined
	 * @return the value for the given property.
	 */
	public static String getProperty(String propertyKey, String defaultValue) {
		String propertyValue = appProperties.getProperty(propertyKey,
				defaultValue);
		if (useContext) {// context is used
			try {
				propertyValue = (String) envCtx.lookup(propertyKey);
				if (propertyValue != null)
					UsedAppProperties.setProperty(propertyKey, propertyValue);
			} catch (NamingException ne) {
				// in case of an exception, ie the property is not defined
				// in the context, use the value from the properties file,
				// or the default value.
			}

		} else {
			propertyValue = System.getProperty(propertyKey, propertyValue);

			if (propertyValue != null)
				UsedAppProperties.setProperty(propertyKey, propertyValue);

			// in case there is no system property, the returned property value
			// is the one read from the properties file, or the default value.
		}
		return propertyValue;
	}

	/**
	 * Returns a properties object detailing all the properties fetched during
	 * the lifetime of this class. It is of note that this is NOT the underlying
	 * appProperties table, as to update that would mean that properties fetched
	 * using their defaults, could not have different defaults in different
	 * places.
	 */
	public static Properties getUsedProperties() {
		return UsedAppProperties;
	}

	public static Properties getProperties() {
		return appProperties;
	}

	/**
	 * Sets a value for the specified property. The properties set with this
	 * method are not saved in the properties file.
	 * 
	 * @param propertyKey
	 *            the name of the property to set.
	 * @param value
	 *            the value of the property to set.
	 */
	public static void setProperty(String propertyKey, String value) {
		appProperties.setProperty(propertyKey, value);
	}

	/**
	 * set a property value only if it has not already been set
	 * 
	 * @param propertyKey
	 *            the name of the property to set.
	 * @param defaultValue
	 *            the value of the property to set.
	 */
	public static void setDefaultProperty(String propertyKey,
			String defaultValue) {
		if (getProperty(propertyKey, null) != null)
			setProperty(propertyKey, defaultValue);
	}

	/**
	 * Sets up the names of the inverted file, the direct file, the document
	 * index file and the lexicon file.
	 */
	public static void setupFilenames() {
		String filenameTemplate = LUCENE_INDEX_PATH + FILE_SEPARATOR
				+ LUCENE_INDEX_PREFIX;
		// INVERTED_FILENAME =filenameTemplate + IFSUFFIX;
		DIRECT_FILENAME = filenameTemplate + DF_SUFFIX;
		DOCUMENT_INDEX_FILENAME = filenameTemplate + DOC_INDEX_SUFFIX;
		// LEXICON_FILENAME = filenameTemplate + LEXICONSUFFIX;
		// LEXICON_INDEX_FILENAME = filenameTemplate + LEXICON_INDEX_SUFFIX;
		LOG_FILENAME = filenameTemplate + LOG_SUFFIX;
	}

	/** list of loaded plugins */
	protected static List<TerrierApplicationPlugin> loadedPlugins = null;

	/** Calls the initialise method of any plugins named in terrier.plugins */
	protected static void setupPlugins() {
		loadedPlugins = new LinkedList<TerrierApplicationPlugin>();
		final String[] pluginNames = getProperty("terrier.plugins", "").split(
				"\\s*,\\s*");
		for (String pluginName : pluginNames) {
			if (pluginName.length() == 0)
				continue;
			try {
				TerrierApplicationPlugin plugin = (TerrierApplicationPlugin) Class
						.forName(pluginName).newInstance();
				plugin.initialise();
				loadedPlugins.add(plugin);
			} catch (Exception e) {
				logger.warn("Problem loading plugin named " + pluginName, e);
			}
		}
	}

	/**
	 * Return a loaded plugin by name. Returns null if a plugin of that name has
	 * not been loaded
	 */
	public TerrierApplicationPlugin getPlugin(String name) {
		for (TerrierApplicationPlugin p : loadedPlugins)
			if (p.getClass().getName().equals(name))
				return p;
		return null;
	}

	/**
	 * Checks whether the given filename is absolute and if not, it adds on the
	 * default path to make it absolute. If a URI scheme is present, the
	 * filename is assumed to be absolute
	 * 
	 * @param filename
	 *            String the filename to make absolute
	 * @param DefaultPath
	 *            String the prefix to add
	 * @return the absolute filename
	 */
	public static String makeAbsolute(String filename, String DefaultPath) {
		if (filename == null)
			return null;
		if (filename.length() == 0)
			return filename;
		if (filename.matches("^\\w+:.*"))
			return filename;
		if (new File(filename).isAbsolute())
			return filename;
		if (!DefaultPath.endsWith(FILE_SEPARATOR)) {
			DefaultPath = DefaultPath + FILE_SEPARATOR;
		}
		return DefaultPath + filename;
	}

	
	static protected String getSequentialQueryCounter() {
		/* TODO: NFS safe locking */
		File fx = new File(ApplicationSetup.TREC_RESULTS, "querycounter");
		int counter = 0;
		if (fx.exists()) {
			try {
				BufferedReader buf = new BufferedReader(new FileReader(fx));
				String s = buf.readLine();
				counter = (new Integer(s)).intValue();
				counter++;
				buf.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return ApplicationSetup.TREC_RESULTS + "/"  + counter + LOG_SUFFIX;
//		return "" + counter;
	}

	
	public static void main(String args[]){
//		DOMConfigurator.configure(LOG4J_CONFIG);
		resetLogFile("./var/logx");
		Properties prop = new Properties();
//		BasicConfigurator.configure();
		try {
//			prop.load(StreamGenerator.getFIS(LOG4J_CONFIG));
//			prop.list(System.out);
//			System.err.println(prop.stringPropertyNames());
//			System.setProperty("log.file", LOG4J_CONFIG);
//			logger.info("load adding another line");
			
			
//			prop.load(StreamGenerator.getFIS(LOG4J_CONFIG));
//			prop.setProperty("org.apache.log4j.FileAppender.File", "./proConf");
//			DOMConfigurator.configure(LOG4J_CONFIG);
//			PropertyConfigurator.configure(prop);
			if(logger.isInfoEnabled()) logger.info("logging after change File property");
		}  catch (Exception e) {
			e.printStackTrace();
		}
		
	}
	
}
