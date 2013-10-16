/**
 * 
 */
package org.apache.lucene.search;

import java.io.IOException;
import java.util.Set;

import org.apache.lucene.index.Term;
import org.apache.lucene.util.ToStringUtils;
import org.dutir.lucene.util.Rounding;

/**
 * @author yezheng
 * 
 */
public class RTermQuery extends RQuery {

	String description = "";

	private Term term;

	/** Constructs a query for the term <code>t</code>. */
	public RTermQuery(Term t) {
		term = t;
	}

	/** Returns the term of this query. */
	public Term getTerm() {
		return term;
	}

	public String getInfo() {
		return this.description;
	}

	public void setInfo(String info) {
		this.description = info;
	}

	// RTermWeight weight = null;
	public Weight createWeight(Searcher searcher) throws IOException {
		RTermWeight weight = RTermWeightManager.getFromPropertyFile();
		weight.initial(searcher, this);
		this.description = weight.getInfo();
		return weight;
	}

	public void extractTerms(Set terms) {
		terms.add(getTerm());
	}

	/** Prints a user-readable version of this query. */
	public String toString(String field) {
		StringBuffer buffer = new StringBuffer();
		if (!term.field().equals(field)) {
			buffer.append(term.field());
			buffer.append(":");
		}
		buffer.append(term.text());
		buffer.append(ToStringUtils.boost(getBoost()));
		buffer.append(ToStringUtils.weight(getOccurNum()));
		return buffer.toString();
	}

	public String toString() {
		return term.text() + ":" + Rounding.format(getOccurNum(), 4);
	}

	/** Returns true iff <code>o</code> is equal to this. */
	public boolean equals(Object o) {
		if (!(o instanceof RTermQuery))
			return false;
		RTermQuery other = (RTermQuery) o;
		// return (this.getBoost() == other.getBoost())
		// && this.term.equals(other.term);
		return this.term.equals(other.term);
	}

	/** Returns a hash code value for this object. */
	public int hashCode() {
		// return Float.floatToIntBits(getBoost()) ^ term.hashCode();
		return term.hashCode();
	}

}
