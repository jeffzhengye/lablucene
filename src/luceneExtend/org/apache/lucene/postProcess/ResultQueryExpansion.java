///**
// * 
// */
//package uk.ac.gla.terrier.querying;
//
//import gnu.trove.THashMap;
//import gnu.trove.TIntHashSet;
//
//import java.util.Arrays;
//
//import org.apache.log4j.Logger;
//
//import uk.ac.gla.terrier.evaluation.TRECResultsInMemory;
//import uk.ac.gla.terrier.utility.ApplicationSetup;
//
///**
// * @author ben
// *
// */
//public class ResultQueryExpansion extends ExplicitQueryExpansion {
//	protected static Logger logger = Logger.getRootLogger();
//	/**
//	 * 
//	 */
//	public ResultQueryExpansion() {
//		super();
//	}
//	/**
//	 * The docnos in the result file need to be converted to docids.
//	 */
//	protected void loadFeedbackInformation(String filename){
//		queryidRelDocumentMap = new THashMap<String, TIntHashSet>();
//		TRECResultsInMemory results = new TRECResultsInMemory(filename);
//		String[] queryids = results.getQueryids();
//		for (int i=0; i<queryids.length; i++){
//			String[] docidStrings = Arrays.copyOfRange(results.getDocnoSet(queryids[i]), 0, ApplicationSetup.EXPANSION_DOCUMENTS);
//			int[] docids = new int[docidStrings.length];
//			for (int j=0; j<docids.length; j++)
//				docids[j] = Integer.parseInt(docidStrings[j]);
//			queryidRelDocumentMap.put(queryids[i], new TIntHashSet(docids));
//		}
//		results = null;
//	}
//
//}
