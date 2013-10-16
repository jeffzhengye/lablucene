///*
// * Terrier - Terabyte Retriever
// * Webpage: http://ir.dcs.gla.ac.uk/terrier
// * Contact: terrier{a.}dcs.gla.ac.uk
// * University of Glasgow - Department of Computing Science
// * Information Retrieval Group
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
// * The Original Code is PostFilter.java.
// *
// * The Original Code is Copyright (C) 2004, 2005 the University of Glasgow.
// * All Rights Reserved.
// *
// * Contributor(s):
//  *   Craig Macdonald <craigm{a.}dcs.gla.ac.uk> (original author)
// */
//package uk.ac.gla.terrier.querying;
//import java.io.BufferedReader;
//import java.io.IOException;
//
//import gnu.trove.THashMap;
//import gnu.trove.THashSet;
//import gnu.trove.TIntHashSet;
//import uk.ac.gla.terrier.matching.ResultSet;
//import uk.ac.gla.terrier.structures.DocumentIndex;
//import uk.ac.gla.terrier.utility.ApplicationSetup;
//import uk.ac.gla.terrier.utility.Files;
///** PostFilters are designed to complement PostProcesses. While PostProcesses
//  * operate on the entire resultset at once, with PostFilters, each PostFilter
//  * is called for each result in the resultset. According to the return of <tt>filter()</tt>
//  * the result can then be included, discarded, or (un)boosted in the resultset. Possible
//  * return values for <tt>filter</tt> are FILTER_OK, FILTER_REMOVE, FILTER_ADJUSTED
//  * Which PostFilters are run, and when is controlled by two properties, as mentioned below.<br/>
//  * <B>Properties</B>
//  * <ul>
//  * <li><tt>querying.postfilters.controls</tt> : A comma separated list of control to PostFilter
//  * class mappings. Mappings are separated by ":". eg <tt>querying.postfilters.controls=scope:Scope</tt></li>
//  * <li><tt>querying.postfilters.order</tt> : The order postfilters should be run in</li></ul>
//  * '''NB:''' Initialisation and running of post filters is carried out by the Manager.
//  * @author Craig Macdonald
//  * @version $Revision: 1.1 $
//  */
//public class PerQueryDocumentFilter implements PostFilter
//{
//	protected THashMap<String, THashSet<String>> queryidDocnosMap;
//	
//	protected THashMap<String, TIntHashSet> queryidDocidsMap;
//	
//	public PerQueryDocumentFilter(){
//		queryidDocidsMap = null;
//		this.loadDocumentFilter();
//	}
//	/**	
//	private void loadFeedback(String filename) {
//                logger.debug("Loading feedback information from "+filename+"...");
//                try {
//                        feedbackMap = new THashMap<String, Feedback>();
//
//                        BufferedReader br = Files.openFileReader(filename);
//                        String line = null;
//                        // for each line in the feedback (qrels) file
//                        while ((line = br.readLine()) != null){
//                                line = line.trim();
//                                if (line.length() == 0) {
//                                        continue;
//                                }
//
//                                // split line into space-separated pieces
//                                String[] pieces = line.split("\\s+");
//
//                                // grab topic id
//                                String topId = pieces[0];
//                                // grab docno
//                                String docNo = pieces[2];
//                                // grab relevance judgment of docno with respect to this topic
//                                boolean relevant = Integer.parseInt(pieces[3]) > 0;
//
//                                // add topic entry to the feedback map
//                                if (!feedbackMap.contains(topId)) {
//                                        feedbackMap.put(topId, new Feedback());
//                                }
//
//                                // add docno to the appropriate feedback list for this topic
//                                if (relevant) {
//                                        feedbackMap.get(topId).getPositiveDocs().add(docNo);
//                                }
//                                else {
//                                        feedbackMap.get(topId).getNegativeDocs().add(docNo);
//                                }
//                        }
//
//                        br.close();
//
//                } catch(IOException e){
//                        e.printStackTrace();
//			System.exit(1);
//                }
//        }*/
//	
//	private void loadDocumentFilter(){
//		System.out.println("loading documents...");
//		queryidDocnosMap = new THashMap<String, THashSet<String>>();
//		String filterFilename = ApplicationSetup.getProperty("per.query.filter.filename", "");
//		try{
//			BufferedReader br = Files.openFileReader(filterFilename);
//			String str = null;
//			/* A line should look like: 
//				queryid docno1 docno2 ...
//				850 docno1 docno2 ...
//				851 docnox docnoy ...
//			  */
//			while ((str=br.readLine())!=null){
//				str = str.trim();
//				if (str.length()==0)
//					continue;
//				String[] tokens = str.split("\\s* \\s*");
//				THashSet<String> docnoSet = new THashSet<String>(); 
//				for (int i=1; i<tokens.length; i++)
//					docnoSet.add(tokens[i]);
//				queryidDocnosMap.put(tokens[0], docnoSet);
//			}
//			br.close();
//		}catch(IOException ioe){
//			ioe.printStackTrace();
//			System.exit(1);
//		}
//	}
//	
//	private void convertToDocids(DocumentIndex docIndex){
//		this.queryidDocidsMap = new THashMap<String, TIntHashSet>();
//		String[] queryids = (String[])queryidDocnosMap.keySet().toArray(new String[queryidDocnosMap.size()]);
//		for (int i=0; i<queryids.length; i++){
//			THashSet<String> docnoSet = queryidDocnosMap.get(queryids[i]);
//			String[] docnos = (String[])docnoSet.toArray(new String[docnoSet.size()]);
//			TIntHashSet docidSet = new TIntHashSet();
//			for (int j=0; j<docnos.length; j++)
//				docidSet.add(docIndex.getDocumentId(docnos[j]));
//			queryidDocidsMap.put(queryids[i], docidSet);
//		}
//		queryidDocnosMap.clear(); queryidDocnosMap = null;
//	}
//			
//	public void new_query(Manager m, SearchRequest srq, ResultSet rs)
//	{
//		
//	}
//
//	/**
//	  * Called for each result in the resultset, used to filter out unwanted results.
//	  * @param m The manager controlling this query
//	  * @param srq The search request being processed
//	  * @param DocAtNumber which array index in the resultset have we reached
//	  * @param DocNo The document number of the currently being procesed result.
//	  */
//	public byte filter(Manager m, SearchRequest srq, ResultSet rs, int DocAtNumber, int DocNo)
//	{
//		if (queryidDocidsMap == null)
//			this.convertToDocids(m.getIndex().getDocumentIndex());
//		TIntHashSet docidSet = this.queryidDocidsMap.get(srq.getQueryID());
//		if (docidSet == null)
//			return FILTER_OK;
//		else if (docidSet.contains(DocNo))
//		{
//			return FILTER_REMOVE;
//		}
//		return FILTER_OK;
//	}
//}
