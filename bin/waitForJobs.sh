#!/bin/bash

# a key word that can be grepped to identify the jobs
jobkw=$1;shift;
# maximum number of running jobs
jobLimit=$1;shift;

for (( x=0;x<=1;x+=0  ));do
        runningJobs=`ps -ef |grep $USER|grep ${jobkw}|egrep -v 'waitForJobs.sh| grep '|wc -l`;
        if (( "${runningJobs}" < "${jobLimit}" ));then
                break;
        else
                sleep 5;
        fi;
done

