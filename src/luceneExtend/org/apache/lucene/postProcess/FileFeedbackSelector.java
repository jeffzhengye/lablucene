package org.apache.lucene.postProcess;

import gnu.trove.THashMap;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;

import org.apache.log4j.Logger;
import org.dutir.lucene.util.ApplicationSetup;
import org.dutir.lucene.util.Files;
import org.dutir.util.Pair;

public class FileFeedbackSelector extends FeedbackSelector {

	static Logger logger = Logger.getLogger("FileFeedbackSelector");
	static boolean mixTag = Boolean.parseBoolean(ApplicationSetup.getProperty(
			"FileFeedbackSelector.mixTag", "false"));

	static THashMap<String, THashMap<String, Feedback>> file2feedbackMap = new THashMap<String, THashMap<String, Feedback>>();
	THashMap<String, Feedback> feedbackMap = null;

	THashMap<String, Feedback> rf08Map = null;

	public FileFeedbackSelector() {

	}

	public FeedbackDocuments getFeedbackDocuments(String topicId) {
		if (this.feedbackMap == null)
			loadFeedback(ApplicationSetup.getProperty(
					"Rocchio.Feedback.filename", ""));
			ArrayList<Pair<String, Float>> list = this.feedbackMap.get(topicId)
					.getPositiveDocs();
			if (list.size() < effDocuments) {
				logger.error("did not have enough feedback docs: Query "
						+ topicId);
			}
			int maxPos = Math.min(effDocuments, list.size());
			int[] docIds = new int[effDocuments];
			float[] scores = new float[effDocuments];
			Pair<String, Float> pair = null;
			for (int i = 0; i < maxPos; i++) {
				pair = list.get(i);
				try {
					docIds[i] = Integer.parseInt(pair.first);
				} catch (NumberFormatException e) {
					docIds[i] = getInnerDocid(pair.first);
				}
				scores[i] = pair.second;
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

	protected void loadFeedback(String filename) {

		try {
			feedbackMap = file2feedbackMap.get(filename);
			if (feedbackMap != null) {
				return;
			} else {
				feedbackMap = new THashMap<String, Feedback>();
				file2feedbackMap.put(filename, feedbackMap);
			}
			if (logger.isInfoEnabled())
				logger.info("Loading feedback information from: " + filename);
			BufferedReader br = Files.openFileReader(filename);
			String line = null;
			// for each line in the feedback (qrels) file
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
				float score = Float.parseFloat(pieces[4]);
				// grab relevance judgment of docno with respect to this topic
				// boolean relevant = // Integer.parseInt(pieces[3]) > 0;
				// qrels.isRelevant(topId, docNo);
				// add topic entry to the feedback map
				if (!feedbackMap.contains(topId)) {
					feedbackMap.put(topId, new Feedback());
				}

				// add docno to the appropriate feedback list for this topic
				// if (relevant) {
				feedbackMap.get(topId).getPositiveDocs().add(
						new Pair<String, Float>(docNo, score));
				// } else {
				// feedbackMap.get(topId).getNegativeDocs().add(docNo);
				// }
			}
			br.close();
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	static class Feedback {
		/** list of positive feedback documents */
		private ArrayList<Pair<String, Float>> positiveDocs;
		/** list of negative feedback documents */
		private ArrayList<Pair<String, Float>> negativeDocs;

		public Feedback() {
			positiveDocs = new ArrayList<Pair<String, Float>>();
			negativeDocs = new ArrayList<Pair<String, Float>>();
		}

		public ArrayList<Pair<String, Float>> getPositiveDocs() {
			return positiveDocs;
		}

		public ArrayList<Pair<String, Float>> getNegativeDocs() {
			return negativeDocs;
		}
	}

	@Override
	public String getInfo() {
		return "";
	}
}
