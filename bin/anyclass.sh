#!/bin/bash
fullPath () {
	t='TEMP=`cd $TEMP; pwd`'
	for d in $*; do
		eval `echo $t | sed 's/TEMP/'$d'/g'`
	done
}

#export LUCENE_HOME="/media/disk/workspace/LabLucene"
export LUCENE_HOME=`pwd`
echo "LUCENE_HOME=$LUCENE_HOME"

#LUCENE_ETC="clueweb09"
#LUCENE_ETC="trec8Disk45-CR-T401-450"
#LUCENE_ETC="trec7Disk45-CR-T351-400"
#LUCENE_ETC="trec6Disk45T301-350"
#LUCENE_ETC="trec5Disk24T251-300"
#LUCENE_ETC="trec9WebAdhocT451-500"
#LUCENE_ETC="trec10WebAdhocT501-500"
#LUCENE_ETC="trec123Disk12T51-200"
#export LUCENE_ETC="WT10GT451-550"
#export LUCENE_ETC="WT2G"
#export LUCENE_ETC="robust04"
#LUCENE_ETC="disk4and5T301-450"
#export LUCENE_ETC="GOV2"
#export LUCENE_ETC="robust"
#export LUCENE_ETC="robustT301-450"
#LUCENE_ETC="disk12NEWST51-200"
#export LUCENE_ETC="socialTag"
#export LUCENE_ETC="Chemiscal"
#export LUCENE_ETC="trecDisk1to5"
export LUCENE_ETC="Microblog11"
export LUCENE_ETC="Microblog12"
#export LUCENE_ETC="genomic04"
#export LUCENE_ETC="genomic06"

if [ -n "$ETC" ]
then 
	export LUCENE_ETC=$ETC;
	echo "using: ETC $ETC"
fi



LUCENE_BIN=`dirname $0`
if [ -e "$LUCENE_BIN/lucene-env.sh" ];
then
    ./$LUCENE_BIN/lucene-env.sh
fi

#setup LUCENE_HOME
if [ -n "$LUCENE_HOME" ]
then
	#find out where this script is running
	TEMPVAR=`dirname $0`
	#make the path abolute
	fullPath TEMPVAR
	#lucene folder is folder above
	LUCENE_HOME=`dirname $TEMPVAR`
fi

echo "Setting LUCENE_HOME to $LUCENE_HOME"

#setup LUCENE_ETC
if [ ! -n "$LUCENE_ETC" ];
then
        LUCENE_ETC=$LUCENE_HOME/etc;
elif [[ $LUCENE_ETC == /* ]]; then
        echo ""
else
        LUCENE_ETC=$LUCENE_HOME/etc/$LUCENE_ETC;
fi
echo "DataSet:${LUCENE_ETC}"

#setup JAVA_HOME
if [ ! -n "$JAVA_HOME" ]
then
	#where is java?
	TEMPVAR=`which java`
	#j2sdk/bin folder is in the dir that java was in
	TEMPVAR=`dirname $TEMPVAR`
	#then java install prefix is folder above
	JAVA_HOME=`dirname $TEMPVAR`
	echo "Setting JAVA_HOME to $JAVA_HOME"
fi

#setup CLASSPATH
for jar in $LUCENE_HOME/lib/*.jar; do
	if [ ! -n "$CLASSPATH" ]
	then
		CLASSPATH=$jar
	else
		CLASSPATH=$CLASSPATH:$jar
	fi
done


if [ ! -n "$LUCENE_HEAP_MEM" ];
then
    LUCENE_HEAP_MEM=1024M
fi

#echo $JAVA_OPTIONS
#echo $CLASSPATH
#exit
java -Xmx$LUCENE_HEAP_MEM $JAVA_OPTIONS $LUCENE_OPTIONS \
	 -Dlucene.etc=$LUCENE_ETC \
	 -Dlucene.home=$LUCENE_HOME \
         -Dlucene.setup=$LUCENE_ETC/lucene.properties \
     -cp $CLASSPATH $@
