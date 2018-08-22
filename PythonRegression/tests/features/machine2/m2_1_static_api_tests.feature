@machine2 @static
Feature: Test API calls on Machine 2
	Test various api calls to make sure they are responding
	correctly 
	
	@nodeInfo
	Scenario: GetNodeInfo is called
		Given "getNodeInfo" is called on each node in "machine2" 
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
	
	
	@neighbors
	Scenario: GetNeighbors is called
		Given "getNeighbors" is called on each node in "machine2"
		Then a response with the following is returned:
		|keys								|
		|address							|
		|numberOfAllTransactions			|
		|numberOfAllTransactionRequests		|
		|numberOfNewTransactions			|
		|numberOfInvalidTransactions		|
		|numberOfSentTransactions			|
		|connectionType						|
		
	@getTips	
	Scenario: GetTips is called
		Given "getTips" is called on each node in "machine2"
		Then a response with the following is returned:
		|keys 								|
		|hashes								|
		|duration							|

	@getTrytes
	Scenario: GetTrytes is called 
		Given getTrytes is called with the hash static_vals.TEST_HASH
		Then the response should be equal to static_vals.TEST_TRYTES
	
	
	@transactionsToApprove	
 	Scenario: GetTransactionsToApprove is called
    	Given "getTransactionsToApprove" is called on each node in "machine1"
		Then a response with the following is returned: 
		|keys								|
		|trunkTransaction					|
		|branchTransaction					|
		|duration							|
		

		