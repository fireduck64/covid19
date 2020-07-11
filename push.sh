#!/bin/sh

cd /home/clash/projects/covid19/web

cat index-head.html locations.html index-tail.html > index.html

/home/clash/.local/bin/aws s3 sync --acl=public-read --metadata "Access-Control-Allow-Origin=*" --delete . s3://1209k-covid19


