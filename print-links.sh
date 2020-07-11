#!/bin/bash

locations=$1
label=$2

if [ -z "$2" ]
then
  echo "Syntax: locations label"
  exit 1
fi

echo -n "<li>"
echo -n "$label"
echo -n " <a href='cases.html#$locations'>Cases</a>"
echo -n " <a href='cases.html#$locations?include_delta=true'>with delta</a>"
echo -n " <a href='cases.html#$locations?pop_norm=true'>per 100k pop</a>"
echo -n " <a href='death.html#$locations'>Deaths</a>"
echo -n " <a href='death.html#$locations?include_delta=true'>with delta</a>"
echo -n " <a href='death.html#$locations?pop_norm=true'>per 100k pop</a>"
echo "</li>"

