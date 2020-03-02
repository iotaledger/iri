Feature: Test transaction confirmation

    Scenario: Zero Value Transactions are confirmed
        In this test, a number of zero value transactions will be made to a specified node.
        A milestone will be issued that references these transactions, and this should
        confirm the transactions.

        Given "10" transactions are issued on "nodeA-m2" with:
        |keys                   |values                     |type           |
        |address                |TEST_ADDRESS               |staticValue    |
        |value                  |0                          |int            |
        |tag                    |ZERO9VALUE                 |string         |

        #In the default DB, the current index is 50. The next milestone issued should be 51.
        When a milestone is issued with index 51 and references:
        |keys                   |values                     |type           |
        |transactions           |evaluate_and_send          |responseValue  |

        #Give the node 10 seconds to solidify the milestone
        And we wait "10" second/seconds

        Then "getInclusionStates" is called on "nodeA-m2" with:
        |keys                   |values                     |type           |
        |transactions           |evaluate_and_send          |responseValue  |
        |tips                   |latestMilestone            |configValue    |

        And the response for "getInclusionStates" should return with:
        |keys                   |values                     |type           |
        | states | True True True True True True True True True True | boolListMixed |


        When a transaction is generated and attached on "nodeA-m2" with:
        | keys                  | values                    | type          |
        | address               | TEST_ADDRESS              | staticValue   |
        | value                 | 0                         | int           |

        And "getInclusionStates" is called on "nodeA-m2" with:
        | keys                  | values                    | type          |
        | transactions          | TEST_STORE_ADDRESS        | staticList    |
        | tips                  | latestMilestone           | configValue   |

        Then the response for "getInclusionStates" should return with:
        | keys                  | values                    | type          |
        | states                | False                     | boolListMixed |


    Scenario: Value Transactions are confirmed
        In this test, a number of value transactions will be made to a specified node.
        A milestone will be issued that references these transactions, and this should
        confirm the transations.

        Given "10" transactions are issued on "nodeA-m2" with:
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
        And we wait "10" second/seconds

        Then "getInclusionStates" is called on "nodeA-m2" with:
        |keys                   |values                     |type           |
        |transactions           |evaluate_and_send          |responseValue  |
        |tips                   |latestMilestone            |configValue    |

        And the response for "getInclusionStates" should return with:
        |keys                   |values                     |type           |
        | states | True True True True True True True True True False | boolListMixed |

        When a transaction is generated and attached on "nodeA-m2" with:
            | keys    | values       | type        |
            | address | TEST_ADDRESS | staticValue |
            | value   | 0            | int         |

        And "getInclusionStates" is called on "nodeA-m2" with:
            | keys         | values             | type        |
            | transactions | TEST_STORE_ADDRESS | staticList  |
            | tips         | latestMilestone    | configValue |

        Then the response for "getInclusionStates" should return with:
            | keys   | values | type          |
            | states | False  | boolListMixed |


    Scenario: ZMQ receives transaction streams
        Sends a predefined transaction object and a milestone that references it. This should trigger a series
        of zmq stream publications. The responses for these streams are then checked against the expected contents of
        the stream.

        #Subscribe to zmq stream transaction topics
        Given "nodeA-m2" is subscribed to the following zmq topics:
        | keys                  |
        | sn                    |
        | sn_trytes             |
        | tx                    |
        | tx_trytes             |

        Then "storeTransactions" is called on "nodeA-m2" with:
        |keys                   |values                                 |type           |
        |trytes                 |TRANSACTION_TEST_TRANSACTION_TRYTES    |staticList     |

        #In the default test, the latest sent index will be 52. The next milestone issued should be 53.
        When a milestone is issued with index 53 and references:
        |keys                   |values                                 |type           |
        |transactions           |TRANSACTION_TEST_TRANSACTION_HASH      |staticValue    |

        #Give the node time to solidify the milestone
        And we wait "10" second/seconds

        Then the zmq stream for "nodeA-m2" contains a response for following responses:
        | keys                  | values                                | type          |
        |sn                     | TRANSACTION_TEST_TRANSACTION_HASH     | staticValue   |
        |sn_trytes              | TRANSACTION_TEST_TRANSACTION_TRYTES   | staticValue   |
        |tx                     | TRANSACTION_TEST_TRANSACTION_HASH     | staticValue   |
        |tx_trytes              | TRANSACTION_TEST_TRANSACTION_TRYTES   | staticValue   |
