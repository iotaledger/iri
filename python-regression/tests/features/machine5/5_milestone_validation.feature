Feature: Test milestone validation
    The testnet allows one to set a flag to overlook milestone signature validation. NodeA will have this flag turned on
    while nodeB will have this flag turned off. NodeA should return a solid milestone no.45 while nodeB will return a
    solid milestone no.37.

    Scenario: Verify current milestone index

        Given "getNodeInfo" is called on "nodeA" with:
        |keys                   |values     |type       |

        Then the response for "getNodeInfo" should return with:
        |keys                   |values     |type       |
        |latestMilestoneIndex   |45         |int        |

        And "getNodeInfo" is called on "nodeB" with:
        |keys                   |values     |type       |

        Then the response for "getNodeInfo" should return with:
        |keys                   |values     |type       |
        |latestMilestoneIndex   |37         |int        |
