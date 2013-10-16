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
public class IndexUnitLengthFilter extends TokenFilter {

	/** The maximum number of digits that are allowed in valid terms. */
	protected final int maxNumOfDigitsPerTerm = 4;
	
	/** 
	 * The maximum number of consecutive same letters or digits that are 
	 * allowed in valid terms.
	 */
	protected final int maxNumOfSameConseqLettersPerTerm = 3;
	
	
	
	public IndexUnitLengthFilter(TokenStream input) {
		super(input);
	}
	
	
	static int MAX_TERM_LENGTH = Integer.parseInt(ApplicationSetup.getProperty("Lucene.IndexUnit.Length", "20"));
	
	
	  /**
	   * Returns the next input Token whose term() is not a stop word.
	   */
	  public final Token next(final Token reusableToken) throws IOException {
	    assert reusableToken != null;
LP:	    for (Token nextToken = input.next(reusableToken); nextToken != null; nextToken = input.next(reusableToken)) {
	    	if(nextToken.termLength < MAX_TERM_LENGTH){
	    		String s = nextToken.term();
	    		int length = s.length();
	    		int counter = 0;
	    		int counterdigit = 0;
	    		int ch = -1;
	    		int chNew = -1;
	    		for(int i=0;i<length;i++)
	    		{
	    			chNew = s.charAt(i);
	    			if (chNew >= 48 && chNew <= 57)
	    				counterdigit++;
	    			if (ch == chNew)
	    				counter++;
	    			else
	    				counter = 1;
	    			ch = chNew;
	    			/* if it contains more than 3 consequtive same 
	    			 * letters (or digits), or more than 4 digits, 
	    			 * then discard the term. 
	    			 */
	    			if (counter > maxNumOfSameConseqLettersPerTerm ||
	    				counterdigit > maxNumOfDigitsPerTerm)
	    				continue LP;
	    		}
	    		
	    		
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
		// TODO Auto-generated method stub

	}

}
