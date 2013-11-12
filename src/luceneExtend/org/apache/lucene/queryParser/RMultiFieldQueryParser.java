package org.apache.lucene.queryParser;

/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Map;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.RBooleanClause;
import org.apache.lucene.search.RBooleanQuery;
import org.apache.lucene.search.RPhraseQuery;
import org.apache.lucene.search.RQuery;
import org.apache.lucene.search.RTermQuery;
import org.dutir.lucene.util.ApplicationSetup;

/**
 * A QueryParser which constructs queries to search multiple fields.
 * 
 * 
 * @version $Revision: 692921 $
 */
public class RMultiFieldQueryParser {
	protected String[] fields;
	protected Map boosts;
	private Analyzer analyzer;

	/**
	 * Creates a MultiFieldQueryParser. Allows passing of a map with term to
	 * Boost, and the boost to apply to each term.
	 * 
	 * <p>
	 * It will, when parse(String query) is called, construct a query like this
	 * (assuming the query consists of two terms and you specify the two fields
	 * <code>title</code> and <code>body</code>):
	 * </p>
	 * 
	 * <code>
   * (title:term1 body:term1) (title:term2 body:term2)
   * </code>
	 * 
	 * <p>
	 * When setDefaultOperator(AND_OPERATOR) is set, the result will be:
	 * </p>
	 * 
	 * <code>
   * +(title:term1 body:term1) +(title:term2 body:term2)
   * </code>
	 * 
	 * <p>
	 * When you pass a boost (title=>5 body=>10) you can get
	 * </p>
	 * 
	 * <code>
   * +(title:term1^5.0 body:term1^10.0) +(title:term2^5.0 body:term2^10.0)
   * </code>
	 * 
	 * <p>
	 * In other words, all the query's terms must appear, but it doesn't matter
	 * in what fields they appear.
	 * </p>
	 */
	public RMultiFieldQueryParser(String[] fields, Analyzer analyzer, Map boosts) {
		this(fields, analyzer);
		this.boosts = boosts;
	}

	public RMultiFieldQueryParser(String[] fields, Analyzer analyzer) {
		this.fields = fields;
		this.analyzer = analyzer;
	}

	public static RBooleanQuery parse(String[] queries, String[] fields,
			Analyzer analyzer) throws ParseException {
		if (queries.length != fields.length)
			throw new IllegalArgumentException(
					"queries.length != fields.length");
		RBooleanQuery bQuery = new RBooleanQuery();
		for (int i = 0; i < fields.length; i++) {
			for (int j = 0; j < queries.length; j++) {
				RTermQuery tquery = new RTermQuery(new Term(fields[i],
						queries[j]));
				bQuery.add(tquery, RBooleanClause.Occur.SHOULD);
			}
		}
		return bQuery;
	}

	/**
	 * Parses a query, searching on the fields specified. Use this if you need
	 * to specify certain fields as required, and others as prohibited.
	 * <p>
	 * 
	 * <pre>
	 * Usage:
	 * <code>
	 * String[] fields = {"filename", "contents", "description"};
	 * BooleanClause.Occur[] flags = {BooleanClause.Occur.SHOULD,
	 *                BooleanClause.Occur.MUST,
	 *                BooleanClause.Occur.MUST_NOT};
	 * MultiFieldQueryParser.parse("query", fields, flags, analyzer);
	 * </code>
	 * </pre>
	 *<p>
	 * The code above would construct a query:
	 * 
	 * <pre>
	 * <code>
	 * (filename:query) +(contents:query) -(description:query)
	 * </code>
	 * </pre>
	 * 
	 * @param query
	 *            Query string to parse
	 * @param fields
	 *            Fields to search on
	 * @param flags
	 *            Flags describing the fields
	 * @param analyzer
	 *            Analyzer to use
	 * @throws ParseException
	 *             if query parsing fails
	 * @throws IllegalArgumentException
	 *             if the length of the fields array differs from the length of
	 *             the flags array
	 */
	public static BooleanQuery parse(String query, String[] fields,
			BooleanClause.Occur[] flags, Analyzer analyzer)
			throws ParseException {
		if (fields.length != flags.length)
			throw new IllegalArgumentException("fields.length != flags.length");
		BooleanQuery bQuery = new BooleanQuery();
		for (int i = 0; i < fields.length; i++) {
			RQueryParser qp = new RQueryParser(fields[i], analyzer);
			Query q = qp.parse(query);
			if (q != null && // q never null, just being defensive
					(!(q instanceof BooleanQuery) || ((BooleanQuery) q)
							.getClauses().length > 0)) {
				bQuery.add(q, flags[i]);
			}
		}
		return bQuery;
	}

	public static RBooleanQuery parse(String query, String[] fields,
			Analyzer analyzer) throws ParseException {
		RBooleanQuery bQuery = new RBooleanQuery();
		StringReader reader = null; 
		boolean proximity = Boolean.parseBoolean(ApplicationSetup.getProperty("proximity.enable", "false"));
		float pweight = Float.parseFloat(ApplicationSetup.getProperty("proximity.weight", "1.0"));
		int slop = Integer.parseInt(ApplicationSetup.getProperty("proximity.slop", "1"));
		String pType = ApplicationSetup.getProperty("proximity.type", "SD");
		int qlength = 0;
		for (int i = 0; i < fields.length; i++) {
			reader = new StringReader(query);
			TokenStream ts = analyzer.tokenStream(fields[i], reader);
			org.apache.lucene.analysis.Token token = null;
			org.apache.lucene.analysis.Token pretoken = null;
			try {
				ArrayList<org.apache.lucene.analysis.Token> tlist = new ArrayList<org.apache.lucene.analysis.Token>();
				ArrayList<RQuery> querylist = new ArrayList<RQuery>(); //record all the query in order to setQuerylength for MATF currently. 
				
				while((token = ts.next()) != null){
					RTermQuery tquery = new RTermQuery(new Term(fields[i], token.term()));
					bQuery.add(tquery, RBooleanClause.Occur.SHOULD);
					querylist.add(tquery);
					qlength += 1;
					
					if(pType.equalsIgnoreCase("FD")){
						tlist.add(token);
						continue;
					}
					//deal with sequential dependency. 
					if(pretoken == null){
						pretoken = token; 
						continue;
					}else{
						//deal with sequential dependency 
						if (proximity){
							RPhraseQuery fquery = new RPhraseQuery();
							fquery.add(new Term(fields[i], pretoken.term()));
							fquery.add(new Term(fields[i], token.term()));
							fquery.setBoost(pweight);
							fquery.setOccurNum(1);
							fquery.setSlop(slop);
							bQuery.add(fquery, RBooleanClause.Occur.SHOULD);
							querylist.add(fquery);
						}
						pretoken = token; 
					}
				}
				if(proximity && pType.equalsIgnoreCase("FD")){
					for(int k =0; k < tlist.size() - 1; k++){
						for(int j= k + 1; j< tlist.size(); j++){
							RPhraseQuery fquery = new RPhraseQuery();
							fquery.add(new Term(fields[i], tlist.get(k).term()));
							fquery.add(new Term(fields[i], tlist.get(j).term()));
							fquery.setBoost(pweight);
							fquery.setOccurNum(1);
							fquery.setSlop(slop);
							bQuery.add(fquery, RBooleanClause.Occur.SHOULD);
							querylist.add(fquery);
						}
					}
				}
				//set query length
				for(int li=0; li < querylist.size(); li++){
					querylist.get(li).setqueryLen(qlength);
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		}
		bQuery.setqueryLen(qlength);
		return bQuery;
	}

	/**
	 * Parses a query, searching on the fields specified. Use this if you need
	 * to specify certain fields as required, and others as prohibited.
	 * <p>
	 * 
	 * <pre>
	 * Usage:
	 * <code>
	 * String[] query = {"query1", "query2", "query3"};
	 * String[] fields = {"filename", "contents", "description"};
	 * BooleanClause.Occur[] flags = {BooleanClause.Occur.SHOULD,
	 *                BooleanClause.Occur.MUST,
	 *                BooleanClause.Occur.MUST_NOT};
	 * MultiFieldQueryParser.parse(query, fields, flags, analyzer);
	 * </code>
	 * </pre>
	 *<p>
	 * The code above would construct a query:
	 * 
	 * <pre>
	 * <code>
	 * (filename:query1) +(contents:query2) -(description:query3)
	 * </code>
	 * </pre>
	 * 
	 * @param queries
	 *            Queries string to parse
	 * @param fields
	 *            Fields to search on
	 * @param flags
	 *            Flags describing the fields
	 * @param analyzer
	 *            Analyzer to use
	 * @throws ParseException
	 *             if query parsing fails
	 * @throws IllegalArgumentException
	 *             if the length of the queries, fields, and flags array differ
	 */
	public static BooleanQuery parse(String[] queries, String[] fields,
			BooleanClause.Occur[] flags, Analyzer analyzer)
			throws ParseException {
		if (!(queries.length == fields.length && queries.length == flags.length))
			throw new IllegalArgumentException(
					"queries, fields, and flags array have have different length");
		BooleanQuery bQuery = new BooleanQuery();
		for (int i = 0; i < fields.length; i++) {
			RQueryParser qp = new RQueryParser(fields[i], analyzer);
			Query q = qp.parse(queries[i]);
			if (q != null && // q never null, just being defensive
					(!(q instanceof BooleanQuery) || ((BooleanQuery) q)
							.getClauses().length > 0)) {
				bQuery.add(q, flags[i]);
			}
		}
		return bQuery;
	}

	public static void main(String args[]) {
		String squery = " \"this is\" a";
		Analyzer analyzer = AnalyzerManager.getFromPropertyFile();
		try {
			Query query = RMultiFieldQueryParser.parse(squery,
					new String[] { "title" }, analyzer);
			System.out.println(query);
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
