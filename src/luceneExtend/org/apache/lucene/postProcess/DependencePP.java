///**
// * 
// */
//package org.apache.lucene.postProcess;
//
//import java.io.IOException;
//
//import org.apache.lucene.search.RBooleanQuery;
//import org.apache.lucene.search.Searcher;
//import org.apache.lucene.search.TopDocCollector;
//import org.dutir.lucene.util.ApplicationSetup;
//
///**
// * @author zheng
// *
// */
//public class DependencePP extends QueryExpansion {
//
//	/** weight of unigram model */
//	protected double w_t = Double.parseDouble(ApplicationSetup.getProperty(
//				"proximity.w_t", "1.0d"));
//	/** weight of ordered dependence model */
//	protected double w_o = Double.parseDouble(ApplicationSetup.getProperty(
//				"proximity.w_o", "1.0d"));
//	/** weight of unordered dependence model */
//	protected double w_u = Double.parseDouble(ApplicationSetup.getProperty(
//				"proximity.w_u", "1.0d"));
//	/* (non-Javadoc)
//	 * @see org.apache.lucene.postProcess.PostProcess#getInfo()
//	 */
//	@Override
//	public String getInfo() {
//		return null;
//	}
//
//	/* (non-Javadoc)
//	 * @see org.apache.lucene.postProcess.PostProcess#postProcess(org.apache.lucene.search.RBooleanQuery, org.apache.lucene.search.TopDocCollector, org.apache.lucene.search.Searcher)
//	 */
//	@Override
//	public TopDocCollector postProcess(RBooleanQuery query,
//			TopDocCollector topDoc, Searcher seacher) {
//		setup(query, topDoc, seacher); // it is necessary
//		
//		/////////////////////////////////////////////////////////
//		w_t = Double.parseDouble(ApplicationSetup.getProperty(
//				"proximity.w_t", "1.0d"));
//		w_o = Double.parseDouble(ApplicationSetup.getProperty(
//				"proximity.w_o", "1.0d"));
//		w_u = Double.parseDouble(ApplicationSetup.getProperty(
//				"proximity.w_u", "1.0d"));
//		
//		if (dependency.equals("FD")) {
//			doDependency(index, set, phraseTermWeights, false);
//		} else if (dependency.equals("SD")) {
//			doDependency(index, set, phraseTermWeights, true);
//		} else {
//			System.err.println("WARNING: proximity.dependency.type not set. Set it to either FD or SD");
//			return false;
//		}
//		
//		/////////////////////////////////////////////////////////
//		int num = Integer.parseInt(ApplicationSetup.getProperty(
//				"TRECQuerying.endFeedback", "1000"));
//		TopDocCollector cls = new TopDocCollector(num);
//		cls.setInfo(topDoc.getInfo());
//		cls.setInfo_add(this.getInfo());
//		cls.setInfo_add(QEModel.getInfo());
//		try {
//			this.searcher.search(fquery, cls);
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
//		return cls;
//	}
//
//	/**
//	 * @param args
//	 */
//	public static void main(String[] args) {
//		// TODO Auto-generated method stub
//
//	}
//
//}
