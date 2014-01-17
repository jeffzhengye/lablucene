#!/bin/bash


ant jar; git add src/; git add etc/; git add lib/;

if [ -n "$1" ];
then
	git commit -a -m "$1" ;git push
else
	git commit -a -m 'minor' ;git push
fi

exit
ant jar; git add src/; git add lib/; git commit -a -m 'minor' ;git push
