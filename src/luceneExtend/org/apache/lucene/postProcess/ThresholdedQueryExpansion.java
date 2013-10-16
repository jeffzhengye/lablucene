//package uk.ac.gla.terrier.querying;
//
//import gnu.trove.THashMap;
//import gnu.trove.TIntHashSet;
//
//import java.io.BufferedReader;
//import java.io.IOException;
//import java.util.StringTokenizer;
//
//import org.apache.log4j.Logger;
//
//import uk.ac.gla.terrier.utility.ApplicationSetup;
//import uk.ac.gla.terrier.utility.Files;
//import uk.ac.gla.terrier.utility.Rounding;
//
//public class ThresholdedQueryExpansion extends ExplicitQueryExpansion {
//	protected static Logger logger = Logger.getRootLogger();
//	
//	protected double threshold;
//	
//	public ThresholdedQueryExpansion() {
//		super();
//	}
//	protected void loadFeedbackInformation(String filename){
//		try{
//			logger.debug("Loading feedback document "+filename+"...");
//			threshold = Double.parseDouble(ApplicationSetup.getProperty("qe.threshold", ""));
//			queryidRelDocumentMap = new THashMap<String, TIntHashSet>();
//			BufferedReader br = Files.openFileReader(filename);
//			//THashSet<String> queryids = new THashSet<String>();
//			String line = null;
//			while ((line=br.readLine())!=null){
//				line=line.trim();
//				if (line.length()==0)
//					continue;
//				StringTokenizer stk = new StringTokenizer(line);
//				int docid = Integer.parseInt(stk.nextToken());
//				String qid = stk.nextToken();
//				double featureValue = Double.parseDouble(stk.nextToken());
//				int relevance = Integer.parseInt(stk.nextToken());
//				if (featureValue>=threshold){
//					//logger.debug("docid "+docid+" is used for QE. value="+Rounding.toString(featureValue, 4)+
//							//", threhold="+Rounding.toString(threshold, 4)+", relevance="+relevance);
//					if (queryidRelDocumentMap.contains(qid))
//						queryidRelDocumentMap.get(qid).add(docid);
//					else
//						queryidRelDocumentMap.put(qid, new TIntHashSet(docid));
//				}else
//					logger.debug("docid "+docid+" ignored from QE due to low feature value. value="+Rounding.toString(featureValue, 4)+
//							", threhold="+Rounding.toString(threshold, 4)+", relevance="+relevance);
//			}
//			br.close();
//		}catch(IOException ioe){
//			ioe.printStackTrace();
//			System.exit(1);
//		}
//	}
//}
