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
package org.apache.lucene.postProcess;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;

import gnu.trove.TFloatArrayList;
import gnu.trove.THashMap;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntHashSet;

import org.apache.log4j.Logger;
import org.apache.lucene.postProcess.RF08FeedbackSelector.Feedback;
import org.apache.lucene.search.ScoreDoc;
import org.dutir.lucene.evaluation.TRECQrelsInMemory;
import org.dutir.lucene.util.ApplicationSetup;
import org.dutir.lucene.util.Files;
import org.dutir.util.Pair;

/**
 * A feedback selector for *Both* pseudo-relevance feedback and real relevance
 * feedback. Selects the top ApplicationSetup.EXPANSION_DOCUMENTS documents from
 * the ResultSet attached to the specified request.
 */
public class PseudoRelevanceFeedbackSelector extends FeedbackSelector {
	Logger logger = Logger.getLogger(PseudoRelevanceFeedbackSelector.class);
	private ScoreDoc[] ScoreDoc;
	static boolean Relevance = Boolean.parseBoolean(ApplicationSetup
			.getProperty("QueryExpansion.RelevanceFeedback", "false"));
	static TRECQrelsInMemory trecR = null;

	protected TRECQrelsInMemory getTRECQerls() {
		if (trecR == null) {
			logger.info("Getting  TRECQrelsInMemory");
			trecR = new TRECQrelsInMemory();
			String feedbackfile = ApplicationSetup.getProperty(
					"QueryExpansion.FeedbackfromFile", "false");
			if (!feedbackfile.equalsIgnoreCase("false")) {
				loadrfFeedback(feedbackfile);
			}
		}
		return trecR;
	}

	public PseudoRelevanceFeedbackSelector() {
	}

	protected void loadrfFeedback(String filename) {
		try {
			logger.info("Loading feedback information from: " + filename);
			BufferedReader br = Files.openFileReader(filename);
			String line = null;
			// for each line in the feedback (qrels) file

			TIntArrayList ranklist = new TIntArrayList();
			TIntArrayList docidlist = new TIntArrayList();
			TFloatArrayList scorelist = new TFloatArrayList();
			TFloatArrayList doclenlist = new TFloatArrayList();
			float totaldoclen = 0;
			String pretopic = "";
			while ((line = br.readLine()) != null) {
				line = line.trim();
				if (line.length() == 0) {
					continue;
				}

				// split line into space-separated pieces
				String[] pieces = line.split("\\s+");
				// grab topic id
				String topId = pieces[0];
				// grab docno
				String docNo = pieces[2];
				int docint = Integer.parseInt(docNo);
				int rank = Integer.parseInt(pieces[3]);
				float score = Float.parseFloat(pieces[4]);
				float doclen = this.searcher.getFieldLength(field, docint);
				totaldoclen += doclen;

				if (topId.equals(pretopic)) {
					ranklist.add(rank);
					docidlist.add(docint);
					scorelist.add(score);
					doclenlist.add(doclen);
				} else {
					if (pretopic.equals("")) {
						pretopic = topId;
						ranklist.add(rank);
						docidlist.add(docint);
						scorelist.add(score);
						doclenlist.add(doclen);
						continue;
					} else {
						feedbackMap.put(
								pretopic,
								new FeedbackDocuments(
										docidlist.toNativeArray(), ranklist
												.toNativeArray(), scorelist
												.toNativeArray(), totaldoclen,
								doclenlist.toNativeArray()));
						totaldoclen = 0;
						ranklist.clear();
						docidlist.clear();
						scorelist.clear();
						doclenlist.clear();
						pretopic = topId;
					}
				}
			}
			feedbackMap.put(
					pretopic,
					new FeedbackDocuments(docidlist.toNativeArray(), ranklist
							.toNativeArray(), scorelist.toNativeArray(),
							totaldoclen, doclenlist.toNativeArray() ));
			br.close();
			logger.info("added" +  feedbackMap.size() +" topics from file");
			if(logger.isDebugEnabled()){
				StringBuilder sb = new StringBuilder();
				for(String tp : feedbackMap.keySet()){
					sb.append(tp + "	");
				}
				logger.debug("topics: " + sb.toString());
			}
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	public FeedbackDocuments getFeedbackDocuments(String topicId) {
		int docIds[] = new int[0];
		float scores[] = new float[0];
		int maxPos = 0;
		getTRECQerls();	
		if (feedbackMap.containsKey(topicId)) {
			getTRECQerls();
			FeedbackDocuments fdocs = feedbackMap.get(topicId);
			logger.debug("get feedback dynamically: " + topicId + ", "
					+ fdocs.docid.length);
			return fdocs.toTopK(effDocuments);
		} else {
			if (Relevance) {
				if (trecR == null) {
					trecR = getTRECQerls();
				}
				int pos = 0;
				try {
					String relDocs[] = trecR
							.getRelevantDocumentsToArray(getTrimID(topicId));
					if (relDocs == null) {
						logger.warn("no relevance doc for query: " + topicId);
						maxPos = 0;
					} else {
						maxPos = Math.min(effDocuments, relDocs.length);
						docIds = new int[maxPos];
						scores = new float[maxPos];

						if (ApplicationSetup.Eval_ID) {
							for (int i = 0; i < relDocs.length && pos < maxPos; i++) {
								int _id = 0;
								try {
									_id = Integer.parseInt(relDocs[i]);
								} catch (Exception e) {
									logger.warn("false inner doc number: ", e);
									logger.warn("false doc: " + relDocs[i]);
									continue;
								}
								int docid = _id;
								docIds[pos] = docid;
								scores[pos] = 1;
								pos++;
							}
						} else {
							throw new UnsupportedOperationException();
						}
					}

				} catch (Exception e) {
					e.printStackTrace();
				}
			} else {
				maxPos = effDocuments;
				if (maxPos > this.ScoreDoc.length) {
					logger.warn("there is no sufficient feedback docs for Query "
							+ topicId
							+ ", "
							+ maxPos
							+ ":"
							+ this.ScoreDoc.length);
				}
				maxPos = Math.min(effDocuments, this.ScoreDoc.length);
				docIds = new int[maxPos];
				scores = new float[maxPos];
				for (int i = 0; i < maxPos; i++) {
					docIds[i] = this.ScoreDoc[i].doc;
					scores[i] = this.ScoreDoc[i].score;
				}
			}
		}
		FeedbackDocuments fdocs = new FeedbackDocuments();
		fdocs.totalDocumentLength = 0;
		fdocs.docid = docIds;
		fdocs.score = scores;
		for (int i = 0; i < maxPos; i++) {
			fdocs.totalDocumentLength += searcher.getFieldLength(field,
					docIds[i]);
		}
		return fdocs;
	}

	public void setTopDocs(ScoreDoc[] ScoreDoc) {
		this.ScoreDoc = ScoreDoc;
	}

	@Override
	public String getInfo() {
		String feedbackfile = ApplicationSetup.getProperty(
				"QueryExpansion.FeedbackfromFile", "false");
		int pos = feedbackfile.lastIndexOf("/");
		return feedbackfile.equalsIgnoreCase("false") ? "FromTop" : "FromFile_" + feedbackfile.substring(pos+1);
	}
	
	public static void main(String args[]){
		String feedbackfile = "/data/Dropbox/workspace/experiment/wt10g/WT10GT451-550.train.quality.2.ipr5level.scaled.f2.run";
		int pos = feedbackfile.lastIndexOf("/");
		System.out.println(feedbackfile.substring(pos+1));
	}

}
