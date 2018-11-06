Feature: Test transaction confirmation

    Scenario: Zero Value Transactions are confirmed
        In this test, a number of zero value transactions will be made to a specified node.
        A milestone will be issued that references these transactions, and this should
        confirm the transations.

        Given "10" transactions are issued on "nodeA" with:
        |keys                   |values                     |type           |
        |address                |TEST_ADDRESS               |staticValue    |
        |value                  |0                          |int            |
        |tag                    |ZERO9VALUE                 |string         |

        And "findTransactions" is called on "nodeA" with:
        |keys                   |values                     |type           |
        |tags                   |ZERO9VALUE                 |list           |

        Then the response for "findTransactions" on "nodeA" is stored in the static value "FOUND_TRANSACTIONS"
        #In the default DB, the current index is 60. The next milestone issued should be 61.
        #However, due to the M bug, the index registered on the node will be much higher
        When a milestone is issued with index 61 and referencing a hash from "FOUND_TRANSACTIONS"
        #Give the node 10 seconds to process to the new milestone
        And we wait "15" second/seconds

        Then "getInclusionStates" is called on "nodeA" with:
        |keys                   |values                     |type           |
        |transactions           |FOUND_TRANSACTIONS         |staticValue    |
        |tips                   |latestMilestone            |configValue    |

        And the response for "getInclusionStates" should return with:
        |keys                   |values                     |type           |
        |states                 |True                       |bool           |


    @now
    Scenario: Value Transactions are confirmed
        In this test, a number of value transactions will be made to a specified node.
        A milestone will be issued that references these transactions, and this should
        confirm the transations.

        Given "10" transactions are issued on "nodeA" with:
        |keys                   |values                     |type           |
        |seed                   |THE_BANK                   |staticList     |
        |address                |TEST_ADDRESS               |staticValue    |
        |value                  |10                         |int            |
        |tag                    |VALUE9TRANSACTION          |string         |

        And "findTransactions" is called on "nodeA" with:
        |keys                   |values                     |type           |
        |tags                   |VALUE9TRANSACTION          |list           |

        Then the response for "findTransactions" on "nodeA" is stored in the static value "FOUND_TRANSACTIONS"

        And "checkConsistency" is called on "nodeA" with:
        |keys                   |values                     |type           |
        |tails                  |FOUND_TRANSACTIONS         |staticValue    |

        #In the default DB, the latest sent index will be 61. The next milestone issued should be 62.
        #However, due to the M bug, the index registered on the node will be much higher
        When a milestone is issued with index 63 and referencing a hash from "FOUND_TRANSACTIONS"
        #Give the node 10 seconds to process to the new milestone
        And we wait "15" second/seconds

        Then "getInclusionStates" is called on "nodeA" with:
        |keys                   |values                     |type           |
        |transactions           |FOUND_TRANSACTIONS         |staticValue    |
        |tips                   |latestMilestone            |configValue    |

        And the response for "getInclusionStates" should return with:
        |keys                   |values                     |type           |
        |states                 |True                       |bool           |
