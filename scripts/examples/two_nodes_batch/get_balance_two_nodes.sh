#!/bin/bash

# get the total balance
total_1=0
total_2=0
balances_1=0
balances_2=0
FLAG="false"
for account in {a..z} {A..Z}
do
    balances_1[$account]=$(curl -s -X GET http://127.0.0.1:5000/get_balance -H 'Content-Type: application/json' -H 'cache-control: no-cache' -d "{\"account\": \"$account\"}")
    balances_2[$account]=$(curl -s -X GET http://127.0.0.1:6000/get_balance -H 'Content-Type: application/json' -H 'cache-control: no-cache' -d "{\"account\": \"$account\"}")
    total_1=$((total_1+balances_1[$account]))
    total_2=$((total_2+balances_2[$account]))
    if [ ${balances_1[$account]} != ${balances_2[$account]} ] ; then
        echo "Balances of" $account "in node1 and node2 are different:" ${balances_1[$account]} -- ${balances_2[$account]}
        FLAG="true"
        #break
    fi
    echo "total balances " $total_1 -- $total_2
done

curl http://localhost:5000/get_utxo -X GET -H "Content-Type: application/json" -d "{\"type\":\"DOT\"}" > utxo1
curl http://localhost:6000/get_utxo -X GET -H "Content-Type: application/json" -d "{\"type\":\"DOT\"}" > utxo2
dot utxo1 -Tpdf -o utxo1.pdf
dot utxo2 -Tpdf -o utxo2.pdf

curl http://localhost:5000/get_dag -X GET -H "Content-Type: application/json" -d "{\"type\":\"DOT\"}" > dag1
curl http://localhost:6000/get_dag -X GET -H "Content-Type: application/json" -d "{\"type\":\"DOT\"}" > dag2
dot dag1 -Tpdf -o dag1.pdf
dot dag2 -Tpdf -o dag2.pdf

curl http://localhost:5000/get_total_order -X GET -H "Content-Type: application/json"  > order1
curl http://localhost:6000/get_total_order -X GET -H "Content-Type: application/json"  > order2

# check the total balance
if [ ${FLAG} == "false" ] && [ ${total_1} -eq "1000000000" ] && [ ${total_2} -eq "1000000000" ];
then
    echo
    echo "UTXO with double spending is OK!"
else
    echo "Wrong!"
    echo "    FLAG = " $FLAG
    echo "    total of node1" ${total_1}
    echo "    total of node2" ${total_2}
    exit -1
fi

