#!/bin/bash

mkdir -p tmp


n=${1}
curl http://localhost:8115/worst/$1 |jq -r .county.ordered_list > tmp/$$.tmp

for k in `seq 0 $(($n-1))`
do
  cat tmp/$$.tmp | jq -r .[$k]
done

rm -f tmp/$$.tmp


