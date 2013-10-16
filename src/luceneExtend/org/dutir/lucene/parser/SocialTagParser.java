/**
 * 
 */
package org.dutir.lucene.parser;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import org.apache.log4j.Logger;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.Field.TermVector;
import org.apache.lucene.index.IndexWriter;
import org.dutir.util.Digester;
import org.dutir.util.Strings;
import org.dutir.util.dom.DomNodeUtils;
import org.dutir.util.net.HttpDocument;
import org.dutir.util.stream.StreamManager;

/**
 * @author zheng
 * 
 */
public class SocialTagParser implements DocumentParser {
	private static org.dutir.util.dom.HtmlFileToDocument hf2doc = new org.dutir.util.dom.HtmlFileToDocument();
	static Logger logger = Logger.getLogger(SocialTagParser.class);
	
	private IndexWriter writer;
	private File file;
	private String encoding;
	private BufferedInputStream bis;
	private BufferedReader br;

	
	/*
	 * (non-Javadoc)
	 * 
	 * @see org.dutir.lucene.parser.DocumentParser#indexAll()
	 */
	public void indexAll() {
		logger.info("adding doc: " + this.file.getAbsolutePath());
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

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.dutir.lucene.parser.DocumentParser#nextDoc()
	 */
	public Document nextDoc() throws Exception {
		String line = null;
		String url = null;
		String urlmd5 = null;
		String title = null;
		String tags = null;
		String htmlsource = null;

		while ((line = this.br.readLine()) != null) {
			if (line.startsWith("<title>")) {
				title = line.trim().substring(7, line.length() - 8);

				line = this.br.readLine();
				assert line.startsWith("<url>");
				url = line.trim().substring(5, line.length() - 6);
				urlmd5 = Digester.digest(url);
				// skip savecount
				line = this.br.readLine();
				assert line.startsWith("<savecount>");
				// urlmd5 = line.trim().substring(5, line.length() -5);

				// skip user
				line = this.br.readLine();
				assert line.startsWith("<user>");
				// urlmd5 = line.trim().substring(5, line.length() -5);

				line = this.br.readLine();
				assert line.startsWith("<tags>");
				tags = line.trim().substring(6, line.length() - 7);
//				htmlsource = Strings.normalizeWhitespace(getContentViaURL(url));
				htmlsource = "";
				return getLuceneDoc(urlmd5, title, tags, htmlsource);
			} else {
				continue;
			}
		}
		return null;
	}



	public static String getContentViaURL(String url) {
		try {
			org.w3c.dom.Document domdoc = getDocument(url);
			if (domdoc == null)
				return "";
			String content = DomNodeUtils.getText(domdoc);
			return content;
		} catch (Exception e) {
			return "";
		}
	}

	private static org.apache.commons.httpclient.HttpClient client = new org.apache.commons.httpclient.HttpClient();
	private static org.dutir.util.net.HttpDocument Http = new HttpDocument(client);

	public static org.w3c.dom.Document getDocument(String aURL) {
		try {
			client.setConnectionTimeout(10000);
			org.w3c.dom.Document domdoc = Http.get(aURL);
			return domdoc;
		} catch (Exception e) {
			logger.warn("invalid url: " + aURL, e);
		}
		return null;
	}

	public static Document getLuceneDoc(String urlmd5, String title,
			String tags, String content) {
		Document doc = new Document();
		try {
			Field fieldid = new Field("urlmd5", urlmd5, Store.YES,
					Index.NOT_ANALYZED_NO_NORMS);
			// Field fieldurl = new Field("url", url, Store.COMPRESS, Index.NO);
			Field fieldTitle = new Field("title", title, Store.COMPRESS,
					Index.ANALYZED, TermVector.WITH_POSITIONS);
			Field fieldTag = new Field("tag", tags, Store.COMPRESS,
					Index.ANALYZED, TermVector.WITH_POSITIONS);
			Field fieldTitleTag = new Field("tagtitle", title + " " + tags,
					Store.COMPRESS, Index.ANALYZED, TermVector.WITH_POSITIONS);
			Field fieldContent = new Field("content", content != null ? content
					: "", Store.COMPRESS, Index.ANALYZED,
					TermVector.WITH_POSITIONS);
			doc.add(fieldContent);
			doc.add(fieldTitle);
			doc.add(fieldid);
			doc.add(fieldTag);
			doc.add(fieldTitleTag);
		} catch (Exception e) {
			e.printStackTrace();
		}

		return doc;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.dutir.lucene.parser.DocumentParser#setIndexWriter(org.apache.lucene
	 * .index.IndexWriter)
	 */
	public void setIndexWriter(IndexWriter writer) {
		this.writer = writer;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.dutir.lucene.parser.DocumentParser#setParserFile(java.lang.String)
	 */
	public void setParserFile(String path) {
		setParserFile(new File(path));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.dutir.lucene.parser.DocumentParser#setParserFile(java.io.File)
	 */
	public void setParserFile(File file) {
		this.file = file;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.dutir.lucene.parser.DocumentParser#setup(java.io.InputStream,
	 * java.io.File, java.lang.String)
	 */
	public void setup(InputStream is, File file, String encoding) {
		bis = new BufferedInputStream(is, Cache);
		this.file = file;
		this.encoding = encoding;
		try {
			this.br = new BufferedReader(new InputStreamReader(bis, encoding));
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
	}

	/**
	 * @param args
	 * @throws FileNotFoundException
	 */
	public static void main(String[] args) throws FileNotFoundException {
		SocialTagParser stp = new SocialTagParser();
		String s = "/media/disk1/Collections/socialTags/abortion+clinic+attack.resultslist";
		File file = new File(s);
		FileInputStream is = new FileInputStream(file);
		stp.setup(is, file, "utf8");
		stp.indexAll();
	}

}
