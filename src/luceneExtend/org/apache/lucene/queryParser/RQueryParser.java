/**
 * 
 */
package org.apache.lucene.queryParser;

import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.RTermQuery;
import org.apache.lucene.search.TermQuery;

/**
 * @author yezheng
 * 
 */
public class RQueryParser extends QueryParser {

	public RQueryParser(CharStream stream) {
		super(stream);
		// TODO Auto-generated constructor stub
	}

	public RQueryParser(String string, Analyzer standardAnalyzer) {
		super(string, standardAnalyzer);
	}

	protected Query newTermQuery(Term term) {
		return new RTermQuery(term);
	}

	protected void addClause(List clauses, int conj, int mods, Query q) {
		boolean required, prohibited;
		if(q instanceof RTermQuery){
			for(int i=0; i< clauses.size(); i++){
				Object o = clauses.get(i);
				if(o instanceof BooleanClause){
					Query tq = ((BooleanClause) o).getQuery();
					if(tq instanceof RTermQuery){
						int result = ((RTermQuery) tq).getTerm().compareTo(((RTermQuery) q).getTerm());
						if(result ==0){
							tq.addOccurNum();
							return ;
						}
					}
				}
			}
		}
		super.addClause(clauses,conj, mods, q);
	}
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
