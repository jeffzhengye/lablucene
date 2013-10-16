package org.apache.lucene.analysis;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;

import org.apache.log4j.Logger;
import org.apache.lucene.queryParser.AnalyzerManager;
import org.dutir.lucene.TRECQuerying;

public class GeneralAnalyzer extends Analyzer {
	protected static final Logger logger = Logger.getLogger(GeneralAnalyzer.class);
	static String suffix = "org.apache.lucene.analysis";
	Tokenizer tokenizer = null;
	ArrayList<String> flist = new ArrayList<String>(4);

	public void addFilter(String sfilter) {
		if (sfilter.indexOf(".") == -1) {
			flist.add(suffix + "." + sfilter);
		} else {
			flist.add(sfilter);
		}
	}

	public GeneralAnalyzer(Tokenizer tokenizer) {
		this.tokenizer = tokenizer;
	}

	private static Class[] constructor_array_termpipeline = new Class[] { TokenStream.class };
	TokenFilter mfilter = null;
	TokenFilter filterList[] = null;

	public TokenStream tokenStream(String fieldName, Reader reader) {

		try {
			this.tokenizer.reset(reader);
			
			if(filterList ==null){
				filterList = new TokenFilter[flist.size()];
				for (int i = 0; i < flist.size(); i++) {
						Class filterClass = Class.forName(flist.get(i), false, this
								.getClass().getClassLoader());
						mfilter = (TokenFilter) filterClass.getConstructor(
								constructor_array_termpipeline).newInstance(
								new Object[] { this.tokenizer });
						filterList[i] = mfilter;
						if(i ==0){
							mfilter.reset(this.tokenizer);
						}else{
							mfilter.reset(filterList[i-1]);
						}
				}
				// problem
				if(filterList.length >0){
					return filterList[filterList.length -1];
				}
			}else{
				for(int i=0; i < filterList.length; i++){
					if(i ==0 ){
						filterList[i].reset(this.tokenizer);
					}else{
						filterList[i].reset(filterList[i-1]);
					}
				}
				if(filterList.length >0){
					return filterList[filterList.length -1];
				}
			}

			
		} catch (Exception e) {
			e.printStackTrace();
		}
		return tokenizer;
	}

	public static void main(String args[]) throws IOException {

		String s = "#redbossfan (2) why I've didn't mentioned this & that";
		Reader reader = new StringReader(s);
		Analyzer analyzer = AnalyzerManager.getFromPropertyFile();
		TokenStream ts = analyzer.tokenStream("", reader);
		// toknz.setInput();
		Token token = new Token();
		while ((token = ts.next(token)) != null) {
			System.out.println(token);
		}
	}

}
