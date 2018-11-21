Feature: Ensure node reliability while stitching a side tangle
	A stitching tangle transaction needs to be issued to the tangle referencing
	a transaction from the main tangle, and a transaction from a large, unconfirmed
	side tangle. Check consistency then needs to be called to ensure the transaction does not
	crash the node or stall out. If the node responds to the api call within a
	reasonable time limit, it has not crashed or stalled. A transaction will then
	need to be cast referencing the stitching transaction should also not crash the node.
	
	Scenario: Check consistency on a stitching transaction responds
		
		Given a stitching transaction is issued on "nodeA" with the tag "STITCHING"
		And "checkConsistency" is called in parallel on "nodeA" with:
		|keys                   |values                 |type           |
		|tails                  |previousTransaction    |responseList   |

		Then the "checkConsistency" parallel call should return with:
		|keys                   |values                 |type           |
		|state                  |False                  |bool           |

		When a transaction is issued referencing the previous transaction

		And "getTransactionsToApprove" is called on "nodeA" with:
		|keys                   |values                 |type           |
		|depth                  |3                      |int            |

		Then a response with the following is returned:
		|keys							|
		|branchTransaction					|
		|duration						|
		|trunkTransaction					|

		
		