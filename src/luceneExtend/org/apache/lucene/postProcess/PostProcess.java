/**
 * 
 */
package org.apache.lucene.postProcess;

import org.apache.lucene.search.RBooleanQuery;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.search.TopDocCollector;

/**
 * @author yezheng
 * 
 * this interface define the contract that is used to refine the retrieved results.
 */
public interface PostProcess {
	
	TopDocCollector postProcess(RBooleanQuery query, TopDocCollector topDoc, Searcher seacher);
	public String getInfo();
}
