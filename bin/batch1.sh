#!/bin/bash
#0.1 0.3 0.5 0.7 0.9 0.2 0.4 0.6 0.8 1 , 0.1 0.2 0.3 0.4 0.5 0.6 0.7 0.8 0.9 1.0



collBase=""
collections=(GOV2)
General="--Lucene.Search.WeightingModel Dirichlet_LM --Lucene.PostProcess QueryExpansionLM --Lucene.Search.LanguageModel true --term.selector.name TopicTermSelector --Lucene.QueryExpansion.Model ModelBasedTermSelector --parameter.free.expansion true  --expansion.terms 50 --expansion.mindocuments 2 --TopicTermSelector.threshold 0.15"
for sys_para in ${collections[@]}; do
 for alpha in 0.7; do
  for doc in 10 20 30 50; do 
   for num in 5; do
   bin/waitForJobs.sh  trec_lucene.sh 4;
   trec_lucene.sh -Dlucene.pDir=$sys_para -r -q $General --Lucene.QueryExpansion.LMalpha $alpha  --TopicTermSelector.beta 0.1 --TopicTermSelector.NUM_TOPICS $num --TopicTermSelector.strategy 1 --expansion.documents $doc &
   trec_lucene.sh -Dlucene.pDir=$sys_para -r -q $General --Lucene.QueryExpansion.LMalpha $alpha  --TopicTermSelector.beta 0.1 --TopicTermSelector.NUM_TOPICS $num --TopicTermSelector.strategy 2 --expansion.documents $doc &
   done
  done
 done
done





#:<<Block
collections=(-Dlucene.pDir=trec123Disk12T51-200 -Dlucene.pDir=disk4and5T301-450 -Dlucene.pDir=WT10GT451-550 -Dlucene.pDir=GOV2)
General="--Lucene.Search.WeightingModel Dirichlet_LM --Lucene.PostProcess QueryExpansionLM --Lucene.Search.LanguageModel true --term.selector.name DFRTermSelector --Lucene.QueryExpansion.Model ModelBasedTermSelector --parameter.free.expansion true  --expansion.terms 50 --expansion.mindocuments 1 --TopicTermSelector.expTag true --TopicTermSelector.threshold 0.15 "
for (( i=3; i < 4; i=i+1 )); do
 sys_para=${collections[i]}
  alpha=0.5
   for lambda in 0.5; do
    for doc in 20 30 50; do
     bin/waitForJobs.sh  trec_lucene.sh 4;
     trec_lucene.sh $sys_para -r -q $General --TopicTermSelector.beta 0.1  --Lucene.QueryExpansion.LMalpha $alpha --expansion.documents $doc --ModelBased.lambda $lambda &
    done 
   done
done
#Block


##***********topic based QE based on dLM*********##
:<<Block
#sys_para="-Dlucene.pDir=trec6Disk45T301-350"
collections=(-Dlucene.pDir=trec5Disk24T251-300 -Dlucene.pDir=trec6Disk45T301-350 -Dlucene.pDir=trec7Disk45-CR-T351-400 -Dlucene.pDir=trec8Disk45-CR-T401-450)
General="--Lucene.Search.WeightingModel Dirichlet_LM --Lucene.PostProcess QueryExpansionLM --Lucene.Search.LanguageModel true --term.selector.name TopicTermSelector --Lucene.QueryExpansion.Model KL --parameter.free.expansion true --expansion.documents 10 --expansion.terms 100 --expansion.mindocuments 1 --TopicTermSelector.expTag true"
for sys_para in ${collections[@]}; do

 for num in 3 5 10 15; do
  for beta in 0 0.1 0.2 0.3; do
   for alpha in  0.7 0.8 0.9; do
    bin/waitForJobs.sh  TrecLucene 2;
    trec_lucene.sh $sys_para -r -q $General --TopicTermSelector.beta $beta --Lucene.QueryExpansion.LMalpha $alpha --TopicTermSelector.NUM_TOPICS $num --TopicTermSelector.strategy 1  &
  done
  done
 done

#trec_lucene.sh -e $sys_para
done
Block
#####################*End*##########################

###BM25 Rocchio VS DFR, PrameterFree or not Free #########
:<<Block
#General Parameter Setup
sys_para="-Dlucene.pDir=trec6Disk45T301-350"
General="--Lucene.Search.WeightingModel BM25 --Lucene.Search.LanguageModel false --Lucene.QueryExpansion.Model KL --Lucene.PostProcess QueryExpansionAdap"
for beta in 0.3 0.4 0.5 0.6 0.7; do
 for selector in DFRTermSelector RocchioTermSelector; do
  for free in true false; do
   trec_lucene.sh $sys_para -r -q $General --rocchio.beta $beta --term.selector.name $selector --parameter.free.expansion $free
  done
 done
done
trec_lucene.sh -e $sys_para
Block
###########################################################


####################################################################
:<<Block
sys_para="-Dlucene.pDir=trec6Disk45T301-350"
General="--parameter.free.expansion true"
for alpha in 0.6 0.7 0.8 0.9 1.0; do 
 for selector in DFRTermSelector RocchioTermSelector; do
  trec_lucene.sh $sys_para -r -q $General --Lucene.QueryExpansion.LMalpha $alpha --term.selector.name $selector --expansion.documents 20
 done
done
trec_lucene.sh -e $sys_para
Block
####################################################################

:<<Block
for alpha in 0.2 0.4 0.6; do
	for lambda in 0.1 0.3 0.5 0.7 0.9; do

       	trec_lucene.sh -r -q --TopicTermSelector.strategy 1 --TopicTermSelector.beta $beta --Lucene.QueryExpansion.LMalpha $alpha --ModelBased.lambda $lambda

	done
done

for win in 0; do
for delta in 0.001; do
for terms in 30 50 70 100 150; do
  for documents in  20; do
        bin/trec_lucene.sh -r --bm25.b 0.75 --expansion.terms $terms --expansion.documents $documents --CBQE.delta $delta --CBQE.winSize $win
  done
done
  done
done



50 70 100 150 200 


for k in 0.4 0.5; do
	for numFutures in 100; do
	for terms in 200; do
	bin/trec_terrier.sh -r -q --ClassifierReRanking.includeQueryTermTag true --ClassifierReRanking.classifierName weka.classifiers.functions.SVMreg --ClassifierReRanking.reRankNum 1000 --ClassifierReRanking.k $k --ClassifierReRanking.numOfTermFeatures $numFutures --ClassifierReRanking.positiveNum 10 --ClassifierReRanking.negetiveNum 10 --expansion.terms $terms --expansion.documents 30
	done
	done
done
./bin/trec_terrier.sh -e

for terms in 30 50 70 100 150 200 ; do
  for documents in 5 10 20 30; do
	bin/trec_terrier.sh -r -q --expansion.terms $terms --expansion.documents $documents;
  done
done


Block

:<<Block
para1="--BM25DocLen.GEV_k 0.4212 --BM25DocLen.GEV_u 0.1182 --BM25DocLen.GEV_o 0.47325" 
para2="--BM25DocLen.GEV_n_k 0.51745 --BM25DocLen.GEV_N_u 0.1246 --BM25DocLen.GEV_n_o 0.1246"
para3="--BM25DocLen.gamma_r 6.2091 --BM25DocLen.gamma_o 0.27625"
para4="--BM25DocLen.gamma_n_r 19.2776 --BM25DocLen.gamma_n_o 0.05645"

#for ntcir5
para1="--BM25DocLen.m1_gamma_r 12.28345 --BM25DocLen.m1_gamma_o 0.3129 --BM25DocLen.m1_gamma_n 3.33175" 
para2="--BM25DocLen.gamma_r 50.61645 --BM25DocLen.gamma_o 0.0383 --BM25DocLen.gamma_n_r 57.1654 --BM25DocLen.gamma_n_o 0.0319"
para3="--BM25DocLen.GEV_k -0.03325 --BM25DocLen.GEV_u 0.23245 --BM25DocLen.GEV_o 1.8135 --BM25DocLen.GEV_n_k -0.02975 --BM25DocLen.GEV_N_u 0.20195 --BM25DocLen.GEV_n_o 1.69945"
para4="--BM25DocLen.m4_u1 1.9366 --BM25DocLen.m4_n1 99.43815 --BM25DocLen.m4_u2 1.8083 --BM25DocLen.m4_n2 104.8899"
para5="--BM25DocLen.m5_u1 0.651 --BM25DocLen.m5_s1 0.13875 --BM25DocLen.m5_u2 0.5836 --BM25DocLen.m5_s2 0.1306"


#for ntcir6
para1="--BM25DocLen.m1_gamma_r 12.1635 --BM25DocLen.m1_gamma_o 0.33855 --BM25DocLen.m1_gamma_n 3.33175" 
para2="--BM25DocLen.gamma_r 50.3544 --BM25DocLen.gamma_o 0.0399 --BM25DocLen.gamma_n_r 57.1654 --BM25DocLen.gamma_n_o 0.0319"
para3="--BM25DocLen.GEV_k -0.0019 --BM25DocLen.GEV_u 0.2284 --BM25DocLen.GEV_o 1.87535 --BM25DocLen.GEV_n_k -0.02975 --BM25DocLen.GEV_N_u 0.20195 --BM25DocLen.GEV_n_o 1.69945"
para4="--BM25DocLen.m4_u1 1.9366 --BM25DocLen.m4_n1 103.37725 --BM25DocLen.m4_u2 1.8083 --BM25DocLen.m4_n2 104.8899"
para5="--BM25DocLen.m5_u1 0.65685 --BM25DocLen.m5_s1 0.1278 --BM25DocLen.m5_u2 0.5836 --BM25DocLen.m5_s2 0.1306"



for K in 0 0.1 0.2 0.3 0.4 0.5 0.6 0.7 0.8 0.9 1; do
./bin/trec_terrier.sh -r -q --BM25DocLen.K $K --BM25DocLen.modelID 1 $para1;
done
Block

