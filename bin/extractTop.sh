#!/bin/bash


i="1"
while [ $i -lt 51 ]
do
echo $i
grep "rf09-${i} " YUIR.UMas.2 | sed -n '1,1000p' >> YUIR.UMas.2.top1k2
i=$[$i+1]
done
