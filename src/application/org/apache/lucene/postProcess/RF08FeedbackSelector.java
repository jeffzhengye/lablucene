package org.apache.lucene.postProcess;

import gnu.trove.THashMap;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;

import org.apache.log4j.Logger;
import org.apache.lucene.document.Document;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.model.Idf;
import org.dutir.lucene.evaluation.TRECQrelsInMemory;
import org.dutir.lucene.util.ApplicationSetup;
import org.dutir.lucene.util.Files;
import org.dutir.util.Normalizer;
import org.dutir.util.Pair;

/**
 * A feedback selector for pseudo-relevance feedback. Selects the top
 * ApplicationSetup.EXPANSION_DOCUMENTS documents from the ResultSet attached to
 * the specified request.
 */
public class RF08FeedbackSelector extends FeedbackSelector {
	Logger logger = Logger.getLogger(RF08FeedbackSelector.class);
	static boolean mixTag = Boolean.parseBoolean(ApplicationSetup.getProperty(
			"FileFeedbackSelector.mixTag", "true"));
	static float gama = Float.parseFloat((ApplicationSetup.getProperty(
			"FileFeedbackSelector.gama", "0.5")));
	static String rf08file = ApplicationSetup.getProperty(
			"FileFeedbackSelector.rf08file", "");

	static THashMap<String, THashMap<String, Feedback>> file2feedbackMap = new THashMap<String, THashMap<String, Feedback>>();
	THashMap<String, Feedback> feedbackMap = null;

	THashMap<String, Feedback> rf08Map = null;

	private ScoreDoc[] ScoreDoc;
	static boolean Relevance = false;
	
//	static String docidField = ApplicationSetup.getProperty("TrecDocTags.idtag",
//			"DOCNO");
	
	String getdocno(int innerid){
		String docno = null;
		try {
			Document doc = searcher.doc(innerid);
//			tweet = doc.get("content"); 
			docno = doc.get(docidField);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return docno;
	}
	// Boolean.parseBoolean(ApplicationSetup
	// .getProperty("QueryExpansion.RelevanceFeedback", "false"));
	static TRECQrelsInMemory trecR = null;

	public static TRECQrelsInMemory getTRECQerls() {
		if (trecR == null) {
			trecR = new TRECQrelsInMemory();
		}
		return trecR;
	}

	public RF08FeedbackSelector() {
	}

	public FeedbackDocuments getFeedbackDocuments(String topicId) {

		int docIds[] = new int[0];
		float scores[] = new float[0];
		int maxPos = 0;

		maxPos = effDocuments;
		int[] _docIds = new int[effDocuments];
		float[] _scores = new float[effDocuments];
		for (int i = 0; i < effDocuments; i++) {
			_docIds[i] = this.ScoreDoc[i].doc;
			_scores[i] = this.ScoreDoc[i].score;
		}
		norm(_scores);
		if (rf08Map == null)
			loadrfFeedback(rf08file);
		ArrayList<Pair<String, Float>> list = rf08Map.get(topicId)
				.getPositiveDocs();
		if (!mixTag) {
			effDocuments = Math.min(list.size(), effDocuments);
		}

		docIds = new int[effDocuments];
		scores = new float[effDocuments];

		int p = 0;
		for (; p < list.size() && p < effDocuments; p++) {
			Pair<String, Float> pair = list.get(p);
			try {
				docIds[p] = Integer.parseInt(pair.first);
			} catch (NumberFormatException e) {
				docIds[p] = getInnerDocid(pair.first);
			}
			scores[p] = pair.second;
		}
		for (int i = 0; p < effDocuments && i < _docIds.length; i++) {
			if (find(_docIds[i], docIds, p)) {
				continue;
			} else {
				docIds[p] = _docIds[i];
				scores[p] = gama * _scores[i];
				p++;
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

	static boolean LanguageModel = Boolean.parseBoolean(ApplicationSetup
			.getProperty("Lucene.Search.LanguageModel", "false"));

	private void norm(float[] values) {
		if (LanguageModel)
			indriNorm(values);
		// Normalizer.norm2(values);
		// Normalizer.norm_MaxMin_0_1(values);
		float max = values[0];
		for (int i = 0; i < values.length; i++)
			values[i] = values[i] / max;
	}

	private void indriNorm(float[] pQ) {

		float K = pQ[0]; // first is max
		float sum = 0;
		for (int i = 0; i < pQ.length; i++) {
			pQ[i] = Idf.exp(K + pQ[i]);
			sum += pQ[i];
		}
		for (int i = 0; i < pQ.length; i++) {
			pQ[i] /= sum;
		}
	}

	private boolean find(int v, int[] docIds, int p) {
		for (int i = 0; i < p && i < docIds.length; i++) {
			if (docIds[i] == v)
				return true;
		}
		return false;
	}

	public void setTopDocs(ScoreDoc[] ScoreDoc) {
		this.ScoreDoc = ScoreDoc;
	}

	protected void loadrfFeedback(String filename) {

		try {
			this.rf08Map = file2feedbackMap.get(filename);
			if (rf08Map != null) {
				return;
			} else {
				rf08Map = new THashMap<String, Feedback>();
				file2feedbackMap.put(filename, rf08Map);
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
				int level = Integer.parseInt(pieces[3].trim());
				float score = 1;
				// grab relevance judgment of docno with respect to this topic
				// boolean relevant = // Integer.parseInt(pieces[3]) > 0;
				// qrels.isRelevant(topId, docNo);
				// add topic entry to the feedback map
				if (!rf08Map.contains(topId)) {
					rf08Map.put(topId, new Feedback());
				}

				// add docno to the appropriate feedback list for this topic

				if (level > 0) {
					if (level > 1) {
						rf08Map.get(topId).getPositiveDocs().add(0,
								new Pair<String, Float>(docNo, (float)1));
					}else{
						rf08Map.get(topId).getPositiveDocs().add(
								new Pair<String, Float>(docNo, score));
					}
				} else {
					rf08Map.get(topId).getNegativeDocs().add(
							new Pair<String, Float>(docNo, score));
				}
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

	public String getInfo() {
		return "RF08Mix" + rf08file.charAt(rf08file.length() - 9) + "="
				+ mixTag + "Gama=" + gama;
	}

}
