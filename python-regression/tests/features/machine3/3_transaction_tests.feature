Feature: Test transaction confirmation

    Scenario: Zero Value Transactions are confirmed
        In this test, a number of zero value transactions will be made to a specified node.
        A milestone will be issued that references these transactions, and this should
        confirm the transations.

        Given "10" transactions are issued on "nodeA-m3" with:
        |keys                   |values                     |type           |
        |address                |TEST_ADDRESS               |staticValue    |
        |value                  |0                          |int            |
        |tag                    |ZERO9VALUE                 |string         |

        #In the default DB, the current index is 50. The next milestone issued should be 51.
        When a milestone is issued with index 51 and references:
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
        confirm the transations.

        Given "10" transactions are issued on "nodeA-m3" with:
        |keys                   |values                     |type           |
        |seed                   |THE_BANK                   |staticList     |
        |address                |TEST_ADDRESS               |staticValue    |
        |value                  |10                         |int            |
        |tag                    |VALUE9TRANSACTION          |string         |

        #In the default test, the latest sent index will be 51. The next milestone issued should be 52.
        When a milestone is issued with index 52 and references:
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
        | states | True True True True True True True True True False | boolListMixed |

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

