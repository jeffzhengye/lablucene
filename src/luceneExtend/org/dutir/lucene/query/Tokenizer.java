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
 * The Original Code is Tokenizer.java.
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
/**
 * The specification of the interface implemented
 * by tokeniser classes.
 * @author Gianni Amati, Vassilis Plachouras
 * @version $Revision: 1.15 $
 */
public interface Tokenizer {
	/**
	 * Returns the identifier of the tag the tokenizer is into.
	 * @return the name of the tag the tokenizer is processing
	 */	
	public String currentTag();
	/**
	 * Returns the next token from the input stream used.
	 * @return the next token, or null if the end of file is encountered.*/
	public String nextToken();
	/**
	 * Indicates whether we are in a special document number tag.
	 * @return true if the tokenizer is in a document number tag.
	 */
	public boolean inDocnoTag();
	/**
	 * Indicates whether we are in a tag to process.
	 * @return true if we are in a tag to process.
	 */
	public boolean inTagToProcess();
	/** 
	 * Indicates whether we are in a tag to skip
	 * @return true if we are in a tag to skip
	 */
	public boolean inTagToSkip();
	
	/**
	 * Returns true if the end of document is encountered.
	 * @return true if the end of document is encountered.
	 */
	public boolean isEndOfDocument();
	/**
	 * Returns true if the end of file is encountered.
	 * @return true if the end of document is encountered.
	 */
	public boolean isEndOfFile();
	
	/**
	 * Proceed to process the next document.
	 */
	public void nextDocument();
	
	/**
	 * Returns the byte offset in the current indexed file.
	 */
	public long getByteOffset();
	
	/**
	 * Sets the input of the tokenizer
	 * @param input BufferedReader the input stream to tokenize
	 */
	public void setInput(BufferedReader input);
}
