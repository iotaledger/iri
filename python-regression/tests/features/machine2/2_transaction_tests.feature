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
        #In the default DB, the current index is 50. The next milestone issued should be 51.
        When a milestone is issued with index 51 and referencing a hash from "FOUND_TRANSACTIONS"
        #Give the node 10 seconds to solidify the milestone
        And we wait "10" second/seconds

        Then "getInclusionStates" is called on "nodeA" with:
        |keys                   |values                     |type           |
        |transactions           |FOUND_TRANSACTIONS         |staticValue    |
        |tips                   |latestMilestone            |configValue    |

        And the response for "getInclusionStates" should return with:
        |keys                   |values                     |type           |
        |states                 |True                       |bool           |



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

        #In the default test, the latest sent index will be 51. The next milestone issued should be 52.
        When a milestone is issued with index 52 and referencing a hash from "FOUND_TRANSACTIONS"
        #Give the node time to solidify the milestone
        And we wait "10" second/seconds

        Then "getInclusionStates" is called on "nodeA" with:
        |keys                   |values                     |type           |
        |transactions           |FOUND_TRANSACTIONS         |staticValue    |
        |tips                   |latestMilestone            |configValue    |

        And the response for "getInclusionStates" should return with:
        |keys                   |values                     |type           |
        |states                 |True                       |bool           |

