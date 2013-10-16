#!/bin/bash

cd ~/workspace/lucene2.4.1
ls var/results/trec5*
mv var/results/trec5* var/sigir10/trec5/
mv var/results/trec6* var/sigir10/trec6/
mv var/results/trec7* var/sigir10/trec7/
mv var/results/trec8* var/sigir10/trec8/
mv var/results/trec123* var/sigir10/trec123Disk12T51-200/
mv var/results/disk4and5T301-450* var/sigir10/disk4and5T301-450
mv var/results/WT10GT451-550* var/sigir10/WT10GT451-550


#sys_para="-Dlucene.pDir=trec6Disk45T301-350"
collections=(-Dlucene.pDir=trec5Disk24T251-300 -Dlucene.pDir=trec6Disk45T301-350 -Dlucene.pDir=trec7Disk45-CR-T351-400 -Dlucene.pDir=trec8Disk45-CR-T401-450 -Dlucene.pDir=trec123Disk12T51-200 -Dlucene.pDir=disk4and5T301-450 -Dlucene.pDir=WT10GT451-550)
resultsDir=(trec5 trec6 trec7 trec8 trec123Disk12T51-200 disk4and5T301-450 WT10GT451-550)
for (( i=4;i<7;i+=1 ));do
  trec_lucene.sh  ${collections[i]} -e "var/sigir10/${resultsDir[i]}/*.res"
done
