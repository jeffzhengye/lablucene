/**
 * 
 */
package org.apache.lucene.queryParser;

import java.io.IOException;
import java.io.StringReader;

import org.apache.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.GeneralAnalyzer;
import org.apache.lucene.analysis.Token;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.dutir.lucene.ISManager;
import org.dutir.lucene.util.ApplicationSetup;

/**
 * @author yezheng
 * 
 */
public class AnalyzerManager {
	static Logger logger  = Logger.getLogger(AnalyzerManager.class);
	static String analyzerName = ApplicationSetup.getProperty("Lucene.Tokenizer",
			"org.apache.lucene.analysis.GeneralTokenizer");
	static String filterstr = ApplicationSetup.getProperty("Lucene.Filters", "");

	//return a general analyzer from property file
	static GeneralAnalyzer analyzer = null;
	public static Analyzer getFromPropertyFile() {
		try {
			if (analyzer == null) {
				Tokenizer tokenizer = (Tokenizer) Class.forName(analyzerName)
						.newInstance();
				analyzer = new GeneralAnalyzer(tokenizer);
				String filters[] = filterstr.split("\\s*,\\s*");
				for (int i = 0; i < filters.length; i++) {
					if (filters[i].length() > 1) {
						analyzer.addFilter(filters[i]);
						if(logger.isInfoEnabled()) logger.info("adding filter: " + filters[i]);
					}
				}
			}
			return analyzer;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception {
		Analyzer ana = getFromPropertyFile();
		String text = "R&D Europe's zwischenbilanz 0038z wis GOOG goods 0-0.005Mg,0,1 adb-cde silicon-bonded MK-0431 02Cu5 3-hydroxy-w-cyclohexylalkanoic butan-2-amine";
//		text = "s12s china is gooddestmansileddddddd eakin@ogp.noaa.gov 0000";
		StringReader  reader = new StringReader(text);
		TokenStream ts = ana.tokenStream("", reader);
		Token tk = new Token();
		while( (tk = ts.next(tk)) != null){
			System.out.println(tk);
		}
	}

}
