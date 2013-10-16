package org.apache.lucene.postProcess;

import gnu.trove.TIntDoubleHashMap;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;

import org.apache.log4j.Logger;
import org.apache.lucene.index.TermFreqVector;
import org.apache.lucene.search.RBooleanQuery;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.search.TopDocCollector;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.model.Idf;
import org.dutir.lucene.evaluation.AdhocEvaluation;
import org.dutir.lucene.util.ApplicationSetup;
import org.dutir.lucene.util.ExpansionTerms;
import org.dutir.lucene.util.TermsCache.Item;
import org.dutir.util.Arrays;
import org.dutir.util.stream.StreamGenerator;

/**
 * used for sigir13 Feature Extraction for Document Quality Estimation.
 * 
 * Features: 1. Mitra 2. abs(docLen - aveLen) 3. find good doc 4. Lv 5. doc
 * quality paper 6. possible another aspect score measure.
 * 
 * @author zheng
 * 
 */

public class FeatureExtract13PP extends MitraReRankingPostProcess {

	protected static Logger logger = Logger.getLogger("FeatureExtract13PP");

	private static AdhocEvaluation trecR = null;

	public static AdhocEvaluation getTRECQerls() {
		if (trecR == null) {
			trecR = new AdhocEvaluation();
		}
		return trecR;
	}

	static String outfile = "SVMreg.res";
	static String modelout = "reg.model";
	static ArrayList<TIntDoubleHashMap> insts = new ArrayList<TIntDoubleHashMap>();
	static ArrayList<Float> grades = new ArrayList<Float>();
	static ArrayList<Float> grades1 = new ArrayList<Float>();

	static ArrayList<String> trainPara = new ArrayList<String>();

	static String classfierName = ApplicationSetup.getProperty(
			"PerQueryRegModelTraining.reg", "SVMreg");
	String lasttopic = ApplicationSetup.getProperty(
			"PerQueryRegModelTraining.lasttopic", ApplicationSetup.getProperty("trec.query.endid", ""));

	// boolean trainingTag = Boolean.parseBoolean(ApplicationSetup.getProperty(
	// "PerQueryRegModelTraining.train", "true"));

	protected void makeInstance(int tid) {
		float avelen = this.averageDocumentLength;

		for (int i = 0; i < this.set_size; i++) {
			TIntDoubleHashMap instance = new TIntDoubleHashMap();
			float relscore = this.scores[i]; // relevance score, id=1
			float doc_len = Math.abs(avelen - this.doc_lens[i]); // id =2
			// id=3, the percentage of query terms that appear in doc[i]
			// id=4, a weighted percentage
			// id=5, Mitra score

			// id =6, KL-div between query and doc[i]
			// id =7, clarity of doc[i]

			instance.put(0, tid);
			instance.adjustOrPutValue(1, 0, relscore);
			instance.adjustOrPutValue(2, 0, doc_len);
			instance.adjustOrPutValue(3, 0, this.occurPerc[i]);
			instance.adjustOrPutValue(4, 0, this.weighted_occurPerc[i]);
			instance.adjustOrPutValue(5, 0, this.mitra_socres[i]);
			instance.adjustOrPutValue(6, 0, this.query_doc_kl[i]);
			instance.adjustOrPutValue(7, 0, this.doc_clarity[i]);
			instance.adjustOrPutValue(8, 0, this.doc_topK_clarity[i]);
			instance.adjustOrPutValue(9, 0, this.docids[i]); // this feature
																// only stores
																// the docno for
																// evaluation
																// purpose

			// last one is quality level
			int inner_id = this.ScoreDoc[i].doc;
			int level = this.trecR.qrels.getRelevantGrad("" + tid, ""
					+ inner_id);
			instance.adjustOrPutValue(10, 0, level);
			addInstance(instance);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.apache.lucene.postProcess.PostProcess#postProcess(org.apache.lucene
	 * .search.TopDocCollector, org.apache.lucene.search.Searcher)
	 */
	public TopDocCollector postProcess(RBooleanQuery query,
			TopDocCollector topDoc, Searcher seacher) {
		setup(query, topDoc, seacher); // it is necessary
		try {
			getTRECQerls();
			pre_process();
		} catch (Exception e) {
			e.printStackTrace();
		}

		// ////////////////////////////////////////////////////

		String topicId = query.getTopicId();
		int int_topicId = Integer.parseInt(topicId);
		makeInstance(int_topicId);

		// // output(topDoc);
		String content = outputStr(topDoc);

		trecR.evaluateStr(content);
		double map = trecR.AveragePrecision;
		for (int i = 0; i < this.set_size; i++) {
			FeedbackSelector fselector = this.getFeedbackSelector(seacher);
			fselector.putFeedbackDocs(topicId,
					java.util.Arrays.copyOfRange(docids, i, i + 1),
					java.util.Arrays.copyOfRange(scores, i, i + 1));
			QueryExpansionAdap qea = new QueryExpansionAdap();
			TopDocCollector tdc = qea.postProcess(query, topDoc, seacher);
			content = outputStr(tdc);
			trecR.evaluateStr(content);
			double map_new = trecR.AveragePrecision;
			grades.add((float) (map_new - map));
		}
		// /////////////////////////////////////////////////////////////////////

		// trainPara.add(trainBuf.toString());
		if (topicId.equalsIgnoreCase(lasttopic)) {
			build_save();
		}
		// besttdc.setInfo(this.getInfo() + topDoc.getInfo() + para);
		// besttdc.setInfo_add(QEModel.getInfo());
		// return besttdc;
		return topDoc;
	}

	private void build_save() {
		if (true) {
			try {
				String prefix = ApplicationSetup.getProperty("lucene.etc",
						"etc");
				prefix = prefix == null ? ApplicationSetup.LUCENE_ETC : prefix;
				int pos = prefix.lastIndexOf('/');
				prefix = prefix.substring(pos+1);

				BufferedWriter bout = StreamGenerator.getBufferFileWriter(
						prefix + ".train.quality.1." + lasttopic, 2);
				for (int i = 0; i < insts.size(); i++) {
					bout.write(toString(insts.get(i)) + "\n");
				}
				bout.close();
				// ////////////////////////////////////////////////////////
				 bout = StreamGenerator.getBufferFileWriter(prefix +
				 ".train.quality.2." + lasttopic, 2);
				 for (int i = 0; i < insts.size(); i++) {
				 bout.write(grades.get(i) + " " + toString(insts.get(i)) +
				 "\n");
				 }
				 bout.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private String toString(TIntDoubleHashMap inst) {
		StringBuilder buf = new StringBuilder();
		buf.append((int) inst.get(inst.size() - 1) + " ");
		buf.append("qid:" + (int) inst.get(0) + " ");
		for (int i = 1; i < inst.size() - 2; i++) {
			buf.append("" + i + ":" + inst.get(i) + " ");
		}
		buf.append(" #docno=" + (int) inst.get(inst.size() - 2));
		return buf.toString();
	}

	protected static String toPythonPredictStr(TIntDoubleHashMap inst) {
		StringBuilder buf = new StringBuilder();
		for (int i = 1; i < inst.size() - 1; i++) {
			buf.append("" + inst.get(i) + " ");
		}
		return buf.toString();
	}

	private void addInstance(TIntDoubleHashMap inst) {
		insts.add(inst);
	}

	private void output(TopDocCollector topDoc) {
		TopDocs topDocs = topDoc.topDocs();
		int len = topDocs.totalHits;
		PrintWriter pw = null;
		try {
			pw = new PrintWriter(new File(outfile));
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		}

		int maximum = Math.min(topDocs.scoreDocs.length, 1000);

		// if (minimum > set.getResultSize())
		// minimum = set.getResultSize();
		final String iteration = "Q" + "0";
		final String queryIdExpanded = this.topicId + " " + iteration + " ";
		final String methodExpanded = " " + "LabLucene" + ApplicationSetup.EOL;
		StringBuilder sbuffer = new StringBuilder();
		// the results are ordered in descending order
		// with respect to the score.
		for (int i = 0; i < maximum; i++) {
			int docid = topDocs.scoreDocs[i].doc;
			String filename = "" + docid;
			float score = topDocs.scoreDocs[i].score;

			if (filename != null && !filename.equals(filename.trim())) {
				if (logger.isDebugEnabled())
					logger.debug("orginal doc name not trimmed: |" + filename
							+ "|");
			} else if (filename == null) {
				logger.error("inner docid does not exist: " + docid
						+ ", score:" + score);
				if (docid > 0) {
					try {
						logger.error("previous docno: "
								+ this.searcher.doc(docid - 1).toString());
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				continue;
			}
			sbuffer.append(queryIdExpanded);
			sbuffer.append(filename);
			sbuffer.append(" ");
			sbuffer.append(i);
			sbuffer.append(" ");
			sbuffer.append(score);
			sbuffer.append(methodExpanded);
		}
		pw.write(sbuffer.toString());
		pw.close();
	}

	public String getInfo() {
		return ("PerQueryOptimal");
	}
	
	public static void main(String args[]){
		String prefix = "/data/Dropbox/wt10g.1";
		int pos = prefix.lastIndexOf('/');
		prefix = prefix.substring(pos+1);
		System.out.println(prefix);
	}
}
