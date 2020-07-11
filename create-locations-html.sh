#!/bin/bash

set -eu

echo "<H4>US</H4>"

./print-links.sh "US" "US"

worst_states=$(./get-worst-state-by-death-pop.sh 10|sed "s/ /%20/g"|tr "\n" "/")
worst_counties=$(./get-worst-county-by-death-pop.sh 10|sed "s/ /%20/g"|tr "\n" "/")

echo "<H4>Worst 10 States</H4>"
echo "<p>as judged by deaths per capita</p>"

./print-links.sh "US/${worst_states}" "Hot 10 States"

echo "<H4>Worst 10 Counties</H4>"
echo "<p>as judged by deaths per capita</p>"

./print-links.sh "US/${worst_counties}" "Hot 10 Counties"



echo "<h4>Metro Areas</h4>"

./print-links.sh "Washington/Washington,King/Washington,Kitsap/Washington,Snohomish/Washington,Pierce" Seattle
./print-links.sh "New%20York,New%20York%20City/New%20York,Westchester/New%20York,Nassau/New%20York,Rockland/New%20Jersey,Hudson" "New York City"
./print-links.sh "New%20York,Cattaraugus/New%20York,Allegany/Pennsylvania,McKean/Pennsylvania,Potter" "Little Genesee"
./print-links.sh "Massachusetts,Middlesex/Massachusetts,Suffolk/Massachusetts,Norfolk" "Natick"
./print-links.sh "District%20of%20Columbia/Maryland,Charles/Maryland,Frederick/Maryland,Montgomery/Maryland,Prince%20Georges/Virginia,Fairfax/Virginia,Loudoun/Virginia,Prince%20William/Virginia,Arlington" "DC"


echo "<H4>All States</H4>"

statelist=""

for state in `cat data.git/us-states.csv |grep -v "date"|cut -d "," -f 2|sed "s/ /%20/g"|sort|uniq`
do
  statelist="${statelist}${state}/"
done

./print-links.sh "$statelist" "All States"



echo "<H4>States</H4>"


for state in `cat data.git/us-states.csv |grep -v "date"|cut -d "," -f 2|sed "s/ /_/g"|sort|uniq`
do
  s=$(echo $state|sed "s/_/ /g")
  sw=$(echo $s|sed "s/ /%20/g")
  
  ./print-links.sh $sw "$s"

  all_c="$sw"
  for county in `cat data.git/us-counties.csv |tr -d "'"|cut -d "," -f 2,3|grep ",$s"|grep -v "Unknown"|cut -d "," -f 1|sort|uniq|tr " " "_"`
  do
    c=$(echo $county|tr "_" " ")

    cw=$(echo $c|sed "s/ /%20/g")
    all_c="$all_c/$sw,$cw"
  done
  ./print-links.sh "$all_c" "$s - All Counties"
done

echo "<H4>Counties</H4>"

for state in `cat data.git/us-states.csv |grep -v "date"|cut -d "," -f 2|sed "s/ /_/g"|sort|uniq`
do
  s=$(echo $state|sed "s/_/ /g")
  sw=$(echo $s|sed "s/ /%20/g")

  for county in `cat data.git/us-counties.csv |tr -d "'"|cut -d "," -f 2,3|grep ",$s"|cut -d "," -f 1|sort|uniq|tr " " "_"`
  do
    c=$(echo $county|tr "_" " ")

    cw=$(echo $c|sed "s/ /%20/g")

    ./print-links.sh "$sw,$cw/$sw" "$s: $c County"

  done

done
  


