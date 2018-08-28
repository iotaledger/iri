Feature: Test API calls on Machine 1
	Test various api calls to make sure they are responding
	correctly 
	
	Scenario: GetNodeInfo is called
		Given "getNodeInfo" is called on "nodeA" 
		Then a response with the following is returned:
		|keys								|
		|appName							|	
		|appVersion							|
		|duration							|
		|jreAvailableProcessors				|
		|jreFreeMemory						|
		|jreMaxMemory						|
		|jreTotalMemory						|
		|jreVersion							|		
		|latestMilestone					|
		|latestMilestoneIndex				|
		|latestSolidSubtangleMilestone		|
		|latestSolidSubtangleMilestoneIndex |
		|milestoneStartIndex				|
		|neighbors							|
		|packetsQueueSize					|
		|time								|
		|tips								|
		|transactionsToRequest				|
	
	@nodeInfo	
	Scenario: Log GetNodeInfo
		Given a response for "getNodeInfo" exists
		Then create the log directory "./tests/features/machine1/static_test_logs/get_node_info_logs/"
		And log the response to the file "getNodeInfoLog.txt"		 
		

	Scenario: GetNeighbors is called
		Given "getNeighbors" is called on "nodeA"
		Then a response with the following is returned:
		|keys								|
		|address							|
		|numberOfAllTransactions			|
		|numberOfAllTransactionRequests		|
		|numberOfNewTransactions			|
		|numberOfInvalidTransactions		|
		|numberOfSentTransactions			|
		|connectionType						|	 
		
		
	Scenario: GetTips is called
		Given "getTips" is called on "nodeA"
		Then a response with the following is returned:
		|keys 								|
		|hashes								|
		|duration							|

	
	Scenario Outline: GetTrytes is called 
		Given getTrytes is called with the hash <hash>
		Then the response should be equal to <trytes>
		
		Examples:
			|hash 		| trytes 		| 
			|TEST_HASH	| TEST_TRYTES	|
	
		
	Scenario: GetTransactionsToApprove is called
		Given "getTransactionsToApprove" is called on "nodeA"
		Then a response with the following is returned: 
		|keys								|
		|trunkTransaction					|
		|branchTransaction					|
		|duration							|
		

###
# To be replaced with a new neighbor test linking nodes within a given topology together 
### 
#	@neighbors
#	Scenario: Add and remove Neighbors
#		Given 2 neighbors are added with "addNeighbors" on "nodeA"
#		When "getNeighbors" is called, it should return the following neighbors:
#			|neighbors 				|
#			|178.128.236.6:14600 	|
#			|167.99.178.3:14600		|
#		Then "removeNeighbors" will be called to remove the same neighbors 
#		And "getNeighbors" should not return the following neighbors:
#			|neighbors 				|
#			|178.128.236.6:14600 	|
#			|167.99.178.3:14600		|		

	
	Scenario: Broadcast a test transacion
		Send a test transaction from one node in a machine, and find that transaction
		through a different node in the same machine
		
 		Given "nodeA" and "nodeB" are neighbors
		When a transaction with the tag "TEST9TRANSACTION" is sent from "nodeA"
		And findTransaction is called with the same tag on "nodeB" 
		Then the transaction should be found 
		