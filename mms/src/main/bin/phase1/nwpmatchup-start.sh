#!/bin/bash

. $MMS_HOME/bin/mms-env.sh
cd $MMS_INST

year=$1
month=$2
# optional parameters
#parts=${3:-a b c d e f}
parts='a b c d e f'

echo "`date -u +%Y%m%d-%H%M%S` submitting tasks for nwpmatchup $year/$month"

read_task_jobs nwpmatchup

if [ -z $jobs ]; then
    for part in $parts
    do
        echo "`date -u +%Y%m%d-%H%M%S` submitting job nwpmatchup $year/$month quartal $part"
        
        line=`qsub -l h_rt=24:00:00,sages_1ppn=1 -j y -cwd -o $MMS_LOG/nwpmatchup-$year-$month-$part.out -N nm-$year$month$part $MMS_HOME/bin/nwpmatchup-run.sh $year $month $part`
        echo $line
        job=`echo $line | awk '{ print $3 }'`
        if [ "$jobs" != "" ]
        then
            jobs="$jobs|$job"
        else
            jobs="$job"
        fi
        echo "$MMS_LOG/nwpmatchup-$year-$month-$part.out/$job" >> $MMS_TASKS/nwpmatchup-$year-$month.tasks
    done
fi

wait_for_task_jobs_completion nwpmatchup