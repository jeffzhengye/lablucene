/**
 * 
 */
package org.apache.lucene.analysis;

import java.io.IOException;

import org.dutir.lucene.util.ApplicationSetup;

/**
 * @author yezheng
 *
 */
public class NumberFilter extends TokenFilter {

	static boolean CombineUnitTag = Boolean.parseBoolean(ApplicationSetup.getProperty("NumberFilter.CombineUnitTag", "true")); 
	
	public NumberFilter(TokenStream input) {
		super(input);
	}
	
	  /**
	   * Returns the next input Token whose term() is not a stop word.
	   */
	  public final Token next(final Token reusableToken) throws IOException {
	    assert reusableToken != null;
	    for (Token nextToken = input.next(reusableToken); nextToken != null; nextToken = input.next(reusableToken)) {
	    	
	    		String s = nextToken.term();
	    		if(s.matches("[09]+")){
	    			continue;
	    		}else if(s.length() ==3 && s.equalsIgnoreCase("ps2")){
	    			return nextToken;
	    		}
	    		else if(CombineUnitTag && !s.matches("[a-zA-Z]+")){
	    			continue;
	    		}
	    		else{
	    			return nextToken;
	    		}
	    }
	    // reached EOS -- return null
	    return null;
	  }
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		String s = "ma";
		if(s.matches("[a-zA-Z]+")){
			System.out.println("number: " + s);
		}else{
			System.out.println("not number: " + s);
		}
	}

}
