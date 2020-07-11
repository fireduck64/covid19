#!/bin/bash

date=$(tail -n 1 data.git/us-states.csv | cut -d "," -f 1)


num=$1

# Uses death data for last day as metric
paste data.git/us-states.csv data.git/us-states.csv |grep "^${date}," |tr "\t" ","|cut -d "," -f 5,7|sort -nr | head -n $num|cut -d "," -f 2


