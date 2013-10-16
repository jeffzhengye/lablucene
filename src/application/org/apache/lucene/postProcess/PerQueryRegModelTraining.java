package org.apache.lucene.postProcess;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;

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

import weka.classifiers.Classifier;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.functions.LibSVM;
import weka.classifiers.functions.SVMreg;
import weka.classifiers.functions.supportVector.NormalizedPolyKernel;
import weka.core.Attribute;
import weka.core.FastVector;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.SelectedTag;

/**
 * used for sigir12 short paper. 
 * @author zheng
 *
 */
public class PerQueryRegModelTraining extends QueryExpansion {
	
	private static AdhocEvaluation trecR =null;
	public static AdhocEvaluation getTRECQerls() {
		if (trecR  == null) {
			trecR = new AdhocEvaluation();
		}
		return trecR;
	}
	
	static String outfile = "SVMreg.res";
	static String modelout = "reg.model";
	private static Classifier classifier = null;
	private static Instances insts = null;
	static String classfierName = ApplicationSetup.getProperty("PerQueryRegModelTraining.reg", "SVMreg");
	String lasttopic = ApplicationSetup.getProperty("PerQueryRegModelTraining.lasttopic", "500");
	boolean trainingTag = Boolean.parseBoolean(ApplicationSetup.getProperty("PerQueryRegModelTraining.train", "true"));
	
	/**
	 * todo
	 * 
	 * @param classfierName
	 * @return
	 */
	private static Classifier getClassifier() {
		if(classifier == null){
			if (classfierName.equals("NaiveBayes")) {
				return (Classifier) new NaiveBayes();
			} else if (classfierName.equals("SVM")
					|| classfierName.equals("weka.classifiers.functions.LibSVM")) {
				LibSVM svm = new LibSVM();
				svm.setProbabilityEstimates(true);

				SelectedTag stag = new SelectedTag(LibSVM.SVMTYPE_EPSILON_SVR,
						LibSVM.TAGS_SVMTYPE);
				svm.setSVMType(stag);
				SelectedTag ktag = new SelectedTag(LibSVM.KERNELTYPE_SIGMOID,
						LibSVM.TAGS_KERNELTYPE);
				svm.setKernelType(ktag);
				classifier = (Classifier)svm;
				return  classifier;

			} else {
				try {
					classifier = (Classifier) Class.forName("weka.classifiers.functions." + classfierName).newInstance();
					if(classifier instanceof SVMreg){
						((SVMreg) classifier).setKernel(new NormalizedPolyKernel());
					}
					return classifier;
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		return classifier;
	}
	
	private static Classifier readClassifier(){
		if(classifier == null){
			try {
				classifier = 
				(Classifier) AbstractExternalizable.readObject(new File(modelout));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return classifier;
	}
	
	static int classid = 5;
	static Instances getTrainingSet(){
		if(insts == null){
			FastVector fvNominalVal = new FastVector();
			Attribute docLenAtt = new Attribute("QueryLength"); // 0
			fvNominalVal.addElement(docLenAtt);
			Attribute mitraAtt = new Attribute("QueryEntropy"); // 1
			fvNominalVal.addElement(mitraAtt);
			Attribute clarityAtt = new Attribute("QueryClarity"); // 2 
			fvNominalVal.addElement(clarityAtt);
			Attribute clarityAtt1 = new Attribute("QueryClarity1"); // 3 
			fvNominalVal.addElement(clarityAtt1);
			Attribute feedbacklengthAtt = new Attribute("FeedbackLength"); // 4 : number of feedback docs
			fvNominalVal.addElement(feedbacklengthAtt);
			
			// add class category feature
			/*
			 * FastVector fvClassVal = new FastVector(2);
			 * fvClassVal.addElement("positive"); fvClassVal.addElement("negative");
			 * Attribute ClassAttribute = new Attribute("theClass", fvClassVal);
			 * fvNominalVal.addElement(ClassAttribute);
			 */
			Attribute ClassAttribute = new Attribute("theClass"); // 5 
			fvNominalVal.addElement(ClassAttribute);

			int FutureSize = fvNominalVal.size();
			insts = new Instances("Rel", fvNominalVal, FutureSize);
			insts.setClass(ClassAttribute);
//			System.out.println(insts.attribute(0));
//			System.out.println(insts.attribute(5));
		}
		return insts;
	}
	
	private Instance makeInstance(float beta) {
		getTrainingSet();
		Instance example = new Instance(insts.numAttributes());
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
		//////////////////////clarity1
		double clarity1 = 0; 
		float pwq = 1f/ this.termSet.size();
		float totalNumTokens = this.searcher.getNumTokens(field);
		for(String term: this.termSet){
			Item item = getItem(term);
			clarity1 +=  pwq * Idf.log(pwq/ (item.ctf/totalNumTokens ));
		}
		
		//////////////////////clarity2 and QueryEntropy
		String terms[] = expTerms.getTerms();
		double queryentropy = 0;  
		double clarity2 = 0; 
		for(int i=0; i < terms.length; i++){
			String term = terms[i];
			Item item = getItem(term);
			float pwf = expTerms.getFrequency(term)/ totalDocLength;
			queryentropy += -pwf *Idf.log(pwf);
			clarity2 +=  pwf * Idf.log(pwf/ (item.ctf/totalNumTokens ));
		}
		
		example.setValue(0, this.termSet.size());
		example.setValue(1, queryentropy);
		example.setValue(2, clarity1);
		example.setValue(3, clarity2); 
		example.setValue(4, ApplicationSetup.EXPANSION_DOCUMENTS);
		if(trainingTag){
			example.setValue(classid, beta);
//			Attribute ClassAttribute = getTrainingSet().attributeStats(5);
//			example.setValue(ClassAttribute,  beta);
		}else{
//			example.setValue(5, 0);
		}
		example.setDataset(getTrainingSet());
		return example;
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
		if(trainingTag){
			output(topDoc);
			getTRECQerls();
			trecR.evaluate(outfile);
			float optBeta = 0; 
			TopDocCollector besttdc = topDoc;
			double map = trecR.AveragePrecision;
			String para = "";
			
			//find the best interpolation parameter. 
			for(float beta = 0.1f; beta < 1.1 ; beta += 0.1){
				QueryExpansionAdap qea = new QueryExpansionAdap();
				qea.QEModel.ROCCHIO_BETA = beta;
				TopDocCollector tdc = qea.postProcess(query, topDoc, seacher);
				output(tdc);
				trecR.evaluate(outfile);
				para = qea.getInfo();
				if(map <trecR.AveragePrecision){
					optBeta = beta;
					map = trecR.AveragePrecision;
					besttdc = tdc;
				}
			}
			
			System.out.println(this.topicId + ", OptBeta: " + optBeta);
			getClassifier();
			getTrainingSet();
			Instance inst = makeInstance(optBeta);
			addInstance(inst);
			if(topicId.equalsIgnoreCase(lasttopic)){
				build_save();
			}
			besttdc.setInfo(this.getInfo() + topDoc.getInfo()+para);
			besttdc.setInfo_add(QEModel.getInfo());
			return besttdc;
		}else{ // testing
			readClassifier(); 
			Instance insts = makeInstance(0);
			QueryExpansionAdap qea = new QueryExpansionAdap();
			try {
				double cscore[] = classifier.distributionForInstance(insts);
				qea.QEModel.ROCCHIO_BETA = (float) cscore[0];
				System.out.println(this.topicId + ", predict: " + qea.QEModel.ROCCHIO_BETA);
				if(cscore[0] < 0.05){
					topDoc.setInfo(this.getInfo()+ topDoc.getInfo());
					return topDoc;
				}
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(1);
			}
			
			TopDocCollector tdc = qea.postProcess(query, topDoc, seacher);
			tdc.setInfo(this.getInfo()+ tdc.getInfo());
//			tdc.setInfo_add(QEModel.getInfo());
			return tdc;
		}
	}


	private void build_save() {
		Classifier cls = getClassifier();
		try {
			cls.buildClassifier(getTrainingSet());
			AbstractExternalizable.serializeTo(cls, new File(modelout));
		} catch (Exception e) {
			e.printStackTrace();
		}
		//output the training set
		if(true){
			try {
				BufferedWriter bout = StreamGenerator.getBufferFileWriter("train.sample", 2);
				for(int i=0; i <insts.numInstances(); i++){
					bout.write(insts.instance(i).toString() + "\n");
				}
				bout.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private void addInstance(Instance inst) {
		getTrainingSet().add(inst);		
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
		final String methodExpanded = " " + "LabLucene"
				+ ApplicationSetup.EOL;
		StringBuilder sbuffer = new StringBuilder();
		// the results are ordered in descending order
		// with respect to the score.
		for (int i = 0; i < maximum; i++) {
			int docid = topDocs.scoreDocs[i].doc;
			String filename = "" + docid;
			float score = topDocs.scoreDocs[i].score;

			if (filename != null && !filename.equals(filename.trim())) {
				if (logger.isDebugEnabled())
					logger.debug("orginal doc name not trimmed: |"
							+ filename + "|");
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
		return ("PerQuery" + (trainingTag == true? "Train":"Test"));
	}
}
