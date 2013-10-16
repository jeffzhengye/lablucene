/**
 * 
 */
package org.dutir.lucene.parser;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Stack;

import org.apache.log4j.Logger;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.Field.TermVector;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.store.BufferedIndexInput;

import org.dutir.lucene.util.ApplicationSetup;
import org.dutir.lucene.util.TagSet;

/**
 * @author Yezheng
 * 
 */
public class GeneralDocParser implements DocumentParser {

	/** The logger used */
	protected static final Logger logger = Logger.getLogger(GeneralDocParser.class);
	BufferedInputStream bis = null;
	File file = null;
	String encoding = "utf8";
	IndexWriter writer = null;
	static boolean isMergeInto1Field = Boolean.parseBoolean(ApplicationSetup.getProperty(
			"Lucene.GeneralDocParser.isMergeInto1Field", "false"));
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see org.dutir.lucene.parser.DocumentParser#nextDoc()
	 */
	public Document nextDoc() throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.dutir.lucene.parser.DocumentParser#setParserFile(java.lang.String)
	 */
	public void setParserFile(String path) {
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.dutir.lucene.parser.DocumentParser#setParserFile(java.io.File)
	 */
	public void setParserFile(File file) {
		// TODO Auto-generated method stub

	}

	public void indexAll() {
		try {
			logger.info("adding doc: " + this.file.getAbsolutePath());
			FieldTokenizer ftokenizer = new FieldTokenizer(new BufferedReader(
					new InputStreamReader(this.bis, this.encoding)));
			while (!ftokenizer.isEndOfFile()) {
				Document doc = ftokenizer.nextDocument();
				
				if (doc != null) {
//					System.out.println(doc);
					this.writer.addDocument(doc);
				} else {
					// System.out.println(doc);
				}
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void setup(InputStream is, File file, String encoding) {
		// TODO Auto-generated method stub
		bis = new BufferedInputStream(is, Cache);
		this.file = file;
		this.encoding = encoding;
	}

	static class FieldTokenizer {
		/** An option to ignore missing closing tags. Used for the query files. */
		protected boolean ignoreMissingClosingTags = false;
		/** last character read */
		public static int lastChar = -1;
		/** The end of file from the buffered reader. */
		public boolean EOF;
		/** The end of document. */
		public boolean EOD;
		/** A flag which is set when errors are encountered. */
		public boolean error;
		/** The input reader. */
		public BufferedReader br;
		/** The number of bytes read from the input. */
		public long counter = 0;
		/**
		 * The stack where the tags are pushed and popped accordingly.
		 */
		protected static Stack stk = new Stack();
		/** The tag set to use. */
		protected TagSet tagSet;

		/** The set of exact tags. */
		protected TagSet exactTagSet;
		/** The maximum length of a token in the check method. */
		protected final static int tokenMaximumLength = ApplicationSetup.MAX_TERM_LENGTH;
		/** Transform to lowercase or not?. */

		/** Is in tag to process? */
		public boolean inTagToProcess = false;
		/** Is in tag to skip? */
		public boolean inTagToSkip = false;
		/** Is in docno tag? */
		public boolean inDocnoTag = false;

		/**
		 * TConstructs an instance of the TRECFullTokenizer. The used tags are
		 * TagSet.TREC_DOC_TAGS and TagSet.TREC_EXACT_DOC_TAGS
		 */
		public FieldTokenizer() {
			inTagToProcess = false;
			inTagToSkip = false;
			inDocnoTag = false;
			tagSet = new TagSet(TagSet.TREC_DOC_TAGS);
			exactTagSet = new TagSet(TagSet.TREC_EXACT_DOC_TAGS);
			EOD = false;
			EOF = false;
		}

		/**
		 * Constructs an instance of the TRECFullTokenizer, given the buffered
		 * reader. The used tags are TagSet.TREC_DOC_TAGS and
		 * TagSet.TREC_EXACT_DOC_TAGS
		 * 
		 * @param br
		 *            java.io.BufferedReader the input stream to tokenize
		 */
		public FieldTokenizer(BufferedReader br) {
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
		 * Constructs an instance of the TRECFullTokenizer with non-default
		 * tags.
		 * 
		 * @param _tagSet
		 *            TagSet the document tags to process.
		 * @param _exactSet
		 *            TagSet the document tags to process exactly, without
		 *            applying strict checks.
		 */
		public FieldTokenizer(TagSet _tagSet, TagSet _exactSet) {
			inTagToProcess = false;
			inTagToSkip = false;
			inDocnoTag = false;
			tagSet = _tagSet;
			exactTagSet = _exactSet;
			EOD = false;
			EOF = false;
		}

		/**
		 * Constructs an instance of the TRECFullTokenizer with non-default tags
		 * and a given buffered reader.
		 * 
		 * @param _ts
		 *            TagSet the document tags to process.
		 * @param _exactSet
		 *            TagSet the document tags to process exactly, without
		 *            applying strict checks.
		 * @param br
		 *            java.io.BufferedReader the input to tokenize.
		 */
		public FieldTokenizer(TagSet _ts, TagSet _exactSet, BufferedReader br) {
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
		 * Closes the buffered reader associated with the tokenizer.
		 */
		public void close() {
			try {
				br.close();
			} catch (IOException ioe) {
				// logger.warn("Error while closing the buffered reader in TRECTokenizer",
				// ioe);
			}
		}

		/**
		 * Closes the buffered reader associated with the tokenizer.
		 */
		public void closeBufferedReader() {
			try {
				br.close();
			} catch (IOException ioe) {
				// logger.warn("Error while closing the buffered reader in TRECTokenizer",
				// ioe);
			}
		}

		/**
		 * Returns the name of the tag the tokenizer is currently in.
		 * 
		 * @return the name of the tag the tokenizer is currently in
		 */
		public String currentTag() {
			return (String) stk.peek();
		}

		/**
		 * Indicates whether the tokenizer is in the special document number
		 * tag.
		 * 
		 * @return true if the tokenizer is in the document number tag.
		 */
		public boolean inDocnoTag() {
			return (!stk.isEmpty() && tagSet.isIdTag((String) stk.peek()));
		}

		/**
		 * Returns true if the given tag is to be processed.
		 * 
		 * @return true if the tag is to be processed, otherwise false.
		 */
		public boolean inTagToProcess() {
			return (!stk.isEmpty() && tagSet
					.isTagToProcess((String) stk.peek()));
		}

		/**
		 * Returns true if the given tag is to be skipped.
		 * 
		 * @return true if the tag is to be skipped, otherwise false.
		 */
		public boolean inTagToSkip() {
			return (!stk.isEmpty() && tagSet.isTagToSkip((String) stk.peek()));
		}

		/**
		 * Returns true if the end of document is encountered.
		 * 
		 * @return true if the end of document is encountered.
		 */
		public boolean isEndOfDocument() {
			return EOD;
		}

		/**
		 * Returns true if the end of file is encountered.
		 * 
		 * @return true if the end of file is encountered.
		 */
		public boolean isEndOfFile() {
			return EOF;
		}

		/**
		 * nextTermWithNumbers gives the first next string which is not a tag.
		 * All encounterd tags are pushed or popped according they are initial
		 * or final
		 */
		public Document nextDocument() {
			// the string to return as a result at the end of this method.
			String s = null;
			StringWriter sw = null;
			StringWriter fw = null;
			// are we in a body of a tag?
			boolean btag = true;
			// stringBuffer.delete(0, stringBuffer.length());
			int ch = 0;
			// are we reading the document number?
			boolean docnumber = false;
			// while not the end of document, or the end of file, or we are in a
			// tag
			Document doc = new Document();
			EOD = false;
			fw = new StringWriter();
			stk.clear();
			while (ch != -1 && !EOD) {
				// System.out.print(ch);
				boolean tag_f = false; // finish tag
				boolean tag_i = false; // begin tag
				error = false;
				sw = new StringWriter();
				try {
					if (lastChar == 60)
						ch = lastChar;
					// Modified by G.Amati 7 june 2002
					// Removed a check: ch!=62
					// If not EOF and ch.isNotALetter and ch.isNotADigit and
					// ch.isNot '<' and ch.isNot '&'
					while (ch != -1 && !Character.isLetterOrDigit(ch)
							&& ch != '<' && ch != '&') {
						ch = br.read();
						counter++;
						// if ch is '>' (end of tag), then there is an error.
						if (ch == '>')
							error = true;
					}
					// if a tag begins
					if (ch == '<') {
						ch = br.read();
						counter++;
						// if it is a closing tag, set tag_f true
						if (ch == '/') {
							ch = br.read();
							counter++;
							tag_f = true;
						} else if (ch == '!') { // else if it is a comment, that
							// is
							// <!
							// read until you encounter a '<', or a '>', or the
							// end
							// of file
							while ((ch = br.read()) != '>' && ch != '<'
									&& ch != -1) {
								counter++;
							}
							counter++;
						} else
							tag_i = true; // otherwise, it is an opening tag
					}
					// Modified by V.Plachouras to take into account the exact
					// tags
					if (ch == '&' && !stk.empty()
							&& !exactTagSet.isTagToProcess((String) stk.peek())) {
						// Modified by G.Amati 7 june 2002 */
						// read until an opening or the end of a tag is
						// encountered,
						// or the end of file, or a space, or a semicolon,
						// which means the end of the escape sequence &xxx;
						while ((ch = br.read()) != '>' && ch != '<' && ch != -1
								&& ch != ' ' && ch != ';') {
							counter++;
						}
						counter++;
					}
					// ignore all the spaces encountered
					while (ch == ' ') {
						ch = br.read();
						counter++;
					}
					// if the body of a tag is encountered
					if ((btag = (tag_f || tag_i))) {
						// read until the end of file, or the start, or the end
						// of a
						// tag, and save the content of the tag
						while (ch != -1 && ch != '<' && ch != '>') {
							sw.write(ch);
							ch = br.read();
							counter++;
						}
					} else { // otherwise, if we are not in the body of a tag
//						if (inTagToProcess) 
						if (inDocnoTag || (!stk.empty() && tagSet.isTagToProcess((String) stk.peek())) ) 
						{
							while (ch != -1 && ch != '<' && ch != '>') {
								fw.write(ch);
								ch = br.read();
								counter++;
							}
						} else { // in skip tag
							// skip all text
							while (ch != -1 && ch != '<' && ch != '>') {
								ch = br.read();
								counter++;
							}
						}
					}
					lastChar = ch;

					if (tag_i) {
						s = sw.toString();
						sw.close();
//						System.out.println(s);
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
						if(tagSet.isDocTag(s)){
							inDocnoTag = true;
						}
					}
					
					if (tag_f) {
						s = sw.toString();
						sw.close();
//						System.out.println("end:" +s);
						
						if (tagSet.isDocTag(s)) {
							EOD = true;
							processEndOfTag(s);
							if(isMergeInto1Field){
								String contString = fw.toString();
								fw.close();
								fw = new StringWriter();
								Store store = Store.NO;
								Index index = Index.NO;
								
								if (tagSet.isAnalyze(s)) {
									index = Index.ANALYZED;
								} else if (tagSet.isIndex(s)) {
									index = Index.NOT_ANALYZED;
								}
								if (tagSet.isCompress(s)) {
									store = Store.COMPRESS;
								} else if (tagSet.isStore(s)) {
									store = Store.YES;
								}
								Field idField = new Field(s, contString, store,
										index, TermVector.WITH_POSITIONS);
								doc.add(idField);
							}
						}
						if ((tagSet.isTagToProcess(s) || tagSet.isTagToSkip(s))
								&& !s.equals("")) {
							processEndOfTag(s);
							
//							if (tagSet.isDocTag(s)) {
//								EOD = true;
//								if(isMergeInto1Field){
//									String contString = fw.toString();
//									fw.close();
//									fw = new StringWriter();
//									Store store = Store.NO;
//									Index index = Index.NO;
//									
//									if (tagSet.isAnalyze(s)) {
//										index = Index.ANALYZED;
//									} else if (tagSet.isIndex(s)) {
//										index = Index.NOT_ANALYZED;
//									}
//									if (tagSet.isCompress(s)) {
//										store = Store.COMPRESS;
//									} else if (tagSet.isStore(s)) {
//										store = Store.YES;
//									}
//									Field idField = new Field(s, contString, store,
//											index, TermVector.YES);
//									doc.add(idField);
//								}
//							} else 
							if (tagSet.isTagToProcess(s)) {
								if(!isMergeInto1Field){
									String contString = fw.toString();
									fw.close();
									fw = new StringWriter();
									Store store = Store.NO;
									Index index = Index.NO;
									
									if (tagSet.isAnalyze(s)) {
										index = Index.ANALYZED;
									} else if (tagSet.isIndex(s)) {
										index = Index.NOT_ANALYZED;
									}
									if (tagSet.isCompress(s)) {
										store = Store.COMPRESS;
									} else if (tagSet.isStore(s)) {
										store = Store.YES;
									}
									Field idField = new Field(s, contString, store,
											index, TermVector.WITH_POSITIONS);
									doc.add(idField);
								} else{
									fw.write(' ');
								}
							}

							String stackTop = null;
							if (!stk.isEmpty()) {
								stackTop = (String) stk.peek();
								if (tagSet.isTagToProcess(stackTop)) {
									inTagToProcess = true;
									inTagToSkip = false;
									if (tagSet.isIdTag(stackTop)) {
										inDocnoTag = true;

									} else {
										inDocnoTag = false;
									}
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
						}else if (tagSet.isIdTag(s)) {
							String contString = fw.toString().trim();
							fw.close();
							fw = new StringWriter();
							Field idField = new Field(s, contString,
									Store.YES, Index.NOT_ANALYZED_NO_NORMS);
							doc.add(idField);
							inDocnoTag = false;
						} else {
							fw.write(' ');
						}
					}
				} catch (IOException ioe) {
					// logger.fatal("Input/Output exception during reading tokens.",
					// ioe);
					EOD= true;
					EOF = true;
					logger.fatal("Input/Output exception in parsering this file ");
					ioe.printStackTrace();
					return null;
				}
			}
			
			if (ch == -1) {
				EOF = true;
				EOD = true;
				return null;
			}
			if(!stk.empty()){
				logger.info("The following doc misformats:");
				logger.info(doc);
			}
			return doc;
		}

		
		/**
		 * The encounterd tag, which must be a final tag is matched with the tag
		 * on the stack. If they are not the same, then the consistency is
		 * restored by popping the tags in the stack, the observed tag included.
		 * If the stack becomes empty after that, then the end of document EOD
		 * is set to true.
		 * 
		 * @param tag
		 *            The closing tag to be tested against the content of the
		 *            stack.
		 */
		protected void processEndOfTag(String tag) {
			// if there are no tags in the stack, return
			if (stk.empty())
				return;
			// if the given tag is on the top of the stack then pop it
			if (tag.equals((String) stk.peek()))
				stk.pop();
			else { // else report an error, and find the tag.
				if (!ignoreMissingClosingTags) {
					// logger.warn("<" + (String) stk.peek()
					// + "> has no closing tag");
					// logger.warn("<" + tag + "> not expected");
				}
				int counter = 0;
				int x = stk.search(tag);
				while (!stk.empty() & counter < x) {
					counter++;
					stk.pop();
				}
			}
			// if the stack is empty, this signifies the end of a document.
//			if (stk.empty())
//				EOD = true;
		}

		/**
		 * Sets the value of the ignoreMissingClosingTags.
		 * 
		 * @param toIgnore
		 *            boolean to ignore or not the missing closing tags
		 */
		public void setIgnoreMissingClosingTags(boolean toIgnore) {
			ignoreMissingClosingTags = toIgnore;
		}

		/**
		 * Returns the number of bytes read from the current file.
		 * 
		 * @return long the byte offset
		 */
		public long getByteOffset() {
			return counter;
		}

		/**
		 * Sets the input of the tokenizer.
		 * 
		 * @param _br
		 *            BufferedReader the input stream
		 */
		public void setInput(BufferedReader _br) {
			br = _br;
		}
	}

	public void setIndexWriter(IndexWriter writer) {
		this.writer = writer;
	}

	public static void main(String args[]) throws FileNotFoundException {
		 int a = '<', b = '>';
		 System.out.println(a + ", " + b);
		
		GeneralDocParser parser = new GeneralDocParser();
		String s = "/media/disk/IR/Corpus/TREC/Trec5/TrecFormat/x9501001_hz.gz.txt";
		s= "B01";
		File file = new File(s);
		FileInputStream is = new FileInputStream(file);
		parser.setup(is, file, "8859_1");
		parser.indexAll();
	}

}
