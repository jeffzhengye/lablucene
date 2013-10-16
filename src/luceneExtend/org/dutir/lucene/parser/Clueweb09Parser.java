/**
 * 
 */
package org.dutir.lucene.parser;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;

import org.apache.log4j.Logger;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.Field.TermVector;
import org.apache.lucene.index.IndexWriter;
import org.dutir.util.dom.DomNodeUtils;

/**
 * @author yezheng
 * 
 */
public class Clueweb09Parser implements DocumentParser {
	static Logger logger = Logger.getLogger(Clueweb09Parser.class);
	BufferedInputStream bis = null;
	File file = null;
	private String encoding = "utf8";
	BufferedReader br = null;
	IndexWriter writer = null;

	public void indexAll() {
		logger.info("adding doc: " + this.file.getAbsolutePath());
		this.preid = null;
		try {
			Document doc = nextDoc();
			while (doc != null) {
				this.writer.addDocument(doc);
				doc = nextDoc();
				// System.out.println(doc);
			}
			this.br.close();
			this.bis.close();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private static org.dutir.util.dom.HtmlFileToDocument hf2doc = new org.dutir.util.dom.HtmlFileToDocument();
	static String sWARCb = "WARC/0.18";
	static String sWARCType = "WARC-Type:";
	static String sWARCTRECID = "WARC-TREC-ID:";
	static String sWARCTargetURI = "WARC-Target-URI:";
	static String sWARCContentType = "Content-Type:";
	static String sWARCContentLength = "Content-Length:";
	static String conformsTo = "conformsTo";
	static boolean conformsTotag = false;
	// static String sWARCb = "WARC/0.18";
	// static String sWARCb = "WARC/0.18";

	int count = 0;

	String preid = null;

	static void check(String preid, String curid) {
		if (preid == null) {
			preid = curid;
			return;
		} else {
			int id1 = getIntId(preid);
			int id2 = getIntId(curid);
			if ( (id2 - id1) != 1) {
				logger.warn("missed docs between " + preid + " and " + curid);
			}
		}
		preid = curid;
	}

	final static int getIntId(String sid) {
		if (sid.length() != "clueweb09-en0008-00-25908".length()) {
			logger.warn("|" + sid + "| is invalid");
		}
		int pos = sid.lastIndexOf("-");
		if (pos != -1) {
			return Integer.parseInt(sid.substring(pos + 1));
		}
		return -1;
	}

	public Document nextDoc() throws Exception {
		// WARC/0.18 WARC-Type: //WARC-TREC-ID: //WARC-Target-URI:
		// //Content-Type: //Content-Length:
		boolean WARCbtag = false, sWARCTypetag = false, WARCTRECIDtag = false, WARCTargetURItag = false, ContentTypetag = false, ContentLengthtag = false;
		String line = null;
		String id = null;
		String url = null;
		String title = null;
		String content = null;
		String contentType = null;
		// String WARC_Type = null;
		int contentCount = 0;
		StringBuilder buf = new StringBuilder();
		int contentLen = 0;
		while ((line = this.br.readLine()) != null) {
			if (!conformsTotag) {
				if (line.startsWith(conformsTo)) {
					conformsTotag = true;
				}
				continue;
			}
			if (line.startsWith(sWARCType)) {
				sWARCTypetag = true;
				// System.out.println(count++);
			} else if (line.startsWith(sWARCTRECID)) {
				id = line.substring(14).trim();
				count++;
				WARCTRECIDtag = true;
				if (logger.isDebugEnabled()) {
					check(preid, id);
				}
			} else if (line.startsWith(sWARCTargetURI)) {
				url = line.substring(17);
				WARCTargetURItag = true;
			} else if (line.startsWith(sWARCContentType)) {
				contentCount++;
				if (contentCount == 2) {
					contentType = line.substring(14).toLowerCase().trim();
					ContentTypetag = true;
				}
			} else if (WARCTRECIDtag && ContentTypetag
					&& line.startsWith(sWARCContentLength)) {
				contentLen = Integer.parseInt(line.substring(16));
				ContentLengthtag = true; // indicate the content begins

			} else if (WARCTRECIDtag && ContentTypetag && ContentLengthtag) {

				int lineCount = 0;

				while ((line = this.br.readLine()) != null) {
					if (!line.startsWith(sWARCb) && !line.startsWith(sWARCType)
							&& !line.startsWith("WARC-Record-ID:")
							&& !line.startsWith("WARC-Warcinfo-ID:")) {// finished
						if (lineCount++ < 10000) {
							if (lineCount < 5
									&& !contentType.matches("application/xml")) {
								if (line.matches("<\\?xml version.{1,40}\\?>")) {
									line = line.replace(
											"<\\?xml version.{1,40}\\?>", "");
								}
							}
							buf.append(line + "\n");
						} else {
							if (line.startsWith("WARC-TREC-ID:")) {
								logger.warn("WARC error:" + "doc-" + id
										+ " is too long");
								break;
							}
						}
					} else {
						// System.out.println(count +":" + line);
						break;
					}
				}
				// System.out.println(id + ", len: " + contentLen + ", " +
				// buf.length());
				if (url == null) {
					url = "";
				}

				if (contentType != null && contentType.startsWith("text/plain")) {
					return getLuceneDoc(id, url, title, buf.toString(),
							PLAINDOCUMENT);
				} else if (contentType != null) {
					try {
						org.w3c.dom.Document domdoc = hf2doc
								.getDocument(new StringReader(buf.toString()));
						title = DomNodeUtils.getTitle(domdoc);
						content = DomNodeUtils.getText(domdoc);
						if (title == null) {
							title = "";
							// logger.info("titlenull contend: \t" +
							// buf.toString() + "\n");
							// System.out.println("titlenull contend: \t" +
							// content);
						}
						// System.out.print("title:" + title);
						// System.out.println("Content:\n" + content);
						return getLuceneDoc(id, url, title, content,
								HTMLDOCUMENT);
					} catch (Exception e) {
						logger.error("unsupported doc: " + id + ", "
								+ contentType);
						logger.debug(buf.toString());
						logger.info(e.getMessage());
						return getLuceneDoc(id, url,
								title != null ? title : "", buf.toString()
										.replaceAll("<[/!]?[^>]+>", ""),
								PLAINDOCUMENT);
					}
					// System.out.println(id);
				} else {
					logger
							.warn("ignore unsupported type (contentType == null): "
									+ id + ", " + contentType);
					return nextDoc();
				}
			}
		}
		return null;
	}

	final static int HTMLDOCUMENT = 0;
	final static int PLAINDOCUMENT = 1;

	public static Document getLuceneDoc(String id, String url, String title,
			String content, int type) {
		Document doc = new Document();
		if (type == HTMLDOCUMENT) {
			try {
				Field fieldid = new Field("id", id, Store.YES,
						Index.NOT_ANALYZED_NO_NORMS);
				Field fieldurl = new Field("url", url, Store.COMPRESS, Index.NO);
				Field fieldTitle = new Field("title", title, Store.COMPRESS,
						Index.ANALYZED, TermVector.WITH_POSITIONS);
				Field fieldContent = new Field("content", content, Store.NO,
						Index.ANALYZED, TermVector.WITH_POSITIONS);
				doc.add(fieldContent);
				doc.add(fieldTitle);
				doc.add(fieldid);
				doc.add(fieldurl);
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else if (type == PLAINDOCUMENT) {
			Field fieldid = new Field("id", id, Store.YES,
					Index.NOT_ANALYZED_NO_NORMS);
			Field fieldurl = new Field("url", url, Store.COMPRESS, Index.NO);
			Field fieldContent = new Field("content", content, Store.NO,
					Index.ANALYZED, TermVector.WITH_POSITIONS);
			doc.add(fieldContent);
			doc.add(fieldid);
			doc.add(fieldurl);
		}
		
		return doc;
	}

	public void setIndexWriter(IndexWriter writer) {
		this.writer = writer;
	}

	public void setParserFile(String path) {

	}

	public void setParserFile(File file) {
		this.file = file;
	}

	public void setup(InputStream is, File file, String encoding) {
		// TODO Auto-generated method stub
		conformsTotag = false;
		bis = new BufferedInputStream(is, Cache);
		this.file = file;
		this.encoding = encoding;
		try {
			this.br = new BufferedReader(new InputStreamReader(bis, encoding));
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * @param args
	 * @throws FileNotFoundException
	 */
	public static void main(String[] args) throws FileNotFoundException {
		String test = "<?xml version=\"1.0\" encoding=\"windows-1252\"?>add";
		// String ptest = test.replaceAll("<[/!]?[^>]+>", "");
		String ptest = test.replaceAll("<\\?xml version.{1,40}\\?>", "");
		System.out.println(ptest);

		System.out.println(ptest.substring(ptest.length()));

		// Clueweb09Parser parser = new Clueweb09Parser();
		// String s = "/home/yezheng/corpus/TREC/Clueweb09/00.warc.gz";
		// File file = new File(s);
		// FileInputStream is = new FileInputStream(file);
		// parser.setup(is, file, "utf8");
		// parser.indexAll();

	}
}
