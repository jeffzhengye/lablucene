/**
 * 
 */
package org.apache.lucene.search.model;

import gnu.trove.TObjectFloatHashMap;
import gnu.trove.TObjectFloatIterator;
import gnu.trove.TObjectIntIterator;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;

import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermEnum;
import org.apache.lucene.index.IndexReader.FieldOption;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.search.Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.util.BitUtil;
import org.dutir.lucene.ISManager;
import org.dutir.util.NumConversions;

/**
 * @author Yezheng
 * 
 */
public class Statistics {
	static String filename = "index.stats";

	/** The term frequency in the query. */
	protected float numberOfDocuments;

	// class tuple {
	//
	// /** The average length of documents in the collection. */
	// protected float averageDocumentLength;
	// /** The number of tokens in the collections. */
	// protected float numberOfTokens;
	// /** Number of unique terms in the collection */
	// protected float numberOfUniqueTerms;
	// }

	TObjectFloatHashMap<String> averDocLen = new TObjectFloatHashMap<String>();
	TObjectFloatHashMap<String> numTokens = new TObjectFloatHashMap<String>();
	TObjectFloatHashMap<String> numUniqueTokens = new TObjectFloatHashMap<String>();

	public void printStats() {
		System.out.println("TotalDocument: " + this.numberOfDocuments);
		TObjectFloatIterator<String> iter = averDocLen.iterator();
		while (iter.hasNext()) {
			iter.advance();
			String key = iter.key();
			float value = iter.value();
			float avelen = iter.value();
			System.out.println("averdoclen, " + key + ":" + avelen
					+ ", totalLen: " + this.numberOfDocuments * avelen);
		}
		iter = numTokens.iterator();
		while (iter.hasNext()) {
			iter.advance();
			String key = iter.key();
			float value = iter.value();
			System.out.println("numTokens, " + key + ":" + iter.value());
		}
		iter = numUniqueTokens.iterator();
		while (iter.hasNext()) {
			iter.advance();
			String key = iter.key();
			float value = iter.value();
			System.out.println("uniqueTokens, " + key + ":" + iter.value());
		}
	}

	public Statistics(IndexReader reader) {
		// TODO Auto-generated constructor stub
		try {
			Directory directory = null;

			numberOfDocuments = reader.maxDoc();
			if(reader instanceof MultiReader){
				IndexReader[] readers = ((MultiReader)reader).getSubReaders();
				for(int i=0; i < readers.length; i++){
					directory = readers[i].directory();
					if (directory.fileExists(filename)) {
						IndexInput in = directory.openInput(filename);
						read(in);
					} else {
						write(reader, directory);
					}
				}
			}else{
				directory = reader.directory();
				if (directory.fileExists(filename)) {
					IndexInput in = directory.openInput(filename);
					read(in);
				} else {
					write(reader, directory);
				}
			}

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	Searcher searcher = null;

	public Statistics(Searcher searcher) {
		
		this(searcher.getIndexReader());
		this.searcher = searcher;
	}

	public void printTerms(int topN) {
		try {
			TermEnum te = this.searcher.getIndexReader().terms();
			int i = 0;
			while (i < topN && te.next()) {
				StringBuilder buf = new StringBuilder();
				buf.append(te.term() + ", freq:" + te.termFreq() + ", dfreq: "
						+ te.docFreq());
				System.out.println(buf);
				i++;
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void printTerms() {
		printTerms(Integer.MAX_VALUE);
	}
	void read(IndexInput in) {
		try {
			while (true) {
				String field = in.readString();
				byte[] buf = new byte[8];
				in.readBytes(buf, 0, 8);
				float averDlen = (float) NumConversions.bytesToDouble(buf);
				in.readBytes(buf, 0, 8);
				float numToken = (float) NumConversions.bytesToDouble(buf);
				in.readBytes(buf, 0, 8);
				float numUniqueToken = (float) NumConversions
						.bytesToDouble(buf);
//				this.averDocLen.put(field, averDlen);
				this.averDocLen.adjustOrPutValue(field, averDlen, averDlen);
//				this.numTokens.put(field, numToken);
				this.numTokens.adjustOrPutValue(field, numToken, numToken);
//				this.numUniqueTokens.put(field, numUniqueToken);
				this.numUniqueTokens.adjustOrPutValue(field, numUniqueToken, numUniqueToken);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			// e.printStackTrace();
		} finally {
			in.clone();
		}
	}

	void write(IndexReader reader, Directory director) throws IOException {
		Collection coll = reader.getFieldNames(FieldOption.INDEXED);
		Iterator iter = coll.iterator();
		IndexOutput out = director.createOutput(filename);
		while (iter.hasNext()) {
			String field = (String) iter.next();
			float averDlen = 0;
			byte[] norms = reader.norms(field);
			for (int i = 0; i < norms.length; i++) {
				float norm = Similarity.decodeNorm(norms[i]);
				averDlen += 1 / (norm * norm);
			}
			averDlen = averDlen / norms.length;
			float numToken = 0;
			float numUniqueToken = 0;
			TermEnum te = reader.terms(new Term(field));
			do {
				Term t = te.term();
				if (!t.field().equals(field)) {
					break;
				}
				numToken += te.termFreq();
				numUniqueToken++;
				// System.out.println(te.term().text() + ", " + te.termFreq());
			} while (te.next());
			this.averDocLen.put(field, averDlen);
			this.numTokens.put(field, numToken);
			this.numUniqueTokens.put(field, numUniqueToken);
			out.writeString(field);
			out.writeBytes(NumConversions.doubleToBytes((double) averDlen), 8);
			out.writeBytes(NumConversions.doubleToBytes((double) numToken), 8);
			out.writeBytes(NumConversions
					.doubleToBytes((double) numUniqueToken), 8);
		}
		out.close();
	}

	public float getNumTokens(String field) {
		if (numTokens.contains(field)) {
			return numTokens.get(field);
		} else {
			return 0;
		}
	}

	public float getNumUniqueTokens(String field) {
		if (numUniqueTokens.contains(field)) {
			return numUniqueTokens.get(field);
		} else {
			return 0;
		}
	}

	public float getAverageLength(String field) {
		// TODO Auto-generated method stub
		if (averDocLen.contains(field)) {
			return averDocLen.get(field);
		} else {
			return 0;
		}
	}

	/**
	 * @param args
	 * @throws IOException
	 * @throws CorruptIndexException
	 */
	public static void main(String[] args) throws CorruptIndexException,
			IOException {
		// TODO Auto-generated method stub
		// float num = 80;
		// num = (float) ((float) 1 / Math.sqrt((double) num));
		//
		// byte buf = Similarity.encodeNorm(num);
		// float dec = Similarity.decodeNorm(buf);
		// System.out.println(1 / (dec * dec));
//		TObjectFloatHashMap<String> averDocLen = new TObjectFloatHashMap<String>();
//		averDocLen.put("test", 10);
//		System.out.println(averDocLen.get("test"));
//		averDocLen.adjustOrPutValue("test", 5, 7);
//		System.out.println(averDocLen.get("test"));

		String path = "/home/yezheng/corpus/TREC/chemistry2009/multiFieldPorterIndex";
		Searcher searcher = ISManager.getSearcheFromPropertyFile();
		// IndexReader reader = IndexReader.open(path);
		// Statistics stats = new Statistics(reader);
		// stats.printStats();
		Statistics stats = new Statistics(searcher);
		stats.printStats();
//		stats.printTerms();
	}
}
