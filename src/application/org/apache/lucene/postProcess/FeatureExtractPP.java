package org.apache.lucene.postProcess;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;

import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermFreqVector;
import org.apache.lucene.search.RBooleanQuery;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.search.TopDocCollector;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.model.Idf;
import org.dutir.lucene.evaluation.AdhocEvaluation;
import org.dutir.lucene.util.ApplicationSetup;
import org.dutir.lucene.util.ExpansionTerms;
import org.dutir.lucene.util.TermsCache;
import org.dutir.lucene.util.TermsCache.Item;
import org.dutir.util.AbstractExternalizable;
import org.dutir.util.Arrays;
import org.dutir.util.stream.StreamGenerator;

import gnu.trove.TIntDoubleHashMap;

/**
 * used for sigir13 Feature Extraction for Document Quality Estimation.
 * 
 * @author zheng
 * 
 */
public class FeatureExtractPP extends QueryExpansion {

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
	static ArrayList<String> trainPara = new ArrayList<String>();

	static String classfierName = ApplicationSetup.getProperty(
			"PerQueryRegModelTraining.reg", "SVMreg");
	String lasttopic = ApplicationSetup.getProperty(
			"PerQueryRegModelTraining.lasttopic", "500");
	boolean trainingTag = Boolean.parseBoolean(ApplicationSetup.getProperty(
			"PerQueryRegModelTraining.train", "true"));


	protected TIntDoubleHashMap makeInstance(float beta, int tid) {
		TIntDoubleHashMap instance = new TIntDoubleHashMap();

		FeedbackSelector fselector = this.getFeedbackSelector(this.searcher);
		FeedbackDocuments fdocs = fselector.getFeedbackDocuments(topicId);
		ExpansionTerms expTerms = null;
		try {
			expTerms = new ExpansionTerms(this.searcher, 0, field);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		float totalDocLength = 0;
		for (int i = 0; i < fdocs.docid.length; i++) {
			int docid = fdocs.docid[i];
			TermFreqVector tfv = null;
			try {
				tfv = this.searcher.getIndexReader().getTermFreqVector(docid,
						field);
			} catch (IOException e) {
				e.printStackTrace();
			}
			if (tfv == null)
				logger.warn("document " + docid + " not found, field=" + field);
			else {
				String strterms[] = tfv.getTerms();
				int freqs[] = tfv.getTermFrequencies();
				totalDocLength += Arrays.sum(freqs);
				for (int j = 0; j < strterms.length; j++) {
					expTerms.insertTerm(strterms[j], freqs[j]);
				}
			}
		}
		expTerms.setTotalDocumentLength(totalDocLength);
		
		//Average Inverse Collection Term Frequency (AvICTF) (He & Ounis, 2005)
		double aveICTF = 0;
		
		// ////////////////////clarity1
		double clarity1 = 0;
		float pwq = 1f / this.termSet.size();
		float totalNumTokens = this.searcher.getNumTokens(field);
		for (String term : this.termSet) {
			Item item = getItem(term);
			clarity1 += pwq * Idf.log(pwq / (item.ctf / totalNumTokens));
			aveICTF += Idf.log(totalNumTokens / item.ctf)/ this.termSet.size();
		}

		// ////////////////////clarity2 and QueryEntropy
		String terms[] = expTerms.getTerms();
		double queryentropy = 0;
		double clarity2 = 0;
		for (int i = 0; i < terms.length; i++) {
			String term = terms[i];
			Item item = getItem(term);
			float pwf = expTerms.getFrequency(term) / totalDocLength;
			queryentropy += -pwf * Idf.log(pwf);
			clarity2 += pwf * Idf.log(pwf / (item.ctf / totalNumTokens));
		}

		instance.put(0, tid);
		instance.adjustOrPutValue(1, 0, this.termSet.size());
		instance.adjustOrPutValue(2, 0, queryentropy);
		instance.adjustOrPutValue(3, 0, clarity1);
		instance.adjustOrPutValue(4, 0, clarity2);
		instance.adjustOrPutValue(5, 0, aveICTF);
//		instance.adjustOrPutValue(6, 0, aveICTF); //BM25: original retrieval score
		instance.adjustOrPutValue(6, 0, ApplicationSetup.EXPANSION_DOCUMENTS);
		instance.adjustOrPutValue(7, 0, beta);

		return instance;
	}

	static TermsCache tcache = TermsCache.getInstance();

	protected Item getItem(String term) {
		Term lterm = new Term(field, term);
		return tcache.getItem(lterm, searcher);
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

//		output(topDoc);
		String content = outputStr(topDoc);
		getTRECQerls();
//		trecR.evaluate(outfile);
		trecR.evaluateStr(content);
		
		float optBeta = 0;
		TopDocCollector besttdc = topDoc;
		double map = trecR.AveragePrecision;
		String para = "";
		
		StringBuffer trainBuf = new StringBuffer();
		trainBuf.append(query.getTopicId() + " ");
		// find the best interpolation parameter.
		for (float beta = 0.1f; beta < 1.5; beta += 0.1) {
			QueryExpansionAdap qea = new QueryExpansionAdap();
			qea.QEModel.ROCCHIO_BETA = beta;
			TopDocCollector tdc = qea.postProcess(query, topDoc, seacher);
			output(tdc);
			trecR.evaluate(outfile);
			para = qea.getInfo();
			trainBuf.append("" + beta + ":" + trecR.AveragePrecision + " ");
			if (map < trecR.AveragePrecision) {
				optBeta = beta;
				map = trecR.AveragePrecision;
				besttdc = tdc;
			}
		}

		System.out.println(this.topicId + ", OptBeta: " + optBeta);

		TIntDoubleHashMap inst = makeInstance(optBeta, Integer.parseInt(query.getTopicId()));
		
		addInstance(inst);
		trainPara.add(trainBuf.toString());
		if (topicId.equalsIgnoreCase(lasttopic)) {
			build_save();
		}
		besttdc.setInfo(this.getInfo() + topDoc.getInfo() + para);
		besttdc.setInfo_add(QEModel.getInfo());
		return besttdc;
	}

	private void build_save() {
		if (true) {
			try {
				BufferedWriter bout = StreamGenerator.getBufferFileWriter(
						"train.sample", 2);
				for (int i = 0; i < insts.size(); i++) {
					bout.write(toString(insts.get(i)) + "\n");
				}
				bout.close();
				
				//////////////////////////////////////////////////////////
				bout = StreamGenerator.getBufferFileWriter(
						"trainOpt.sample", 2);
				for (int i = 0; i < trainPara.size(); i++) {
					bout.write(trainPara.get(i) + "\n");
				}
				bout.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private static String toString(TIntDoubleHashMap inst) {
		StringBuilder buf = new StringBuilder();
		buf.append(inst.get(inst.size() - 1) + " ");
		buf.append("qid:" + (int)inst.get(0) + " ");
		for(int i= 1; i <inst.size()- 1; i++){
			buf.append("" + i +  ":" + inst.get(i) + " ");
		}
		return buf.toString();
	}
	
	
	protected static String toPythonPredictStr(TIntDoubleHashMap inst){
		StringBuilder buf = new StringBuilder();
		for(int i= 1; i <inst.size()- 1; i++){
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

}
