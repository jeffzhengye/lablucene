#!/bin/bash

# a key word that can be grepped to identify the jobs
jobkw=$1;shift;

for (( x=0;x<=1;x+=0  ));do
        runningJobs=`ps -ef |grep $USER|grep ${jobkw}|egrep -v 'waitForAllJobs.sh| grep '|wc -l`;
        #echo ALlRunningJobs: $runningJobs;
        # (( runningJobs-=2 ));
        # ps -ef |grep $USER|grep ${jobkw}|egrep -v 'waitForAllJobs.sh|grep';
        if [ "$runningJobs" == "0" ] ;then
                break;
        else
                sleep 5;
        fi;
done
