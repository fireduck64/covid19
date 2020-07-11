#!/bin/bash

echo "date,US,WA,King"

for date in `cat data.git/us-states.csv|grep -v "fips"|cut -d "," -f 1|sort|uniq`
do

us_cases=0

for n in `cat  data.git/us-states.csv|grep "$date" |cut -d "," -f 4`
do
  us_cases=$(($us_cases+$n))
done


wa_cases=$(cat  data.git/us-states.csv|grep "$date" | grep "Washington"|cut -d "," -f 4)
king_cases=$(cat  data.git/us-counties.csv|grep "$date" | grep "Washington"|grep "King"|cut -d "," -f 5)

if [ -z "$king_cases" ]
then
  king_cases="0"
fi

echo  "$date,$us_cases,$wa_cases,$king_cases"


done

