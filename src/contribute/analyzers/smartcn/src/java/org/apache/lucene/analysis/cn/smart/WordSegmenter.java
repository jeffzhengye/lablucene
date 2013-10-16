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

package org.apache.lucene.analysis.cn.smart;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.lucene.analysis.cn.smart.hhmm.HHMMSegmenter;
import org.apache.lucene.analysis.cn.smart.hhmm.SegToken;
import org.apache.lucene.analysis.cn.smart.hhmm.SegTokenFilter;

/**
 * Segment a sentence of Chinese text into words.
 * <p><font color="#FF0000">
 * WARNING: The status of the analyzers/smartcn <b>analysis.cn.smart</b> package is experimental. 
 * The APIs and file formats introduced here might change in the future and will not be 
 * supported anymore in such a case.</font>
 * </p>
 */
class WordSegmenter {

  private HHMMSegmenter hhmmSegmenter = new HHMMSegmenter();

  private SegTokenFilter tokenFilter = new SegTokenFilter();

  /**
   * Segment a sentence into words with {@link HHMMSegmenter}
   * 
   * @param sentence input sentence
   * @param startOffset start offset of sentence
   * @return {@link List} of {@link SegToken}
   */
  public List<SegToken> segmentSentence(String sentence, int startOffset) {

    List<SegToken> segTokenList = hhmmSegmenter.process(sentence);
    // tokens from sentence, excluding WordType.SENTENCE_BEGIN and WordType.SENTENCE_END
    List<SegToken> result = Collections.emptyList();
    
    if (segTokenList.size() > 2) // if its not an empty sentence
      result = segTokenList.subList(1, segTokenList.size() - 1);
    
    for (SegToken st : result)
      convertSegToken(st, sentence, startOffset);
    
    return result;
  }

  /**
   * Process a {@link SegToken} so that it is ready for indexing.
   * 
   * This method calculates offsets and normalizes the token with {@link SegTokenFilter}.
   * 
   * @param st input {@link SegToken}
   * @param sentence associated Sentence
   * @param sentenceStartOffset offset into sentence
   * @return Lucene {@link SegToken}
   */
  public SegToken convertSegToken(SegToken st, String sentence,
      int sentenceStartOffset) {

    switch (st.wordType) {
      case WordType.STRING:
      case WordType.NUMBER:
      case WordType.FULLWIDTH_NUMBER:
      case WordType.FULLWIDTH_STRING:
        st.charArray = sentence.substring(st.startOffset, st.endOffset)
            .toCharArray();
        break;
      default:
        break;
    }

    st = tokenFilter.filter(st);
    st.startOffset += sentenceStartOffset;
    st.endOffset += sentenceStartOffset;
    return st;
  }
  
	public static void main(String args[]){
		long time = System.currentTimeMillis();
		WordSegmenter seg = new WordSegmenter();
		List<String> testStr = new ArrayList<String>();
		testStr.add("this is china");
		testStr.add("12.第");
		testStr.add("一九九五年12月31日,");
		testStr.add("1/++ ￥+400 ");
		testStr.add("-2e-12 xxxx1E++300/++"); 
		testStr.add("1500名常用的数量和人名的匹配 超过22万个");
		testStr.add("据路透社报道，印度尼西亚社会事务部一官员星期二(29日)表示，" 
				+ "日惹市附近当地时间27日晨5时53分发生的里氏6.2级地震已经造成至少5427人死亡，" 
				+ "20000余人受伤，近20万人无家可归。");
		testStr.add("古田县城关六一四路四百零五号");
		testStr.add("欢迎使用阿江统计2.01版");
		testStr.add("51千克五十一千克五万一千克两千克拉 五十一");
		testStr.add("十一点半下班十一点下班");
		testStr.add("福州第一中学福州一中福州第三十六中赐进士及第");
		
		for(int i=0; i < 1000; i++){
			for(String t : testStr){
//				System.out.println(t);	
				List<SegToken> list = seg.segmentSentence(t, 0);
				for(SegToken token: list){
					if(i==0) System.out.print(token.toString()+ " | ");
				}
				if(i==0) System.out.println();
			}
		}
		

		System.out.println("time: " + (System.currentTimeMillis() - time));	
	}
}
