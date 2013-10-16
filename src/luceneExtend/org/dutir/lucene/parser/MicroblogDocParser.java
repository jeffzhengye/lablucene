package org.dutir.lucene.parser;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Stack;

import org.apache.log4j.Logger;
import org.apache.lucene.document.DateTools;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.Field.TermVector;
import org.apache.lucene.index.IndexWriter;
import org.dutir.lucene.util.ApplicationSetup;
import org.dutir.lucene.util.TagSet;

/**
 * @author Yezheng
 * 
 */
public class MicroblogDocParser implements DocumentParser {
	static final SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
	static final SimpleDateFormat parser = new SimpleDateFormat("EEE MMM dd HH:mm:ss Z yyyy");
	/** The logger used */
	protected static final Logger logger = Logger.getLogger(GeneralDocParser.class);
	BufferedInputStream bis = null;
	File file = null;
	private String encoding = "utf8";
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
			BufferedReader br = new BufferedReader(
					new InputStreamReader(this.bis, this.encoding));
			String line =null;
			while ((line = br.readLine()) != null) {
				String parts[] = line.split("\t");
//				System.out.println("" + parts.length);
//				for(int i=0; i< parts.length; i++) System.out.println("\t" + parts[i]);
				if(parts.length != 5){
					System.out.println("error: "  + line);
					continue;
				}
				if(parts[3] == null || parts[3].equals("null")) continue;
				
				Field idField = new Field("id", parts[0],
						Store.YES, Index.NOT_ANALYZED_NO_NORMS);
				Field userField = new Field("user", parts[1],
						Store.YES, Index.NOT_ANALYZED_NO_NORMS);
				Field statusField = new Field("status", parts[2],
						Store.YES, Index.NOT_ANALYZED_NO_NORMS);
				
				String date = formatter.format(parser.parse(parts[3]));
				Field timeField = new Field("time", date, Store.YES, Index.NOT_ANALYZED_NO_NORMS);
				Field content = new Field("content", parts[4],
						Store.YES, Index.ANALYZED, TermVector.WITH_POSITIONS);
				Document doc = new Document();
				doc.add(idField);doc.add(userField);doc.add(statusField);doc.add(timeField);doc.add(content);
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


	public void setIndexWriter(IndexWriter writer) {
		this.writer = writer;
	}

	public static void main(String args[]) throws FileNotFoundException {
		
//		MicroblogDocParser parser = new MicroblogDocParser();
//		String s = "/media/disk/Collection/microblog/plain/statistics.10";
//		File file = new File(s);
//		FileInputStream is = new FileInputStream(file);
//		parser.setup(is, file, "8859_1");
//		parser.indexAll();
		try {
			
			String out = formatter.format(parser.parse("Tue Feb 08 04:49:44 +0000 2011"));
			System.out.println(out);
			
			Date date = DateTools.stringToDate(out);
			
			System.out.println(date.getTime());
			System.out.println(date);
			System.out.println(DateTools.timeToString(date.getTime(), DateTools.Resolution.SECOND));
			
			Date date1 = DateTools.stringToDate("20110126082730");
			Date date2 = DateTools.stringToDate("20110208073027");
			if(date1.before(date2)) System.out.println("true");
			else System.out.println("false");
//			System.out.println("\n");
//			long ldate = Long.parseLong("32851298193768448");
////			ldate = date.getTime();
//			date = new Date(ldate);
//			System.out.println(date);
//			System.out.println(DateTools.timeToString(ldate, DateTools.Resolution.SECOND));
//			
//			System.out.println("\n");
//			date = DateTools.stringToDate("32851298193768448");
//			System.out.println(date);
//			System.out.println(date.getTime());
			
		} catch (ParseException e) {
			e.printStackTrace();
		}
	}

}
