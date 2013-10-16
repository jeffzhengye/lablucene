/**
 * 
 */
package org.dutir.lucene.query;

import gnu.trove.TObjectFloatHashMap;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;

import org.apache.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Token;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryParser.AnalyzerManager;
import org.apache.lucene.search.RBooleanClause;
import org.apache.lucene.search.RBooleanQuery;
import org.apache.lucene.search.RConstantScoreRangeQuery;
import org.apache.lucene.search.RQuery;
import org.apache.lucene.search.RTermQuery;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.search.RBooleanClause.Occur;
import org.apache.lucene.search.model.Idf;
import org.dutir.lucene.ISManager;
import org.dutir.lucene.util.ApplicationSetup;
import org.dutir.util.dom.DomNodeUtils;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * @author yezheng
 *
 */
public class ChemicalPATopicParser extends LuceneQueryParser {

	static Logger logger  = Logger.getLogger("ChemicalPATopicParser.class");
	static String pDir = new File(ApplicationSetup.getProperty("trec.topics", "")).getParent();
	static String field = ApplicationSetup.getProperty("Lucene.SearchField", "description");
	static int topK = Integer.parseInt(ApplicationSetup.getProperty("Lucene.ChemicalPATopicParser.topK", "50"));
	TRECQuery trecquery;
	/** The queries in the topic files. */
	protected RBooleanQuery[] queries;
	protected float[] weights;
	private static org.dutir.util.dom.HtmlFileToDocument hf2doc = new org.dutir.util.dom.HtmlFileToDocument();
	
	public ChemicalPATopicParser(){
		trecquery = new TRECQuery();
		queries = new RBooleanQuery[trecquery.getNumberOfQueries()];
		weights = new float[trecquery.getNumberOfQueries()];
		
		extractQuery();
		trecquery.reset();
	}
	
	public ChemicalPATopicParser(String path){
		trecquery = new TRECQuery(path);
		queries = new RBooleanQuery[trecquery.getNumberOfQueries()];
		weights = new float[trecquery.getNumberOfQueries()];
		
		extractQuery();
		trecquery.reset();
	}
	
	public ChemicalPATopicParser(File file){
		trecquery = new TRECQuery(file);
		queries = new RBooleanQuery[trecquery.getNumberOfQueries()];
		weights = new float[trecquery.getNumberOfQueries()];
		
		extractQuery();
		trecquery.reset();
	}
	
	public boolean extractQuery(){
		String filename= null;
		try {
//			System.out.println("tag:" + ApplicationSetup.getProperty("TrecQueryTags.process", ""));
			int pos =0;
			while(trecquery.hasMoreQueries()){
				filename = trecquery.nextQuery().trim();
//				System.out.println( new File(pDir, filename));
				Document domdoc = hf2doc.getDocumentFomeXMLFile(new FileInputStream(new File(pDir, filename)));
				this.queries[pos++] = selectTopK(domdoc, topK);
				if(logger.isDebugEnabled()) logger.debug(pos + ":" + this.queries[pos -1]);
			}
		} catch (Exception e) {
			logger.error("error: " + new File(pDir, filename).getAbsolutePath(), e);
		}
		return true;
	}
	
	static float K_3 = 8f;

	static Searcher searcher = ISManager.getSearcheFromPropertyFile();
	static Analyzer analyzer = AnalyzerManager.getFromPropertyFile();
	
	public RBooleanQuery selectTopK(Document domdoc, int topk){
		String title = DomNodeUtils.getText(domdoc, "invention-title");
		String claims = DomNodeUtils.getText(domdoc, "claims");
		String date = getDate(domdoc);
		String queryterms = title + " " + claims;
		TokenStream ts = analyzer.tokenStream(field, new StringReader(queryterms));
		TObjectFloatHashMap<String> map = new TObjectFloatHashMap<String>();
		Token token = new Token();
		
		try {
			while((token = ts.next(token)) != null){
				map.adjustOrPutValue(token.term(), 1, 1);
			}
			String keys[] = new String[map.size()];
			map.keys(keys);
			float values[] = new float[map.size()];
			values = map.getValues();
			for(int i=0; i < keys.length; i++){
				float n_t = searcher.docFreq(new Term(field, keys[i]));
				values[i] = Idf.log((searcher.maxDoc() - n_t + 0.5f) / (n_t+ 0.5f)) * (K_3 + 1)*values[i]/(K_3 + values[i]);
			}
			
			for (int i = 0; i < topk && i < keys.length; i++){
				int position = i;
				for (int j = i; j < keys.length; j++){
					if (values[j] > values[position])
						position = j;
				}
				
				if (position != i){
					String temp = keys[position];
					keys[position] = keys[i];
					keys[i] = temp;
					
					float tempv = values[position];
					values[position] = values[i];
					values[i] = tempv;
				}
			}
			
			float max = values[0];
			int num = Math.min(topk, keys.length);
			RBooleanQuery bq = new RBooleanQuery();
			bq.setID(this.getQueryId());
			for(int i=0; i < num; i++){
				RTermQuery termq = new RTermQuery(new Term(field, keys[i]));
				
				termq.setOccurNum(values[i]/ max);
				termq.setID(this.getQueryId());
				bq.add(new RBooleanClause(termq, Occur.SHOULD));
			}
			bq.add(new RBooleanClause(lessThanRangeQuery(date), Occur.MUST));
			bq.setMaxClauseCount(1024*10);
			return bq;
			
		} catch (Exception e) {
			e.printStackTrace();
			try {
				System.out.println("We encounter a problem, input anything to continue: " + this.getClass());
				System.in.read();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}
			
		return null;
	}
	

	private String getDate(Document domdoc) {
		StringBuffer buf = new StringBuffer();
		getDate(buf, domdoc, false);
		return buf.toString();
	}
	public static void getDate(StringBuffer sb, Node node, boolean tag) {
		if(node ==null || tag) return;
		String nodeName = null;
		try {
			nodeName = node.getNodeName();
		} catch (Exception e) {
			e.printStackTrace();
			return ;
		}
		
		if (nodeName.equalsIgnoreCase("patent-document")) {
			NamedNodeMap map = node.getAttributes();
			if(map!= null){
				Node attr = node.getAttributes().getNamedItem("date");
				String date = attr.getNodeValue();
				if(!date.matches("[0-9]+{8}")){
					logger.warn("ChemicalPATopicParser: Date parser error for document");
				}else{
					if(logger.isDebugEnabled()) logger.debug("Date: " + date);
				}
				sb.append(date);
				tag = false;
				return;
			}else{
				logger.error("Misformat of tag patent-document: " + "have no data field");
			}
			
		}
		//		System.out.println(node.getNodeName());
		NodeList children = node.getChildNodes();
		if (children != null) {
				int len = children.getLength();
				for (int i = 0; i < len; i++) {
					Node nd = children.item(i);
					getDate(sb, nd, tag);// 递归遍历DOM树
				}


		}
	}
	
	public static String getTitle(Node node){
		StringBuffer buf = new StringBuffer();
		getText(buf, node, false);
		return buf.toString();
	}
	
	public static void getText(StringBuffer sb, Node node, boolean tag) {
		if(node ==null || tag) return;
		String nodeName = null;
		try {
			nodeName = node.getNodeName();
		} catch (Exception e) {
			e.printStackTrace();
			return ;
		}
		
		if (nodeName.equalsIgnoreCase("invention-title")) {
			Node attr = node.getAttributes().getNamedItem("lang");
			if(attr!= null && attr.getNodeValue().equalsIgnoreCase("En")){
				sb.append(DomNodeUtils.getText(node));// 取得结点值，即开始与结束标签之间的信息			
				tag = true;
			}
		}
		//		System.out.println(node.getNodeName());
		NodeList children = node.getChildNodes();
		if (children != null) {
				int len = children.getLength();
				for (int i = 0; i < len; i++) {
					Node nd = children.item(i);
					getText(sb, nd, tag);// 递归遍历DOM树
				}


		}
	}
	
	/* (non-Javadoc)
	 * @see org.dutir.lucene.query.LuceneQueryParser#getNextLuceneQuery(java.lang.String[], org.apache.lucene.analysis.Analyzer)
	 */
	public RBooleanQuery getNextLuceneQuery(String[] fields, Analyzer analyzer) {
		try {
			if(!hasMoreQueries()){
				return null;
			}
//			String squery = this.nextQuery();
//			String queryId = this.getQueryId();
////			System.out.println(queryId + ": " + squery);
//			BooleanQuery query = RMultiFieldQueryParser.parse(squery, fields,
//					analyzer);
//			query.setID(queryId);
			return this.queries[this.trecquery.index++];
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	/* (non-Javadoc)
	 * @see org.dutir.lucene.query.LuceneQueryParser#getNumberOfQueries()
	 */
	public int getNumberOfQueries() {
		return this.trecquery.getNumberOfQueries();
	}

	/* (non-Javadoc)
	 * @see org.dutir.lucene.query.LuceneQueryParser#getQuery(java.lang.String)
	 */
	public String getQuery(String queryNo) {
		String filename = this.trecquery.getQuery(queryNo);
		//TODO
		return null;
	}

	/* (non-Javadoc)
	 * @see org.dutir.lucene.query.LuceneQueryParser#getQueryId()
	 */
	public String getQueryId() {
		return this.trecquery.getQueryId();
	}

	/* (non-Javadoc)
	 * @see org.dutir.lucene.query.LuceneQueryParser#getTopicFilenames()
	 */
	public String[] getTopicFilenames() {
		return this.trecquery.getTopicFilenames();
	}

	/* (non-Javadoc)
	 * @see org.dutir.lucene.query.LuceneQueryParser#hasMoreQueries()
	 */
	public boolean hasMoreQueries() {
		return this.trecquery.hasMoreQueries();
	}

	/* (non-Javadoc)
	 * @see org.dutir.lucene.query.LuceneQueryParser#nextQuery()
	 */
	public String nextQuery() {
//		
//		if (this.trecquery.index == queries.length)
//			return null;
//		return queries[this.trecquery.index++];
		throw new IllegalAccessError();
	}

	/* (non-Javadoc)
	 * @see org.dutir.lucene.query.LuceneQueryParser#reset()
	 */
	public void reset() {
		this.trecquery.reset();
	}

	static String getMessage()
	{
		for(int i=0; i < 1000; i++) System.out.println("123");
		return "1243";
	}
	
//	static Term minLowerTerm = new Term("date", "00000000"); 
	protected RQuery lessThanRangeQuery(String date) {
//		Term minLowerTerm = new Term("date", "00000000"); 
//		Term upperTerm = new Term("date", date);
		RConstantScoreRangeQuery rquery = new RConstantScoreRangeQuery("date", "00000000000000", date, false, false);
//		RangeQuery rquery = new RangeQuery(minLowerTerm, upperTerm, true);
		return rquery;
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
//		ApplicationSetup.setProperty("trec.topics", "PA_all.xml");
//		ApplicationSetup.setProperty("TrecQueryTags.process", "topic,num,file");
//		ChemicalPATopicParser parser = new ChemicalPATopicParser("./PA_all.xml");
		long start = System.currentTimeMillis();
		if(logger.isDebugEnabled()){
			logger.debug(":"  + getMessage());
		}
		System.out.println("total: " + (System.currentTimeMillis() - start));
	}
	
	

}
