package org.apache.lucene.postProcess;

import java.io.IOException;

import gnu.trove.TIntDoubleHashMap;

import org.apache.lucene.search.RBooleanQuery;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.search.TopDocCollector;
import org.dutir.lucene.util.ApplicationSetup;

import zheng.python.scilearn.PredictorClient;

import com.bitmechanic.barrister.HttpTransport;

public class PerQueryAdaptiveFB extends FeatureExtractPP {

	static HttpTransport trans = null;
	static PredictorClient predictor = null;
	static{
		try {
			trans = new HttpTransport("http://127.0.0.1:60000/predictor");
			predictor = new PredictorClient(trans);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
    
	public TopDocCollector postProcess(RBooleanQuery query,
			TopDocCollector topDoc, Searcher seacher) {
		setup(query, topDoc, seacher); // it is necessary

			TIntDoubleHashMap insts = makeInstance(0, Integer.parseInt(query.getTopicId()));
			QueryExpansionAdap qea = new QueryExpansionAdap();
			try {
				double cscore = predictor.predict(toPythonPredictStr(insts)); 
						
				qea.QEModel.ROCCHIO_BETA = (float) cscore;
				System.out.println(this.topicId + ", predict: " + qea.QEModel.ROCCHIO_BETA);
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(1);
			}
			TopDocCollector tdc = qea.postProcess(query, topDoc, seacher);
		
			tdc.setInfo(topDoc.getInfo());
			tdc.setInfo_add(this.getInfo());
			tdc.setInfo_add(QEModel.getInfo());
			return tdc;
	}

	public String getInfo() {
		int n_doc = ApplicationSetup.EXPANSION_DOCUMENTS;
		int n_term = ApplicationSetup.EXPANSION_TERMS;
		return "PerQueryAdaptiveFB" + "_" + "_" + n_doc + "_" + n_term;
	}

}
