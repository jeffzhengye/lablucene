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
import org.apache.lucene.postProcess.termselector.TermSelector;
import org.apache.lucene.search.RBooleanQuery;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.search.TopDocCollector;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.model.Idf;
import org.dutir.lucene.evaluation.TRECQrelsInMemory;
import org.dutir.lucene.util.ApplicationSetup;
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

public class MATFDistributionAnalysis extends QueryExpansion {

	protected static Logger logger = Logger.getLogger("FeatureExtract13PP");

	private static TRECQrelsInMemory trecR = null;

	public static TRECQrelsInMemory getTRECQerls() {
		if (trecR == null) {
			logger.info("Getting  TRECQrelsInMemory");
			trecR = new TRECQrelsInMemory();
		}
		return trecR;
	}

	static ArrayList<TIntDoubleHashMap> insts = new ArrayList<TIntDoubleHashMap>();
	static ArrayList<Float> grades = new ArrayList<Float>();
	static ArrayList<Float> grades1 = new ArrayList<Float>();

	static ArrayList<String> trainPara = new ArrayList<String>();

	String lasttopic = ApplicationSetup.getProperty(
			"dataset.lasttopic", ApplicationSetup.getProperty("trec.query.endid", ""));

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
		setup(query, topDoc, seacher); // it is necessary
		try {
			getTRECQerls();
		} catch (Exception e) {
			e.printStackTrace();
		}

		// ////////////////////////////////////////////////////

		String topicId = query.getTopicId();
		int int_topicId = Integer.parseInt(topicId);
		FeedbackSelector fselector = this.getFeedbackSelector(seacher);
		FeedbackDocuments fdocs = fselector.getFeedbackDocuments(topicId);
		TermSelector selector = TermSelector
				.getDefaultTermSelector(this.searcher);
		selector.setResultSet(topDoc);
		selector.setOriginalQueryTerms(termSet);
		selector.setField(field);


		for (int i = 0; i < fdocs.docid.length; i++) {
				int docid = fdocs.docid[i];
				int int_relevancy = trecR.isRelevant(topicId, ""+docid) ?1:0;
				TIntDoubleHashMap instance = new TIntDoubleHashMap();
				instance.put(0, int_relevancy); // 0: relevance
				
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
					float docLength = Arrays.sum(freqs);
					float AVF = docLength/strterms.length;
					float alpha = 2 / (1 + Idf.log(1 + this.originalQueryLength));
					instance.adjustOrPutValue(1, 0, docLength); //1: doc length
					instance.adjustOrPutValue(2, 0, this.originalQueryLength); //2 : query length
					instance.adjustOrPutValue(3, 0, AVF); //3 : average term frequency

					for (int j = 0; j < strterms.length; j++) {
//							Item item = getItem(strterms[j]);
						if(this.termSet.contains(strterms[j])){
							float RITF = Idf.log(1 + freqs[j])/Idf.log(1 + AVF);
							float LRTF = freqs[j] * Idf.log(1 + averageDocumentLength/docLength);
							float BRITF = RITF/ (1 + RITF);
							float BLRTF = LRTF / (1 + LRTF);
							float TFF = alpha * BRITF + (1 - alpha) * BLRTF;
							instance.adjustOrPutValue(4, 0, BRITF); //4 : BRITF
							instance.adjustOrPutValue(5, 0, BLRTF); //5 : BRITF
							instance.adjustOrPutValue(6, 0, TFF); //6 : TFF
						}
					}
					
					instance.put(4, instance.get(4)/this.originalQueryLength);
					instance.put(5, instance.get(5)/this.originalQueryLength);
					instance.put(6, instance.get(6)/this.originalQueryLength);
					addInstance(instance);
				}
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
						prefix + ".matf." + lasttopic, 2);
				for (int i = 0; i < insts.size(); i++) {
					bout.write(toString(insts.get(i)) + "\n");
				}
				bout.close();
				} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private String toString(TIntDoubleHashMap inst) {
		StringBuilder buf = new StringBuilder();
//		buf.append((int) inst.get(inst.size() - 1) + " ");
//		buf.append("qid:" + (int) inst.get(0) + " ");
		for (int i = 0; i < inst.size(); i++) {
			buf.append("" + inst.get(i) + " ");
		}
		return buf.toString().trim();
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
