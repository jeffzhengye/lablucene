/**
 * 
 */
package org.dutir.lucene.parser;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

import org.apache.log4j.Logger;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.Field.TermVector;
import org.apache.lucene.index.IndexWriter;
import org.dutir.lucene.util.ApplicationSetup;
import org.dutir.parser.DomNodeHandle;
import org.dutir.util.dom.DomNodeUtils;
import org.w3c.dom.DOMException;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * @author yezheng
 *
 */
public class ChemDocParser implements DocumentParser {

	static Logger logger  = Logger.getLogger(ChemDocParser.class);
	private static org.dutir.util.dom.HtmlFileToDocument hf2doc = new org.dutir.util.dom.HtmlFileToDocument();
	InputStream bis = null;
	File file = null;
	private String encoding = "utf8";
	BufferedReader br = null;
	IndexWriter writer = null;
	boolean parsedTag = false;
	boolean oneFieldIndex = Boolean.parseBoolean(ApplicationSetup.getProperty("Lucene.ChemDocParser.oneFieldIndex", "false"));
	boolean XMLDocBuilder = Boolean.parseBoolean(ApplicationSetup.getProperty("Lucene.ChemDocParser.XMLDocBuilder", "true"));
	
	public ChemDocParser(){
		
	}
	
	int count =0;
	static boolean pmcTag = Boolean.parseBoolean(ApplicationSetup.getProperty("Lucene.ChemDocParser.PMC", "false"));; 
	/* (non-Javadoc)
	 * @see org.dutir.lucene.parser.DocumentParser#indexAll()
	 */
	public void indexAll() {
		String retval = null;
		if(file == null){
			retval = "" + count;
		}else{
			retval = file.getAbsolutePath();
		}
		if(count% 500 ==0 ){
			logger.debug("adding doc: " + count );
		}
		try {
			Document doc = nextDoc();
			while (doc != null) {
				this.writer.addDocument(doc);
				count ++;
//				System.out.println(doc);
				doc = nextDoc();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/* (non-Javadoc)
	 * @see org.dutir.lucene.parser.DocumentParser#nextDoc()
	 */
	public Document nextDoc() throws Exception {
		try {
			if (!parsedTag) {
//				long start = System.currentTimeMillis();
				org.w3c.dom.Document domdoc = null;
				if(XMLDocBuilder){
					domdoc = hf2doc.getDocumentFomeXMLFile(this.bis);
				}else{					
					domdoc = hf2doc
					.getDocument(this.bis, "ISO-8859-1");
				}
//				long end = System.currentTimeMillis();
//				System.out.print("build:" +(end -start)+ "\t");
//				start = end;
				DomNodeHandle handle = null;
				if(pmcTag ){
					handle = new ChemPMCHandler();
				}else{
					handle = new ChemHandler();
				}
				handle = new ChemPMCHandler();
				
//				handle.bbuf = this.tbuf;
				DomNodeUtils.recTravel(domdoc, handle);
//				end = System.currentTimeMillis();
//				System.out.println("parser:" +(end -start)+ "\t");
				parsedTag = true;
				if(pmcTag){
					if(oneFieldIndex){
						return ((ChemPMCHandler) handle).toDocument_OneField();
					}else{
						return ((ChemPMCHandler)handle).toDocument_MultiField();
					}
				}else{
					if(oneFieldIndex){
						return ((ChemHandler)handle).toDocument_OneField();
					}else{
						return ((ChemHandler)handle).toDocument_MultiField();
					}
				}
			} else {
				return null;
			}
		} catch (Exception e) {
			logger.warn("file parser error:", e);
//			logger.warn(new String (tbuf));
		}
		return null;
	}

	
	class ChemHandler implements DomNodeHandle{
		byte bbuf[] = null;
		String ucid = null;
		String backid = null;
		String title = null;
		String date = null;
		StringBuilder absBuf = new StringBuilder();
		StringBuilder claBuf = new StringBuilder();
		StringBuilder desBuf = new StringBuilder();
		boolean pTag = true;
//		StringBuilder desBuf = new StringBuilder();
		public boolean handle(Node node) {
			if (node == null) {
				System.out.println("encounter null node");
				return true;
			}
			String name = node.getNodeName();
			try {
				if(name.equalsIgnoreCase("patent-document")){
					NamedNodeMap map = node.getAttributes();
					if(map == null)return false;
					Node inode = map.getNamedItem("ucid");
					ucid = inode.getNodeValue();
//					int pos = ucid.lastIndexOf("-");
//					ucid = ucid.substring(0, pos);
					
					Node dateNode = map.getNamedItem("date");
					date = dateNode.getNodeValue();
					if(!date.matches("[0-9]+{8}")){
						logger.warn("Date parser error for document: " + ucid + ":" + date);
					}
					return false; //extract ucid, and then continue to extract other info in this Node. 
				}else if(name.equalsIgnoreCase("article")){//is article
					pTag = false;
				}
				// extract patent.
				else if(pTag && name.equalsIgnoreCase("invention-title")){
					title = DomNodeUtils.getText(node);
					return true;
				}else if(pTag && name.equalsIgnoreCase("abstract")){
					String text = DomNodeUtils.getText(node);
					if(absBuf.length() > 0 ){
						absBuf.append(" " + text);
					}else{
						absBuf.append(text);
					}
					return true;
				}else if(pTag && name.equalsIgnoreCase("claims")){
					String text = DomNodeUtils.getText(node);
					if(claBuf.length() > 0 ){
						claBuf.append(" " + text);
					}else{
						claBuf.append(text);
					}
					return true;
				}else if(pTag && name.equalsIgnoreCase("description")){
					String text = DomNodeUtils.getText(node);
					if(desBuf.length() > 0 ){
						desBuf.append(" " + text);
					}else{
						desBuf.append(text);
					}
					return true;			
				}
				//article parser
				else if(!pTag && name.equalsIgnoreCase("art-body")){
					String text = DomNodeUtils.getText(node);
					if(desBuf.length() > 0 ){
						desBuf.append(" " + text);
					}else{
						desBuf.append(text);
					}
					return true;			
				}else if(!pTag && name.equalsIgnoreCase("ms-id")){
					String text = DomNodeUtils.getText(node);
					this.backid = text.trim();
					return true;			
				}
				else if(!pTag && name.equalsIgnoreCase("doi")){
					String text = DomNodeUtils.getText(node);
					ucid = text.trim();
					return true;			
				}else if(!pTag && name.equalsIgnoreCase("titlegrp")){
					String text = DomNodeUtils.getText(node);
					title = text.trim();
					return true;
				}else if(!pTag && name.equalsIgnoreCase("abstract")){
					String text = DomNodeUtils.getText(node);
					this.absBuf.append(text);
					return true;
				}
			} catch (DOMException e) {
				e.printStackTrace();
			}
			return false;
		}
		
		public org.apache.lucene.document.Document toDocument_MultiField(){
			Document doc = new Document();
			
//			if(this.ucid == null || this.ucid.equals("")){
//				this.ucid = "10.1039/" + this.backid;
//			}
			
			if(!pTag){
				this.ucid = "10.1039/" + this.backid;
//				System.out.println(this.ucid);
//				try {
//					System.out.println(new String(this.bbuf, "ISO-8859-1"));
//				} catch (UnsupportedEncodingException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}
//				this.ucid = this.ucid.replace("&sol;", "/");
				if(this.ucid.matches("10\\.1039/[a-zA-Z0-9]+$")){
//					System.out.println("replacement is true");
//					logger.info("matched: " + this.ucid);
				}else{
					logger.info("not matched: " + this.ucid);
					try {
						System.out.print(new String(this.bbuf, "utf8"));
						logger.info("ecoding problem in article file");
						logger.info(new String(this.bbuf, "utf8"));
					} catch (UnsupportedEncodingException e) {
						e.printStackTrace();
					}
				}
			}
			
			Field idField = new Field("id", this.ucid, Store.YES, Field.Index.NOT_ANALYZED_NO_NORMS, TermVector.NO);
			if(title != null){
				Field titleField = new Field("title", this.title, Store.YES,  Field.Index.ANALYZED, TermVector.YES);
				doc.add(titleField);
			}
			Field absField = new Field("abstract", this.absBuf.toString(), Store.NO, Field.Index.ANALYZED, TermVector.YES);
			Field desField = new Field("description", this.desBuf.toString(), Store.NO, Field.Index.ANALYZED, TermVector.YES);
			Field claField = new Field("claims", this.claBuf.toString(), Store.NO, Field.Index.ANALYZED, TermVector.YES);
			Field dateField = new Field("date", this.date, Field.Store.YES, Field.Index.NOT_ANALYZED);
			doc.add(dateField);
			doc.add(idField);
			doc.add(claField);
			doc.add(desField);
			doc.add(absField);
			return doc;
		}
		
		public org.apache.lucene.document.Document toDocument_OneField(){
			if(!pTag){
				this.ucid = "10.1039/" + this.backid;
//				System.out.println(this.ucid);
//				try {
//					System.out.println(new String(this.bbuf, "ISO-8859-1"));
//				} catch (UnsupportedEncodingException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}
//				this.ucid = this.ucid.replace("&sol;", "/");
				if(this.ucid.matches("10\\.1039/[a-zA-Z0-9]+$")){
//					System.out.println("replacement is true");
//					logger.info("matched: " + this.ucid);
				}else{
					logger.info("not matched: " + this.ucid);
					try {
						System.out.print(new String(this.bbuf, "utf8"));
						logger.info("ecoding problem in article file");
						logger.info(new String(this.bbuf, "utf8"));
					} catch (UnsupportedEncodingException e) {
						e.printStackTrace();
					}
				}
			}
			
			Document doc = new Document();
			Field idField = new Field("id", this.ucid, Store.YES, Field.Index.NOT_ANALYZED_NO_NORMS, TermVector.NO);
			if(title != null){
				title = "";
			}
			String text = this.absBuf.toString() +" " + this.desBuf.toString() + " " + this.claBuf.toString() + title;
			Field desField = new Field("description", text, Store.NO, Field.Index.ANALYZED, TermVector.YES);
			Field dateField = new Field("date", this.date, Field.Store.YES, Field.Index.NOT_ANALYZED);
			doc.add(dateField);
			doc.add(idField);
			doc.add(desField);
			
			return doc;
		}
		
	}
	
	
	class ChemPMCHandler implements DomNodeHandle{
		byte bbuf[] = null;
		String ucid = null;
		String backid = null;
		String title = null;
		String date = null;
		StringBuilder secBuf = new StringBuilder();
//		StringBuilder absBuf = new StringBuilder();
//		StringBuilder claBuf = new StringBuilder();
//		StringBuilder desBuf = new StringBuilder();
//		boolean pTag = true;
//		StringBuilder desBuf = new StringBuilder();
		public boolean handle(Node node) {
			if (node == null) {
				System.out.println("encounter null node");
				return true;
			}
			String name = node.getNodeName();
			try {
				if(name.equalsIgnoreCase("date")){
					NamedNodeMap map = node.getAttributes();
					if(map == null)return false;
					Node inode = map.getNamedItem("date-type");
					String datetype = inode.getNodeValue();
					if(datetype.equalsIgnoreCase("accepted")){
						NodeList list = node.getChildNodes();
						String year = null, month = null, day = null;
						for(int i=0, len= list.getLength(); i < len; i++){
							Node tmpNode = list.item(i);
							String tmpname = tmpNode.getNodeName();
							if(tmpname.equalsIgnoreCase("year")){
								year =  DomNodeUtils.getText(tmpNode).trim();
							}else if(tmpname.equalsIgnoreCase("month")){
								month = DomNodeUtils.getText(tmpNode).trim();
								if(month.length() < 2){
									month = "0" + month;
								}
							}else if (tmpname.equalsIgnoreCase("day")){
								day = DomNodeUtils.getText(tmpNode).trim();
								if(day.length() <2){
									day = "0" + day; 
								}
							}
						}
						date = year + month + day;
					}else{
						return true;
					}
					
					if(!date.matches("[0-9]+{8}")){
						logger.warn("Date parser error for document: " + ucid + ":" + date);
					}
					return true; //extract ucid, and then continue to extract other info in this Node. 
				}
				else if(name.equalsIgnoreCase("article-title")){
					title = DomNodeUtils.getText(node);
					return true;
				}else if(name.equalsIgnoreCase("abstract")){
					String text = DomNodeUtils.getText(node);
					if(secBuf.length() > 0 ){
						secBuf.append(" " + text);
					}else{
						secBuf.append(text);
					}
					return true;
				}else if(name.equalsIgnoreCase("sec")){
					String text = DomNodeUtils.getText(node);
					if(secBuf.length() > 0 ){
						secBuf.append(" " + text);
					}else{
						secBuf.append(text);
					}
					return true;
				}else if(name.equalsIgnoreCase("article-id")){
					NamedNodeMap map = node.getAttributes();
					if(map == null)return false;
					Node inode = map.getNamedItem("pub-id-type");
					if(inode == null) return true;
					String value = inode.getNodeValue();
					if(value.equalsIgnoreCase("doi")){
						this.ucid = DomNodeUtils.getText(node).trim();
					}
					return true;
				}
			} catch (DOMException e) {
				e.printStackTrace();
			}
			return false;
		}
		
		public org.apache.lucene.document.Document toDocument_MultiField(){

			return toDocument_OneField();
		}
		
		public org.apache.lucene.document.Document toDocument_OneField(){
//				if(this.ucid.matches("10\\.1039/[a-zA-Z0-9]+$")){
////					System.out.println("replacement is true");
////					logger.info("matched: " + this.ucid);
//				}else{
//					logger.info("not matched: " + this.ucid);
//					try {
//						System.out.print(new String(this.bbuf, "utf8"));
//						logger.info("ecoding problem in article file");
//						logger.info(new String(this.bbuf, "utf8"));
//					} catch (UnsupportedEncodingException e) {
//						e.printStackTrace();
//					}
//				}
			
			Document doc = new Document();
			Field idField = new Field("id", this.ucid, Store.YES, Field.Index.NOT_ANALYZED_NO_NORMS, TermVector.NO);
			if(title != null){
				title = "";
			}
			String text = this.secBuf.toString() + title;
			Field desField = new Field("description", text, Store.NO, Field.Index.ANALYZED, TermVector.YES);
			Field dateField = new Field("date", this.date ==null? "00000000": this.date, Field.Store.YES, Field.Index.NOT_ANALYZED);
			doc.add(dateField);
			doc.add(idField);
			doc.add(desField);
			return doc;
		}
		
	}

	
	public void setIndexWriter(IndexWriter writer) {
		this.writer = writer;
	}

	public void setParserFile(String path) {
		
	}

	public void setParserFile(File file) {
		this.file = file;
	}

	byte tbuf[] = null;
	public void setup(InputStream is, File file, String encoding) {
		parsedTag = false;
		this.file = file;
		this.encoding = encoding;
		try {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			byte buf[] = new byte[1024];
			int blen;
			while ( (blen = is.read(buf)) != -1){
				bos.write(buf, 0, blen);
			}
			byte cbuf[] = bos.toByteArray();
			tbuf = cbuf;
			bis = new ByteArrayInputStream(cbuf);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * @param args
	 * @throws FileNotFoundException 
	 */
	public static void main(String[] args) throws FileNotFoundException {
//		String text = "<art-admin><ms-id>A701092C</ms-id></art-admin><published type=\"print\">";
		pmcTag = true;
		ChemDocParser parser = new ChemDocParser();
		String s = "PA_all.xml";
//		s ="/home/zheng/Desktop/tmp/pi-6-230.nxml";
		File file = new File(s);
		FileInputStream is = new FileInputStream(file);
		parser.setup(is, file, "utf8");
		parser.indexAll();
	}

}
