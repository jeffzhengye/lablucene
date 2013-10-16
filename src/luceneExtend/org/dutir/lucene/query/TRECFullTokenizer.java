/*
 * Terrier - Terabyte Retriever 
 * Webpage: http://ir.dcs.gla.ac.uk/terrier 
 * Contact: terrier{a.}dcs.gla.ac.uk
 * University of Glasgow - Department of Computing Science
 * http://www.gla.ac.uk/
 * 
 * The contents of this file are subject to the Mozilla Public License
 * Version 1.1 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
 * the License for the specific language governing rights and limitations
 * under the License.
 *
 * The Original Code is TRECFullTokenizer.java.
 *
 * The Original Code is Copyright (C) 2004-2009 the University of Glasgow.
 * All Rights Reserved.
 *
 * Contributor(s):
 *   Gianni Amati <gba{a.}fub.it> (original author)
 *   Vassilis Plachouras <vassilis{a.}dcs.gla.ac.uk> 
 */
package org.dutir.lucene.query;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Stack;

import org.apache.log4j.Logger;
import org.dutir.lucene.util.ApplicationSetup;
import org.dutir.lucene.util.TagSet;

/**
 * This class is the tokenizer used for indexing TREC topic files. It can be
 * used for tokenizing other topic file formats, provided that the tags to skip
 * and to process are specified accordingly.
 * <p>
 * <b>NB:</b> This class only accepts A-Z a-z and 0-9 as valid character for query terms.
 * If this restriction is too tight, please use TRECFullUTFTokenizer instead.
 * @see uk.ac.gla.terrier.utility.TagSet
 * @author Gianni Amati, Vassilis Plachouras
 * @version $Revision: 1.33 $
 */
public class TRECFullTokenizer implements Tokenizer {
	Logger logger = Logger.getLogger(this.getClass());
	/** An option to ignore missing closing tags. Used for the query files. */
	protected boolean ignoreMissingClosingTags = false;
	/** last character read */
	public static int lastChar = -1;
	/** A counter for the number of terms. */
	public int number_of_terms = 0;
	/** The end of file from the buffered reader. */
	public boolean EOF;
	/** The end of document. */
	public boolean EOD;
	/** A flag which is set when errors are encountered. */
	public boolean error;
	/** The input reader. */
	public BufferedReader br;
	/** The number of bytes read from the input.*/
	public long counter = 0;
	/**
	 * The stack where the tags are pushed and popped accordingly. 
	 */
	protected static Stack stk = new Stack();
	/** The tag set to use. */
	protected TagSet tagSet;
	
	/** The set of exact tags.*/
	protected TagSet exactTagSet;
	/** The maximum length of a token in the check method. */
	protected final static int tokenMaximumLength = ApplicationSetup.MAX_TERM_LENGTH;
	/** Transform to lowercase or not?. */
	protected final static boolean lowercase = Boolean.parseBoolean(ApplicationSetup.getProperty("lowercase","true"));

	/** Is in tag to process? */
	public boolean inTagToProcess = false;
	/** Is in tag to skip? */
	public boolean inTagToSkip = false;
	/** Is in docno tag? */
	public boolean inDocnoTag = false;
	/**
	 * TConstructs an instance of the TRECFullTokenizer.
	 * The used tags are TagSet.TREC_DOC_TAGS and
	 * TagSet.TREC_EXACT_DOC_TAGS
	 */
	public TRECFullTokenizer() {
		inTagToProcess = false;
		inTagToSkip = false;
		inDocnoTag = false;
		tagSet = new TagSet(TagSet.TREC_DOC_TAGS);
		exactTagSet = new TagSet(TagSet.TREC_EXACT_DOC_TAGS);
		EOD = false;
		EOF = false;
	}
	/**
	 * Constructs an instance of the TRECFullTokenizer, 
	 * given the buffered reader.
	 * The used tags are TagSet.TREC_DOC_TAGS and
	 * TagSet.TREC_EXACT_DOC_TAGS
	 * @param br java.io.BufferedReader the input stream to tokenize
	 */
	public TRECFullTokenizer(BufferedReader br) {
		inTagToProcess = false;
		inTagToSkip = false;
		inDocnoTag = false;
		this.br = br;
		tagSet = new TagSet(TagSet.TREC_DOC_TAGS);
		exactTagSet = new TagSet(TagSet.TREC_EXACT_DOC_TAGS);
		EOD = false;
		EOF = false;
	}
	/**
	 * Constructs an instance of the TRECFullTokenizer with 
	 * non-default tags.
	 * @param _tagSet TagSet the document tags to process.
	 * @param _exactSet TagSet the document tags to process exactly, without
	 *        applying strict checks.
	 */
	public TRECFullTokenizer(TagSet _tagSet, TagSet _exactSet) {
		inTagToProcess = false;
		inTagToSkip = false;
		inDocnoTag = false;
		tagSet = _tagSet;
		exactTagSet = _exactSet;
		EOD = false;
		EOF = false;
	}
	/**
	 * Constructs an instance of the TRECFullTokenizer with 
	 * non-default tags and a given buffered reader.
	 * @param _ts TagSet the document tags to process.
	 * @param _exactSet TagSet the document tags to process exactly, without
	 *        applying strict checks.
	 * @param br java.io.BufferedReader the input to tokenize.
	 */
	public TRECFullTokenizer(TagSet _ts, TagSet _exactSet, BufferedReader br) {
		inTagToProcess = false;
		inTagToSkip = false;
		inDocnoTag = false;
		this.br = br;
		tagSet = _ts;
		exactTagSet = _exactSet;
		EOD = false;
		EOF = false;
	}
	/**
	 * A restricted check function for discarding uncommon, or 'strange' terms.
	 * @param s The term to check.
	 * @return the term if it passed the check, otherwise null.
	 */
	protected String check(String s) {
		//if the s is null
		//or if it is longer than a specified length
		if (s == null)
			return null;
		final int length = s.length();
		if (length == 0 || length > tokenMaximumLength)
			return null;
		if (!stk.empty() && exactTagSet.isTagToProcess((String) stk.peek()))
			return s;
		final StringReader sr = new StringReader(s);
		int counter = 0;
		int counterdigit = 0;
		int ch = -1;
		int chNew = -1;
		try {
			while ((chNew = sr.read()) != -1 && counter <= 2) {
				if (chNew >= 48 && chNew <= 57)
					counterdigit++;
				if (ch == chNew)
					counter++;
				else
					counter = 1;
				ch = chNew;
			}
			sr.close();
		} catch (IOException ioe) {  /* we're reading a string, this should never happen */ }
		//if it contains more than 4 consequtive same letters,
		//or more than 4 digits, then discard the term.
		if (counter > 3 | counterdigit > 4)
			return null;
		return s;
	}
	/**
	 * Closes the buffered reader associated with the tokenizer.
	 */
	public void close() {
		try {
			br.close();
		} catch (IOException ioe) {
			logger.warn("Error while closing the buffered reader in TRECTokenizer", ioe);
		}
	}
	/**
	 * Closes the buffered reader associated with the tokenizer.
	 */
	public void closeBufferedReader() {
		try {
			br.close();
		} catch (IOException ioe) {
			logger.warn("Error while closing the buffered reader in TRECTokenizer", ioe);
		}
	}
	/**
	 * Returns the name of the tag the tokenizer is currently in.
	 * @return the name of the tag the tokenizer is currently in
	 */
	public String currentTag() {
		return (String) stk.peek();
	}
	/**
	 * Indicates whether the tokenizer is in the special document number tag.
	 * @return true if the tokenizer is in the document number tag.
	 */
	public boolean inDocnoTag() {
		return (!stk.isEmpty() && tagSet.isIdTag((String) stk.peek()));
	}
	/**
	 * Returns true if the given tag is to be processed.
	 * @return true if the tag is to be processed, otherwise false.
	 */
	public boolean inTagToProcess() {
		return (!stk.isEmpty() && tagSet.isTagToProcess((String) stk.peek()));
	}
	/**
	 * Returns true if the given tag is to be skipped.
	 * @return true if the tag is to be skipped, otherwise false.
	 */
	public boolean inTagToSkip() {
		return (!stk.isEmpty() && tagSet.isTagToSkip((String) stk.peek()));
	}
	/**
	 * Returns true if the end of document is encountered.
	 * @return true if the end of document is encountered.
	 */
	public boolean isEndOfDocument() {
		return EOD;
	}
	/**
	 * Returns true if the end of file is encountered.
	 * @return true if the end of file is encountered.
	 */
	public boolean isEndOfFile() {
		return EOF;
	}
	/**
	 * Proceed to the next document.
	 */
	public void nextDocument() {
		if (EOD == true) {
			EOD = false;
		}
	}
	/**
	 * nextTermWithNumbers gives the first next string which is not a tag. All
	 * encounterd tags are pushed or popped according they are initial or final
	 */
	public String nextToken() {
		//the string to return as a result at the end of this method.
		String s = null;
		StringWriter sw = null;
		//are we in a body of a tag?
		boolean btag = true;
		//stringBuffer.delete(0, stringBuffer.length());
		int ch = 0;
		//are we reading the document number?
		boolean docnumber = false;
		//while not the end of document, or the end of file, or we are in a tag
		while (btag && ch != -1 & !EOD) {
			sw = new StringWriter();
			boolean tag_f = false;
			boolean tag_i = false;
			error = false;
			try {
				if (lastChar == 60)
					ch = lastChar;
				//Modified by G.Amati 7 june 2002
				//Removed a check: ch!=62
				//If not EOF and ch.isNotALetter and ch.isNotADigit and
				// ch.isNot '<' and ch.isNot '&'
				while (ch != -1 && (ch < 'A' || ch > 'Z') && (ch < 'a' || ch > 'z')
						&& (ch < '0' || ch > '9') && ch != '<' && ch != '&') {
					ch = br.read();
					counter++;
					//if ch is '>' (end of tag), then there is an error.
					if (ch == '>')
						error = true;
				}
				//if a tag begins
				if (ch == '<') {
					ch = br.read();
					counter++;
					//if it is a closing tag, set tag_f true
					if (ch == '/') {
						ch = br.read();
						counter++;
						tag_f = true;
					} else if (ch == '!') { //else if it is a comment, that is
										   // <!
						//read until you encounter a '<', or a '>', or the end
						// of file
						while ((ch = br.read()) != '>' && ch != '<' && ch != -1) {
							counter++;
						}
						counter++;
					} else
						tag_i = true; //otherwise, it is an opening tag
				}
				//Modified by V.Plachouras to take into account the exact tags
				if (ch == '&' && !stk.empty()
						&& !exactTagSet.isTagToProcess((String) stk.peek())) {
					//Modified by G.Amati 7 june 2002 */
					//read until an opening or the end of a tag is encountered,
					// or the end of file, or a space, or a semicolon,
					//which means the end of the escape sequence &xxx;
					while ((ch = br.read()) != '>' && ch != '<' && ch != -1
							&& ch != ' ' && ch != ';') {
						counter++;
					}
					counter++;
				}
				//ignore all the spaces encountered
				while (ch == ' ') {
					ch = br.read();
					counter++;
				}
				//if the body of a tag is encountered
				if ((btag = (tag_f || tag_i))) {
					//read until the end of file, or the start, or the end of a
					// tag, and save the content of the tag
					while (ch != -1 && ch != '<' && ch != '>') {
						sw.write(ch);
						ch = br.read();
						counter++;
					}
				} else { //otherwise, if we are not in the body of a tag
					if (!stk.empty())
						docnumber = inDocnoTag(); // //check if we are in a DOCNO tag
					//if we are in a DOCNO tag
					if (docnumber) {
						//read and save the characters until encountering a '<'
						// or a '>'
						while (ch != -1 && ch != '<' && ch != '>') {
							sw.write(ch);
							ch = br.read();
							counter++;
						}
					}
					if (!stk.empty() && !exactTagSet.isTagToProcess((String) stk.peek())) {
						//read a sequence of letters or digits.
						while (ch != -1
								&& (((ch >= 'A') && (ch <= 'Z'))
								 || ((ch >= 'a') && (ch <= 'z')) 
								 || ((ch >= '0') && (ch <= '9')))) {
							//transforms the uppercase character to lowercase
							if ((ch >= 'A') && (ch <= 'Z') && lowercase)
								ch += 32;
							sw.write(ch);
							ch = br.read();
							counter++;
						}
					} else {
						//read a sequence of letters or digits.
						while (ch != -1
							   && (ch == '&' 
							    || ((ch >= 'A') && (ch <= 'Z'))
								|| ((ch >= 'a') && (ch <= 'z')) 
								|| ((ch >= '0') && (ch <= '9')))) {
							//transforms the uppercase character to lowercase
							if ((ch >= 'A') && (ch <= 'Z') && lowercase)
								ch += 32;
							sw.write(ch);
							ch = br.read();
							counter++;
						}
					}
				}
				lastChar = ch;
				s = sw.toString();
				sw.close();
				if (tag_i) {
					if ((tagSet.isTagToProcess(s) || tagSet.isTagToSkip(s))
							&& !s.equals("")) {
						stk.push(s);
						if (tagSet.isTagToProcess(s)) {
							inTagToProcess = true;
							inTagToSkip = false;
							if (tagSet.isIdTag(s))
								inDocnoTag = true;
							else
								inDocnoTag = false;
						} else {
							inTagToSkip = true;
							inTagToProcess = false;
						}
					}
					return null;
				}
				if (tag_f) {
					if ((tagSet.isTagToProcess(s) || tagSet.isTagToSkip(s))
							&& !s.equals("")) {
						processEndOfTag(s);
						String stackTop = null;
						if (!stk.isEmpty()) {
							stackTop = (String) stk.peek();
							if (tagSet.isTagToProcess(stackTop)) {
								inTagToProcess = true;
								inTagToSkip = false;
								if (tagSet.isIdTag(stackTop))
									inDocnoTag = true;
								else
									inDocnoTag = false;
							} else {
								inTagToProcess = false;
								inTagToSkip = true;
								inDocnoTag = false;
							}
						} else {
							inTagToProcess = false;
							inTagToSkip = false;
							inDocnoTag = false;
						}
					}
					return null;
				}
			} catch (IOException ioe) {
				logger.fatal("Input/Output exception during reading tokens.", ioe);
			}
		}
		if (ch == -1) {
			EOF = true;
			EOD = true;
		}
		//if the token is not a tag or a document number, then check whether it
		// should be removed or not.
		if (!btag & !docnumber)
			return check(s);
		else
			return s;
	}
	/**
	 * The encounterd tag, which must be a final tag is matched with the tag on
	 * the stack. If they are not the same, then the consistency is restored by
	 * popping the tags in the stack, the observed tag included. If the stack
	 * becomes empty after that, then the end of document EOD is set to true.
	 * 
	 * @param tag The closing tag to be tested against the content of the stack.
	 */
	protected void processEndOfTag(String tag) {
		//if there are no tags in the stack, return
		if (stk.empty())
			return;
		//if the given tag is on the top of the stack then pop it
		if (tag.equals((String) stk.peek()))
			stk.pop();
		else { //else report an error, and find the tag.
			if (!ignoreMissingClosingTags) {
				logger.warn("<" + (String) stk.peek()
						+ "> has no closing tag");
				logger.warn("<" + tag + "> not expected");
			}
			int counter = 0;
			int x = stk.search(tag);
			while (!stk.empty() & counter < x) {
				counter++;
				stk.pop();
			}
		}
//		System.out.println(tag);
		//if the stack is empty, this signifies the end of a document.
		if (stk.empty())
			EOD = true;
	}
	/**
	 * Sets the value of the ignoreMissingClosingTags.
	 * @param toIgnore boolean to ignore or not the missing closing tags
	 */
	public void setIgnoreMissingClosingTags(boolean toIgnore) {
		ignoreMissingClosingTags = toIgnore;
	}
	/**
	 * Returns the number of bytes read from the current file.
	 * @return long the byte offset
	 */
	public long getByteOffset() {
		return counter;
	}
	
	/**
	 * Sets the input of the tokenizer.
	 * @param _br BufferedReader the input stream
	 */
	public void setInput(BufferedReader _br) {
		br = _br;
	}
}
