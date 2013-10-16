package org.apache.lucene.search;

import org.dutir.lucene.util.ApplicationSetup;

/**
 * @author yezheng
 * 
 */
public class RTermWeightManager {
	private static String suffex = "org.apache.lucene.search.";
	static String weightModel = ApplicationSetup.getProperty(
			"Lucene.RTermWeightManager.TermWeight", "RTermWeight");
	
	public static RTermWeight getFromPropertyFile() {
		try {
			return (RTermWeight) Class.forName(suffex + weightModel)
					.newInstance();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
