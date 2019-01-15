Feature: Test GTTA for blowballs

	Scenario: GTTA is called 1000 times on nodeA
		GTTA should be called 1000 times on a node. The returned
		transactions will be checked to find the number of referenced 
		milestones. The total percentage of returned milestones should 
		be less than 5%. 
		
		Given "getTransactionsToApprove" is called 1000 times on "all nodes" with:
		|keys           |values                 |type           |
		|depth          |3                      |int            |

		And "findTransactions" is called on "nodeA" with:
		|keys           |values                 |type           |
		|addresses      |TEST_BLOWBALL_COO      |staticList     |

		#Insert your testnet coordinator address above
		Then the returned GTTA transactions will be compared with the milestones
		And less than 5 percent of the returned transactions should reference milestones
	