#!/bin/bash

printf "Begin local testing\n"
printf "Available Tests: 
    \tMachine 1: General API testing\n\t\tMinimum 2 nodes (nodeA,nodeB)\n\t\tDB: https://s3.eu-central-1.amazonaws.com/iotaledger-dbfiles/dev/testnet_files.tgz\n 
    \tMachine 3: Blowball testing\n\t\tNo minimum # of nodes\n\t\tDB: https://s3.eu-central-1.amazonaws.com/iotaledger-dbfiles/dev/Blowball_Tests_db.tar\n 
    \tMachine 4: Side Tangle Stitching\n\t\tNo minimum # of nodes\n\t\tDB: https://s3.eu-central-1.amazonaws.com/iotaledger-dbfiles/dev/Stitching_tests_db.tar\n
    \tMachine 5: Milestone Validation\n\t\tMinimum 2 nodes (nodeA,nodeB)\n\t\tDB: https://s3.eu-central-1.amazonaws.com/iotaledger-dbfiles/dev/Validation_tests_db.tar\n\n"

IS_MACHINE=false
while [ $IS_MACHINE == false ]
do
    printf "\nEnter the machine number you would like to test: [Machine number] "
    read MACHINE
    MACHINE_NAME="machine$MACHINE"
    DIR=./tests/features/$MACHINE_NAME
    if [ -d $DIR ]; then 
      IS_MACHINE=true  
    else
      printf "Machine $MACHINE does not exist."    
    fi
done

printf "\nYou would like to run test $MACHINE, correct?: [y/n] "
read answer 


if [ "$answer" == "y" ]; then 
    printf "\nRunning test for Machine $MACHINE.\n"


    FILE="$DIR/output.yml"
    echo $FILE
    echo
    #DIR=./    
    #FILE=$DIR/output.yml
    touch $FILE
    echo "nodes: " > $FILE

    printf "Enter the number of nodes you would like to run these tests on: [number]"
    read NUM_NODES  
    
    for ((NODE=0; NODE < $NUM_NODES; ++NODE));
        do
            printf "\n"
            INNER_ARRAY=(name host apiPort udpPort)
            for VAR in ${!INNER_ARRAY[@]}
                do
                    KEY=${INNER_ARRAY[$VAR]}
                    NODE_INDEX=$((NODE + 1))
                    printf "Enter the $KEY for node $NODE_INDEX: "
                    read NODE_INPUT 
                    INNER_ARRAY[$VAR]=$NODE_INPUT
                done            
            echo -e "  ${INNER_ARRAY[0]}:" >> $FILE
            echo -e "    host: ${INNER_ARRAY[1]}" >> $FILE
            echo -e "    clusterip: ${INNER_ARRAY[1]}" >> $FILE
            echo -e "    podip: ${INNER_ARRAY[1]}" >> $FILE
            echo -e "    ports:" >> $FILE
            echo -e "      api: ${INNER_ARRAY[2]}" >> $FILE
            echo -e "      gossip-udp: ${INNER_ARRAY[3]}" >> $FILE
            echo -e "    clusterip_ports:" >> $FILE
            echo -e "      api: ${INNER_ARRAY[2]}" >> $FILE
            echo -e "      gossip-udp: ${INNER_ARRAY[3]}" >> $FILE

        done
    
    printf "output.yml file configured. Running test...\n"
    cd $DIR    
    FEATURE=$(find . -name "*.feature") 
    cd ../../../
    pwd
        
    echo $FEATURE   
    
    aloe $FEATURE --nologcapture --verbose --where $DIR 


elif [ "$answer" == "n" ]; then 
    echo "Exiting." 
else
    printf "\nIncorrect argument. Please enter [y] or [n] and press [Enter]\n\n"
    BASE=`basename "$0"`    
    bash $BASE && exit
fi
