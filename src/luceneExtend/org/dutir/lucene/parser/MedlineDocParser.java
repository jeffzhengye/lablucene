package org.dutir.lucene.parser;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import org.apache.log4j.Logger;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.Field.TermVector;
import org.apache.lucene.index.IndexWriter;
import org.dutir.parser.medline.AuthorList;
import org.dutir.parser.medline.Chemical;
import org.dutir.parser.medline.KeywordList;
import org.dutir.parser.medline.MedlineCitation;
import org.dutir.parser.medline.MedlineHandler;
import org.dutir.parser.medline.MedlineParser;
import org.dutir.parser.medline.MeshHeading;
import org.dutir.parser.medline.Topic;
import org.xml.sax.InputSource;

public class MedlineDocParser implements DocumentParser, MedlineHandler {
	static Logger logger = Logger.getLogger(ChemDocParser.class);
	InputStream bis = null;
	File file = null;
	private String encoding = "utf8";
	IndexWriter writer = null;

	public MedlineDocParser() {

	}

	public void indexAll() {
		boolean saveXML = false;
		MedlineParser parser = new MedlineParser(saveXML);
		parser.setHandler(this);
		InputSource inputSource = new InputSource(bis);
		try {
			parser.parse(inputSource);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public Document nextDoc() throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	public void setIndexWriter(IndexWriter writer) {
		this.writer = writer;
	}

	public void setParserFile(String path) {
		this.file = new File(path);
	}

	public void setParserFile(File file) {
		this.file = file;
	}

	public void setup(InputStream is, File file, String encoding) {
		bis = new BufferedInputStream(is, Cache);
		this.file = file;
		this.encoding = encoding;
	}

	public void handle(MedlineCitation citation) {
		Document doc = new Document();
		Field pmidF = new Field("pmid", citation.pmid(), Store.YES,
				Field.Index.NOT_ANALYZED_NO_NORMS, TermVector.NO);
		KeywordList kwlist[] = citation.keywordLists();
		MeshHeading mheading[] = citation.meshHeadings();
		AuthorList alist = citation.article().authorList();
		// ////////keywords//////////
		StringBuffer keywordsBuf = new StringBuffer();
		for (int i = 0; i < kwlist.length; i++) {
			Topic topics[] = kwlist[i].keywords();
			for (int j = 0; j < topics.length; j++) {
				keywordsBuf.append(topics[j].topic() + " ");
			}
		}

		// ///////////////mheadings/////////////////////
		StringBuffer meshBuf = new StringBuffer();
		for (int i = 0; i < mheading.length; i++) {
			Topic topics[] = mheading[i].topics();
			Topic qtopics[] = mheading[i].qualifiers();
			for (int j = 0; j < topics.length; j++) {
				meshBuf.append(topics[j].topic() + " ");
			}
			for (int j = 0; j < qtopics.length; j++) {
				meshBuf.append(qtopics[j].topic() + " ");
			}
		}
		/////////////////Chemical////////////////////
		Chemical[] chems = citation.chemicals();
		StringBuffer chemBuf = new StringBuffer();
		for (int i = 0; i < chems.length; i++) {
			chemBuf.append(chems[i].nameOfSubstance() + " ");
		}
		
		// Field authorF
		Field contentF = new Field("content", citation.article().articleTitle() + " " + citation.article().abstrct()
				.text() + " " + meshBuf.toString() +" " + chemBuf, Store.NO, Field.Index.ANALYZED,
				TermVector.YES);
		doc.add(pmidF);
		doc.add(contentF);
		try {
			this.writer.addDocument(doc);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void delete(String pmid) {
		throw new UnsupportedOperationException();
	}

	public static void main(String args[]) throws FileNotFoundException {
		MedlineDocParser parser = new MedlineDocParser();
		String s = "conf/Medline_Samp2009.xml";
		// s ="/home/zheng/Desktop/tmp/pi-6-230.nxml";
		File file = new File(s);
		FileInputStream is = new FileInputStream(file);
		parser.setup(is, file, "utf8");
		parser.indexAll();
	}

}
