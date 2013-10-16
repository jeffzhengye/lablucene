/*
 * Terrier - Terabyte Retriever
 * Webpage: http://terrier.org
 * Contact: terrier{a.}dcs.gla.ac.uk
 * University of Glasgow - School of Computing Science
 * http://www.ac.gla.uk
 *
 * The contents of this file are subject to the Mozilla Public License
 * Version 1.1 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
 * the LiCense for the specific language governing rights and limitations
 * under the License.
 *
 * The Original Code is TRECQuerying.java.
 *
 * The Original Code is Copyright (C) 2004-2011 the University of Glasgow.
 * All Rights Reserved.
 *
 * Contributor(s):
 *   Gianni Amati <gba{a.}fub.it> (original author)
 *   Vassilis Plachouras <vassilis{a.}dcs.gla.ac.uk>
 *   Ben He <ben{a.}dcs.gla.ac.uk>
 *   Craig Macdonald <craigm{a.}dcs.gla.ac.uk>
 */

/*
* This file is probably based on a class with the same name from Terrier, 
* so we keep the copyright head here. If you have any question, please notify me first.
*  Thanks. 
*/
/**
 * 
 */
package org.apache.lucene.analysis;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.standard.StandardTokenizer;

/**
 * @author yezheng
 *
 */

public class GeneralTokenizer extends Tokenizer {

	
	private final GeneralTokenizerImpl scanner;
	
	public GeneralTokenizer(Reader input) {
		this.scanner = new GeneralTokenizerImpl(input);
		this.input = input;
		// TODO Auto-generated constructor stub
	}
	
	public GeneralTokenizer(Reader input, boolean replaceInvalidAcronym) {
		this.replaceInvalidAcronym = replaceInvalidAcronym;
		this.input = input;
		this.scanner = new GeneralTokenizerImpl(input);
	}
	
	public GeneralTokenizer() {
		this.scanner = new GeneralTokenizerImpl(input);
	}



	public static final int ALPHANUM = 0;
	public static final int APOSTROPHE = 1;
	public static final int ACRONYM = 2;
	public static final int COMPANY = 3;
	public static final int EMAIL = 4;
	public static final int HOST = 5;
	public static final int NUM = 6;
	public static final int CJ = 7;

	/**
	 * @deprecated this solves a bug where HOSTs that end with '.' are
	 *             identified as ACRONYMs. It is deprecated and will be removed
	 *             in the next release.
	 */
	public static final int ACRONYM_DEP = 8;

	/** String token types that correspond to token type int constants */
	public static final String[] TOKEN_TYPES = new String[] { "<ALPHANUM>",
			"<APOSTROPHE>", "<ACRONYM>", "<COMPANY>", "<EMAIL>", "<HOST>",
			"<NUM>", "<CJ>", "<ACRONYM_DEP>" };

	/** @deprecated Please use {@link #TOKEN_TYPES} instead */
	public static final String[] tokenImage = TOKEN_TYPES;

	/**
	 * Specifies whether deprecated acronyms should be replaced with HOST type.
	 * This is false by default to support backward compatibility.
	 *<p/>
	 * See http://issues.apache.org/jira/browse/LUCENE-1068
	 * 
	 * @deprecated this should be removed in the next release (3.0).
	 */
	private boolean replaceInvalidAcronym = false;

	void setInput(Reader reader) {
		this.input = reader;
	}

	private int maxTokenLength = StandardAnalyzer.DEFAULT_MAX_TOKEN_LENGTH;

	/**
	 * Set the max allowed token length. Any token longer than this is skipped.
	 */
	public void setMaxTokenLength(int length) {
		this.maxTokenLength = length;
	}

	/** @see #setMaxTokenLength */
	public int getMaxTokenLength() {
		return maxTokenLength;
	}



	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.lucene.analysis.TokenStream#next()
	 */
	public Token next(final Token reusableToken) throws IOException {
		assert reusableToken != null;
		int posIncr = 1;

		while (true) {
			int tokenType = scanner.getNextToken();

			if (tokenType == GeneralTokenizerImpl.YYEOF) {
				return null;
			}

			if (scanner.yylength() <= maxTokenLength) {
				reusableToken.clear();
				reusableToken.setPositionIncrement(posIncr);
				scanner.getText(reusableToken);
				final int start = scanner.yychar();
				reusableToken.setStartOffset(start);
				reusableToken.setEndOffset(start + reusableToken.termLength());
				// This 'if' should be removed in the next release. For now, it
				// converts
				// invalid acronyms to HOST. When removed, only the 'else' part
				// should
				// remain.
				if (tokenType == GeneralTokenizerImpl.ACRONYM_DEP) {
					if (replaceInvalidAcronym) {
						reusableToken
								.setType(GeneralTokenizerImpl.TOKEN_TYPES[GeneralTokenizerImpl.HOST]);
						reusableToken
								.setTermLength(reusableToken.termLength() - 1); // remove
																				// extra
																				// '.'
					} else {
						reusableToken
								.setType(GeneralTokenizerImpl.TOKEN_TYPES[GeneralTokenizerImpl.ACRONYM]);
					}
				} else {
					reusableToken
							.setType(GeneralTokenizerImpl.TOKEN_TYPES[tokenType]);
				}
				return reusableToken;
			} else
				// When we skip a too-long term, we still increment the
				// position increment
				posIncr++;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.lucene.analysis.TokenStream#reset()
	 */
	public void reset() throws IOException {
		super.reset();
		scanner.yyreset(input);
	}

	public void reset(Reader reader) throws IOException {
		input = reader;
		reset();
	}

	/**
	 * Prior to https://issues.apache.org/jira/browse/LUCENE-1068,
	 * StandardTokenizer mischaracterized as acronyms tokens like www.abc.com
	 * when they should have been labeled as hosts instead.
	 * 
	 * @return true if StandardTokenizer now returns these tokens as Hosts,
	 *         otherwise false
	 * 
	 * @deprecated Remove in 3.X and make true the only valid value
	 */
	public boolean isReplaceInvalidAcronym() {
		return replaceInvalidAcronym;
	}

	/**
	 * 
	 * @param replaceInvalidAcronym
	 *            Set to true to replace mischaracterized acronyms as HOST.
	 * @deprecated Remove in 3.X and make true the only valid value
	 * 
	 *             See https://issues.apache.org/jira/browse/LUCENE-1068
	 */
	public void setReplaceInvalidAcronym(boolean replaceInvalidAcronym) {
		this.replaceInvalidAcronym = replaceInvalidAcronym;
	}
	
	
	public static void main(String args[]) throws IOException {
		String s = "美国 日本 中国，y6o6u are not right zimmerman&m zimbabwe' zimmer-25716 0.521 0.05.";
		Reader reader = new StringReader(s);
		GeneralTokenizer toknz = new GeneralTokenizer(reader);
		
//		toknz.setInput();
		Token token = new Token();
		while ((token = toknz.next(token)) != null) {
			System.out.println(token);
		}
	}

}
