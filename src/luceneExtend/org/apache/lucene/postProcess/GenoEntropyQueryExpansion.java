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
// * The Original Code is QueryExpansion.java.
// *
// * The Original Code is Copyright (C) 2004-2008 the University of Glasgow.
// * All Rights Reserved.
// *
// * Contributor(s):
// *   Gianni Amatti <gba{a.}fub.it> (original author)
// *   Ben He <ben{a.}dcs.gla.ac.uk>
// *   Vassilis Plachouras <vassilis{a.}dcs.gla.ac.uk>
// *   Craig Macdonald <craigm{a.}dcs.gla.ac.uk>
// */
//package uk.ac.gla.terrier.querying;
//import gnu.trove.TObjectDoubleHashMap;
//
//import java.io.BufferedReader;
//import java.io.IOException;
//
//import org.apache.log4j.Logger;
//
//import uk.ac.gla.terrier.matching.MatchingQueryTerms;
//import uk.ac.gla.terrier.matching.ResultSet;
//import uk.ac.gla.terrier.matching.models.WeightingModel;
//import uk.ac.gla.terrier.querying.entropy.GenoDocumentSetEntropy;
//import uk.ac.gla.terrier.querying.parser.SingleTermQuery;
//import uk.ac.gla.terrier.structures.CollectionStatistics;
//import uk.ac.gla.terrier.structures.DirectIndex;
//import uk.ac.gla.terrier.structures.DocumentIndex;
//import uk.ac.gla.terrier.structures.ExpansionTerms;
//import uk.ac.gla.terrier.structures.Index;
//import uk.ac.gla.terrier.structures.InvertedIndex;
//import uk.ac.gla.terrier.structures.Lexicon;
//import uk.ac.gla.terrier.utility.ApplicationSetup;
//import uk.ac.gla.terrier.utility.Files;
//import uk.ac.gla.terrier.utility.Rounding;
///**
// * Implements automatic query expansion as PostFilter that is applied to the resultset
// * after 1st-time matching.
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
// * known as the pseudo relevance set.</li>
// * </ul>
// * @version $Revision: 1.1 $
// * @author Gianni Amatti, Ben He, Vassilis Plachouras, Craig Macdonald
// */
//public class GenoEntropyQueryExpansion extends QueryExpansion{
//	protected static Logger logger = Logger.getRootLogger();
//	
//	protected int expandedCounter = 0;
//	protected int ignoredCounter = 0;
//	
//	protected TObjectDoubleHashMap<String> queryidEntropyMap;
//	
//	/**
//	* The default constructor of EntropyQueryExpansion.
//	*/
//	public GenoEntropyQueryExpansion() {
//		super();
//		this.loadEntropyFile();
//	}
//	
//	private void loadEntropyFile(){
//		String entropyFilename = ApplicationSetup.getProperty("entropy.filename", "");
//		this.queryidEntropyMap = new TObjectDoubleHashMap<String>();
//		logger.info("Loading entropy information from "+entropyFilename);
//		try{
//			BufferedReader br = Files.openFileReader(entropyFilename);
//			String str = null;
//			while ((str=br.readLine())!=null){
//				str = str.trim();
//				if (str.length()==0)
//					continue;
//				String[] tokens = str.split(" ");
//				this.queryidEntropyMap.put(tokens[0], Double.parseDouble(tokens[1]));
//				logger.info("Entropy for query "+tokens[0]+": "+tokens[1]);
//			}
//			br.close();
//		}catch(IOException ioe){
//			ioe.printStackTrace();
//			System.exit(1);
//		}
//	}
//	
//	
//	/**
//	 * Runs the actual query expansion
//	 * @see uk.ac.gla.terrier.querying.PostProcess#process(uk.ac.gla.terrier.querying.Manager,uk.ac.gla.terrier.querying.SearchRequest)
//	 */
//	public void process(Manager manager, SearchRequest q) {
//		Request rq = (Request)q;
//		// get entropy
//		double entropy = this.queryidEntropyMap.get(rq.getQueryID());
//		double threshold = Double.parseDouble(ApplicationSetup.getProperty("entropy.expansion.threshold", "0.29d"));
//		// make decision on qe
//		if (entropy<=threshold && entropy > 0){
//			ignoredCounter++;
//			logger.info("No query expansion applied.");
//			this.printCounter();
//			return;
//		}
//		expandedCounter++; this.printCounter();
//		String c_set = rq.getControl("c_set");
//		//String cString = (rq.getControl("c_set").equals("true"))?(rq.getControl("c")):(null);
//		
//		String cpostString = ApplicationSetup.getProperty("c.post", null);
//		if (cpostString!=null){
//			rq.setControl("c_set", "true");
//			rq.setControl("c", cpostString);
//		}
//		
//	   	Index index = getIndex(manager);
//		documentIndex = index.getDocumentIndex();
//		invertedIndex = index.getInvertedIndex();
//		lexicon = index.getLexicon();
//		collStats = index.getCollectionStatistics(); 
//		directIndex = index.getDirectIndex();
//		if (directIndex == null)
//		{
//			logger.error("This index does not have a direct index. Query expansion disabled!!");
//			return;
//		}
//		logger.info("Starting query expansion post-processing.");
//		//get the query expansion model to use
//		String qeModel = q.getControl("qemodel");
//		if (qeModel == null || qeModel.length() ==0)
//		{
//			logger.warn("WARNING: qemodel control not set for QueryExpansion"+
//					" post process. Using default model Bo1");
//			qeModel = "Bo1";
//		}
//		setQueryExpansionModel(WeightingModel.getWeightingModel(qeModel));
//		if(logger.isInfoEnabled()){
//			logger.info("query expansion model: " + QEModel.getInfo());
//		}
//		MatchingQueryTerms queryTerms = ((Request)q).getMatchingQueryTerms();
//		ResultSet resultSet = q.getResultSet();
//		// get the expanded query terms
//		expandQuery(queryTerms, resultSet);
//		if(logger.isInfoEnabled()){
//			logger.info("query length after expansion: " + queryTerms.length());
//			logger.info("Expanded query: ");
//		}
//		final String[] newQueryTerms = queryTerms.getTerms();
//		StringBuilder newQuery = new StringBuilder();
//		for (int i = 0; i < newQueryTerms.length; i++){
//			try{
//				if(logger.isInfoEnabled()){
//					logger.info((i + 1) + ": " + newQueryTerms[i] +
//						", normalisedFrequency: " + Rounding.toString(queryTerms.getTermWeight(newQueryTerms[i]), 4));
//				}
//				newQuery.append(newQueryTerms[i]);
//				newQuery.append('^');
//				newQuery.append(Rounding.toString(queryTerms.getTermWeight(newQueryTerms[i]), 5));
//				newQuery.append(' ');
//			}
//			catch(NullPointerException npe){
//				logger.fatal("Nullpointer exception occured in Query Expansion dumping of new Query", npe);
//			}
//		}
//		if(logger.isInfoEnabled()){
//			logger.info("NEWQUERY "+q.getQueryID() +" "+newQuery.toString());
////			 run retrieval process again for the expanded query
//			logger.info("Accessing inverted file for expanded query " + q.getQueryID());
//		}
//		
//		
//		manager.runMatching(q);
//		if (cpostString!=null){
//			rq.setControl("c_set", c_set);
//			rq.setControl("c", rq.getControl("c"));
//		}
//	}
//	
//	private void printCounter(){
//		logger.debug("Expanded "+expandedCounter+" queries, and ignored "+ignoredCounter+" queries.");
//	}
//}
