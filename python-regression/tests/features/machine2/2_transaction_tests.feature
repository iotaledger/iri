Feature: Test transaction confirmation

    Scenario: Zero Value Transactions are confirmed
        In this test, a number of zero value transactions will be made to a specified node.
        A milestone will be issued that references these transactions, and this should
        confirm the transations.

        Given "10" transactions are issued on "nodeA" with:
        |keys                   |values                     |type           |
        |address                |TEST_ADDRESS               |staticValue    |
        |value                  |0                          |int            |

#        Given "attachToTangle" is called 10 times on "nodeA" with:
#        |keys                   |values                     |type           |
#        |branch_transaction     |CONFIRMATION_REFERENCE_HASH|staticValue    |
#        |trunk_transaction      |CONFIRMATION_REFERENCE_HASH|staticValue    |
#        |trytes                 |EMPTY_TRANSACTION_TRYTES   |staticList     |
#        |min_weight_magnitude   |9                          |int            |

#        Then "storeTransactions" is called on "nodeA" with:
#        |keys                   |values                     |type           |
#        |trytes                 |ATTACHED_TRANSACTIONS      |staticValue    |

        And "findTransactions" is called on "nodeA" with:
        |keys                   |values                     |type           |
        |addresses              |TEST_ADDRESS               |staticList     |

        Then the response for "findTransactions" on "nodeA" is stored in the static value "FOUND_TRANSACTIONS"

        #In the default DB, the current index is 60. The next milestone issued should be 61.
        #However, due to the M bug, the index registered on the node will be much higher
        When a milestone is issued with index 61 and referencing a hash from "FOUND_TRANSACTIONS"
        #Give the node 10 seconds to process to the new milestone
        And we wait "10" second/seconds

        Then "getInclusionStates" is called on "nodeA" with:
        |keys                   |values                     |type           |
        |transactions           |FOUND_TRANSACTIONS         |staticValue    |
        |tips                   |latestMilestone            |configValue    |

        And the response for "getInclusionStates" should return with:
        |keys                   |values                     |type           |
        |states                 |True                       |bool           |