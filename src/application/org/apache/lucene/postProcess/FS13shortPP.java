package org.apache.lucene.postProcess;

import gnu.trove.TIntDoubleHashMap;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;

import org.apache.log4j.Logger;
import org.apache.lucene.postProcess.termselector.DFRTermSelector;
import org.apache.lucene.search.RBooleanQuery;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.search.TopDocCollector;
import org.apache.lucene.search.TopDocs;
import org.dutir.lucene.evaluation.AdhocEvaluation;
import org.dutir.lucene.util.ApplicationSetup;
import org.dutir.lucene.util.ExpansionTerms.ExpansionTerm;
import org.dutir.util.stream.StreamGenerator;

/**
 * used for sigir13 short Feature Extraction for James
 * 
 * 
 * @author zheng
 * 
 */

public class FS13shortPP extends MitraReRankingPostProcess {

	protected static Logger logger = Logger.getLogger("FS13shortPP");

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
			"PerQueryRegModelTraining.lasttopic",
			ApplicationSetup.getProperty("trec.query.endid", ""));

	// boolean trainingTag = Boolean.parseBoolean(ApplicationSetup.getProperty(
	// "PerQueryRegModelTraining.train", "true"));

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.apache.lucene.postProcess.PostProcess#postProcess(org.apache.lucene
	 * .search.TopDocCollector, org.apache.lucene.search.Searcher)
	 */
	public TopDocCollector postProcess(RBooleanQuery query,
			TopDocCollector topDoc, Searcher seacher) {
		logger.info	("Begin sigir13short PP");
		setup(query, topDoc, seacher); // it is necessary
		try {
			getTRECQerls();
			set_size = this.ScoreDoc.length;
			docids = new int[set_size];
			scores = new float[set_size];
			for (int i = 0; i < set_size; i++) {
				docids[i] = this.ScoreDoc[i].doc;
				scores[i] = this.ScoreDoc[i].score;
			}
			// ////////////////////////////////////////////////////

			String topicId = query.getTopicId();
			int int_topicId = Integer.parseInt(topicId);

			BufferedWriter bw = new BufferedWriter(new FileWriter(
					"sigir13short.feature", true));
			for (int i = 0; i < this.set_size; i++) {
				DFRTermSelector selector = new DFRTermSelector();
				selector.setSearcher(this.searcher);
				selector.assignTermWeights(
						java.util.Arrays.copyOfRange(docids, i, i + 1),
						java.util.Arrays.copyOfRange(scores, i, i + 1), QEModel);
				StringBuffer buf = new StringBuffer();
				ExpansionTerm[] eTerms = selector.getMostWeightedTerms(selector.getNumberOfUniqueTerms());
				buf.append(topicId + " " + docids[i] + " ");
				for(ExpansionTerm t: eTerms){
					buf.append(t.getTerm() + ":" + t.getWeightExpansion() + " ");
				}
//				logger.info	(buf.toString().trim());
				bw.write(buf.toString().trim() + "\n");
			}
			bw.flush();	bw.close();

		} catch (Exception e) {
			e.printStackTrace();
		}

		return topDoc;
	}

	private void build_save() {
		if (true) {
			try {
				String prefix = ApplicationSetup.getProperty("lucene.etc",
						"etc");
				prefix = prefix == null ? ApplicationSetup.LUCENE_ETC : prefix;
				int pos = prefix.lastIndexOf('/');
				prefix = prefix.substring(pos + 1);

				BufferedWriter bout = StreamGenerator.getBufferFileWriter(
						prefix + ".train.quality.1." + lasttopic, 2);
				for (int i = 0; i < insts.size(); i++) {
					bout.write(toString(insts.get(i)) + "\n");
				}
				bout.close();
				// ////////////////////////////////////////////////////////
				bout = StreamGenerator.getBufferFileWriter(prefix
						+ ".train.quality.2." + lasttopic, 2);
				for (int i = 0; i < insts.size(); i++) {
					bout.write(grades.get(i) + " " + toString(insts.get(i))
							+ "\n");
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

	public static void main(String args[]) {
		String prefix = "/data/Dropbox/wt10g.1";
		int pos = prefix.lastIndexOf('/');
		prefix = prefix.substring(pos + 1);
		System.out.println(prefix);
	}
}
