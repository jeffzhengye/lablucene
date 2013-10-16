#!/bin/bash

if [ $#!= 1 ]
then
	if [ $#!= 2 ]
	then
		echo "taging1"
 		 echo "usage: setup.sh [-a] <collection directory>"
  		echo "where collection directory is where the test"
  		echo "collection to index is stored."
 		 exit 1;
	fi
fi


fullPath () {
	t='TEMP=`cd $TEMP; pwd`'
	for d in $*; do
		eval `echo $t | sed 's/TEMP/'$d'/g'`
	done
}
export LUCENE_HOME="/home/yezheng/workspace/lucene2.4.1"

LUCENE_BIN=`dirname $0`
if [ -e "$LUCENE_BIN/lucene-env.sh" ];
then
	. $LUCENE_BIN/lucene-env.sh
fi

#setup LUCENE_HOME
if [ ! -n "$LUCENE_HOME" ]
then
	#find out where this script is running
	TEMPVAR=`dirname $0`
	echo TEMPVAR
	#make the path abolute
	fullPath TEMPVAR
	#terrier folder is folder above
	LUCENE_HOME=`dirname $TEMPVAR`
	
fi

#setup CLASSPATH
for jar in $LUCENE_HOME/lib/*.jar; do
	if [ ! -n "$CLASSPATH" ]
	then
		CLASSPATH=$jar
	else
		CLASSPATH="$CLASSPATH:$jar"
	fi
done

#LUCENE_HOME= $TERRIER_HOME
echo "Setting LUCENE_HOME to $TERRIER_HOME"

#setup LUCENE_ETC
if [ ! -n "$LUCENE_ETC" ]
then
	LUCENE_ETC=$TERRIER_HOME/etc
fi

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



#updating the address_collection file

if [ $# == 1 ]
then 
	find $1 -type f | sort > $LUCENE_ETC/collection.spec
fi


if [ $# == 2 ]
then 
	if [ "$1"X = "-a"X ]
	then 
		find $2 -type f | sort >> $LUCENE_ETC/collection.spec
	else
		echo "taging3"
		echo "usage: setup.sh [-a] <collection directory>"
  		echo "where collection directory is where the test"
  		echo "collection to index is stored."
  		exit 1;
	fi
fi

#find $1 -type f | sort >> $LUCENE_ETC/collection.spec
#echo $LUCENE_ETC/collection.spec
#tail
echo "Updated collection.spec file. Please check that it contains"
echo "all and only all the files to be indexed, or create it manually."
