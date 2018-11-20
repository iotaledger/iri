Feature: Test API calls on Machine 1
	Test various api calls to make sure they are responding
	correctly.

	Scenario: GetNodeInfo is called

		#All api calls will be formatted as following, any arguments should be
		#listed below the call in table format
		#Example:
		# "<Api Call name here>" is called on "<insert node name here>" with:
		#|keys      |values             |type           |
		#|<arg key> |<arg val>          |<arg type>     |
		#
		#See tests/features/steps/api_test_steps.py for further details
		#

		Given "getNodeInfo" is called on "nodeA" with:
		|keys       |values				|type   	|

		Then a response with the following is returned:
		|keys						|
		|appName					|
		|appVersion					|
		|duration					|
		|features					|                                                              
		|jreAvailableProcessors				|
		|jreFreeMemory					|
		|jreMaxMemory					|
		|jreTotalMemory 				|
		|jreVersion					|
		|latestMilestone				|
		|latestMilestoneIndex				|
		|latestSolidSubtangleMilestone			|
		|latestSolidSubtangleMilestoneIndex		|
		|milestoneStartIndex				|
		|neighbors					|
		|packetsQueueSize				|
		|time						|
		|tips						|
		|transactionsToRequest				|
		|coordinatorAddress				|


	Scenario: GetNeighbors is called

	    Given "addNeighbors" is called on "nodeA" with:
		|keys       |values				|type           |
		|uris       |nodeB				|nodeAddress    |

		And "getNeighbors" is called on "nodeA" with:
		|keys       |values				|type   	|

		Then a response with the following is returned:
		|keys						|
		|address					|
		|numberOfAllTransactions			|
		|numberOfRandomTransactionRequests		|
		|numberOfNewTransactions			|
		|numberOfInvalidTransactions			|
		|numberOfSentTransactions			|
		|connectionType					|


	Scenario: Add and Remove Neighbors
		Adds nodeB as a neighbor to nodeA, and then removes it.

		Given "addNeighbors" is called on "nodeA" with:
		|keys       |values				|type           |
		|uris       |nodeB				|nodeAddress    |

		Then a response with the following is returned:
		|keys						|
		|addedNeighbors                                 |
		|duration					|


		When "removeNeighbors" is called on "nodeA" with:
		|keys       |values				|type           |
		|uris       |nodeB				|nodeAddress    |


		Then a response with the following is returned:
		|keys						|
		|duration					|
		|removedNeighbors				|


	Scenario: GetTips is called
		Given "getTips" is called on "nodeA" with:
		|keys       |values				|type           |

		Then a response with the following is returned:
		|keys						|
		|hashes						|
		|duration					|



    #Values can be found in util/static_vals.py
	Scenario: GetTrytes is called
		Given "getTrytes" is called on "nodeA" with:
		|keys       |values             |type               |
		|hashes     |TEST_HASH          |staticList         |

		Then the response for "getTrytes" should return with:
		|keys       |values             |type               |
		|trytes     |TEST_TRYTES        |staticValue        |



	Scenario: GetTransactionsToApprove is called
		Given "getTransactionsToApprove" is called on "nodeA" with:
		|keys       |values				|type           |
		|depth      |3					|int            |

		Then a response with the following is returned: 
		|keys						|
		|branchTransaction				|
		|duration					|
		|trunkTransaction				|


	Scenario: CheckConsistency is called
		Given "checkConsistency" is called on "nodeA" with:
		|keys           |values				|type           |
		|tails          |TEST_HASH			|staticList     |

		Then the response for "checkConsistency" should return with:
		|keys           |values         |type           |
		|state		|True           |bool           |

		When an inconsistent transaction is generated on "nodeA"

		And "checkConsistency" is called on "nodeA" with:
		|keys           |values				|type           |
		|tails          |inconsistentTransactions       |responseList   |

		Then the response for "checkConsistency" should return with:
		|keys           |values         |type           |
		|state		|False          |bool           |



	#Values can be found in util/static_vals.py
	Scenario: GetInclusionStates is called
		Given "getInclusionStates" is called on "nodeA" with:
		|keys           |values				|type               |
		|transactions   |TEST_HASH			|staticList         |
		|tips           |TEST_TIP_LIST			|staticValue        |

		Then the response for "getInclusionStates" should return with:
		|keys			|values			|type               |
		|states			|False		        |bool               |

	
	#Address can be found in util/static_vals.py
	Scenario: GetBalances is called
		Given "getBalances" is called on "nodeA" with:
		|keys           |values			        |type               |
		|addresses      |TEST_EMPTY_ADDRESS	        |staticList         |
		|threshold      |100			        |int                |

		Then the response for "getBalances" should return with:
		|keys           |values                         |type		    |
		|balances       |0			        |int     	    |


	Scenario: Interrupt attach to tangle
	    Begins attaching a transaction to the tangle with a high MWM, then issues an interrupt to the node
	    If the interrupt is successful, the attachToTangle response will return a null tryte list

		Given "attachToTangle" is called in parallel on "nodeA" with:
		|keys                   |values			|type           |
		|trytes                 |EMPTY_TRANSACTION_TRYTES|staticList     |
		|trunk_transaction      |TEST_HASH		|staticValue    |
		|branch_transaction     |TEST_HASH		|staticValue    |
		|min_weight_magnitude   |50			|int            |

		And we wait "1" second/seconds
		Then "interruptAttachingToTangle" is called in parallel on "nodeA" with:
		|keys                   |values				|type           |

        # Do not include duration in the return expectations as it will always return a variable amount
		Then the "attachToTangle" parallel call should return with:
		|keys                   |values         |type           |
		|trytes                 |NULL_LIST      |staticValue    |



	Scenario: WereAddressesSpentFrom is called
		Given "wereAddressesSpentFrom" is called on "nodeA" with:
		|keys       |values				|type               |
		|addresses  |TEST_EMPTY_ADDRESS			|staticList         |

		Then a response with the following is returned:
		|keys						|
		|duration					|
		|states						|



	Scenario: Create, attach, store and find a transaction
		Generate a transaction, attach it to the tangle, and store it locally. Then find
		that transaction via its address.

		Given a transaction is generated and attached on "nodeA" with:
		|keys       |values				|type           |
		|address    |TEST_STORE_ADDRESS			|staticValue    |
		|value      |0					|int            |

		Then a response with the following is returned:
		|keys						|
		|trytes						|

		When "storeTransactions" is called on "nodeA" with:
		|keys       |values				|type           |
		|trytes     |TEST_STORE_TRANSACTION		|staticValue    |

		And "findTransactions" is called on "nodeA" with:
		|keys       |values				|type           |
		|addresses  |TEST_STORE_ADDRESS			|staticList     |

		Then a response with the following is returned:
		|keys						|
		|hashes						|



	Scenario: Broadcast a test transacion
		Send a test transaction from one node in a machine with a unique tag, and find that transaction
		through a different node in the same machine
		
		Given "nodeA" and "nodeB" are neighbors
		When a transaction is generated and attached on "nodeA" with:
		|keys       |values				|type           |
		|address    |TEST_ADDRESS			|staticValue    |
		|tag        |TEST9TAG9ONE			|string         |
		|value      |0					|int            |

		And "findTransactions" is called on "nodeB" with:
		|keys       |values             |type           |
		|tags       |TEST9TAG9ONE       |list           |

		Then a response for "findTransactions" should exist

