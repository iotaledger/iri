Feature: Test transaction confirmation

    Scenario: Zero Value Transactions are confirmed
        In this test, a number of zero value transactions will be made to a specified node.
        A milestone will be issued that references these transactions, and this should
        confirm the transactions.

        Given "10" transactions are issued on "nodeA-m3" with:
        |keys                   |values                     |type           |
        |address                |TEST_ADDRESS               |staticValue    |
        |value                  |0                          |int            |
        |tag                    |ZERO9VALUE                 |string         |

        #In the default DB, the current index is 50. The next milestone issued should be 51.
        Then a milestone is issued with index 51 and references:
        |keys                   |values                     |type           |
        |transactions           |evaluate_and_send          |responseValue  |

        #Give the node 10 seconds to solidify the milestone
        And we wait "15" second/seconds

        Then "getInclusionStates" is called on "nodeA-m3" with:
        |keys                   |values                     |type           |
        |transactions           |evaluate_and_send          |responseValue  |
        |tips                   |latestMilestone            |configValue    |

        And the response for "getInclusionStates" should return with:
        |keys                   |values                     |type           |
        | states | True True True True True True True True True True | boolListMixed |


        When a transaction is generated and attached on "nodeA-m3" with:
            | keys    | values       | type        |
            | address | TEST_ADDRESS | staticValue |
            | value   | 0            | int         |

        And "getInclusionStates" is called on "nodeA-m3" with:
            | keys         | values             | type        |
            | transactions | TEST_STORE_ADDRESS | staticList  |
            | tips         | latestMilestone    | configValue |

        Then the response for "getInclusionStates" should return with:
            | keys   | values | type          |
            | states | False  | boolListMixed |

    Scenario: Value Transactions are confirmed
        In this test, a number of value transactions will be made to a specified node.
        A milestone will be issued that references these transactions, and this should
        confirm the transactions.

        Given "10" transactions are issued on "nodeA-m3" with:
        |keys                   |values                     |type           |
        |seed                   |THE_BANK                   |staticList     |
        |address                |TEST_ADDRESS               |staticValue    |
        |value                  |10                         |int            |
        |tag                    |VALUE9TRANSACTION          |string         |

        #In the default test, the latest sent index will be 51. The next milestone issued should be 52.
        Then a milestone is issued with index 52 and references:
        |keys                   |values                     |type           |
        |transactions           |evaluate_and_send          |responseValue  |

        #Give the node time to solidify the milestone
        And we wait "15" second/seconds

        Then "getInclusionStates" is called on "nodeA-m3" with:
        |keys                   |values                     |type           |
        |transactions           |evaluate_and_send          |responseValue  |
        |tips                   |latestMilestone            |configValue    |

        And the response for "getInclusionStates" should return with:
        |keys                   |values                     |type           |
        | states | True True True True True True True True True True | boolListMixed |

        When a transaction is generated and attached on "nodeA-m3" with:
            | keys    | values       | type        |
            | address | TEST_ADDRESS | staticValue |
            | value   | 0            | int         |

        And "getInclusionStates" is called on "nodeA-m3" with:
            | keys         | values             | type        |
            | transactions | TEST_STORE_ADDRESS | staticList  |
            | tips         | latestMilestone    | configValue |

        Then the response for "getInclusionStates" should return with:
            | keys   | values | type          |
            | states | False  | boolListMixed |

    Scenario: Valid value transfer bundle that doesnt affect ledger state
        We want to ascertain that ledger state is always calculated correctly.
        Even in the presence of a bundle that handles funds but without changing address

        Given "1" transaction is issued on "nodeA-m3" with:
        |keys                   |values                     |type           |
        |address                |TEST_ADDRESS               |staticValue    |
        |value                  |0                          |int            |
        |tag                    |ZERO9VALUE                 |string         |

        And a value bundle which moves funds back and forth from an address is generated referencing the previous transaction with:
        |keys                   |values                     |type           |
        |seed                   |THE_BANK                   |staticList     |
        |value                  |100                        |int            |  
        |tag                    |FAKE9VALUE                 |string         |

        And a transaction is issued referencing the previous transaction
        |keys                   |values                     |type           |
        |seed                   |THE_BANK                   |staticList     |
        |address                |TEST_ADDRESS               |staticValue    |
        |value                  |11                         |int            |
        |tag                    |VALUE9TRANSACTION          |string         |

        #In the default test, the latest sent index will be 52. The next milestone issued should be 53.
        Then a milestone is issued with index 53 and references:
        |keys                   |values                     |type           |
        |transactions           |previousTransaction        |responseValue  |

        #Give the node time to solidify the milestone
        And we wait "15" second/seconds
        
        When "getBalances" is called on "nodeA-m3" with:
        |keys                   |values                     |type           |
        |addresses              |FAKE_SPEND_ADDRESSES       |staticList     |

        Then the response for "getBalances" should return with:
        |keys                   |values                     |type           |
        |balances               |0                          |int            | 

    Scenario: Double spend only affects the ledger once
        We want to ascertain that ledger state is always calculated correctly.
        Even in the presence of double spend, the confirmed state should have spent only once

        Given "1" transaction is issued on "nodeA-m3" with:
        |keys                   |values                     |type           |
        |address                |TEST_ADDRESS               |staticValue    |
        |value                  |0                          |int            |
        |tag                    |ZERO9VALUE                 |string         |

        And a double spend is generated referencing the previous transaction with:
        |keys                   |values                     |type           |
        |seed                   |DOUBLE_SPEND_SEED          |staticValue    |
        |value                  |1000                       |int            |
        |tag                    |FAKE9VALUE                 |string         |

        #In the default test, the latest sent index will be 53. The next milestone issued should be 54.
        Then a milestone is issued with index 54 and references:
        |keys                   |values                     |type           |
        |transactions           |firstDoubleSpend           |responseValue  |

        #Give the node time to solidify the milestone
        And we wait "15" second/seconds
        
        When "getBalances" is called on "nodeA-m3" with:
        |keys                   |values                     |type           |
        |addresses              |DOUBLE_SPEND_ADDRESSES     |staticList     |

        Then the response for "getBalances" should return with:
        |keys                   |values                     |type           |
        |balances               |1000 0                     |intList        | 