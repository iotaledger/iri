Feature: Test Bootstrapping With LS
  A test to determine whether or not nodes can bootstrap and sync correctly from Local Snapshot DBs and data DBs. One
  permanode will be started containing all the relevant files/folders for a full sync upon start. Two more nodes will
  be started, connected to this node and one another: One will only have a DB and local snapshot DB, while the other will
  only have the local snapshot DB and the snapshot file. All three nodes should sync with one another.
  And a snapshot should be taken on the node started with just a DB.
  [NodeA: Permanode, NodeB: Just DB, NodeC: Just LS DB]

  Scenario: PermaNode is synced
    Check that the permanode has been started correctly and is synced.

    #First make sure nodes are neighbored
    Given "nodeA-m6" and "nodeB-m6" are neighbors
    And "nodeA-m6" and "nodeC-m6" are neighbors

    #Default for test is to issue 10322
    When milestone 10322 is issued on "nodeA-m6"
    And we wait "30" second/seconds
    Then "nodeA-m6" is synced up to milestone 10322


  Scenario: DB node is synced, and files contain expected values
    Check that the node started with just a DB is synced correctly, and that the proper addresses and hashes have been
    stored correctly.

    #First make sure nodes are neighbored
    Given "nodeB-m6" and "nodeA-m6" are neighbors
    And "nodeB-m6" and "nodeC-m6" are neighbors

    # Default for test is to issue 10323
    When milestone 10323 is issued on "nodeA-m6"
    #Give the node time to finish syncing properly, then make sure that the node is synced to the latest milestone.
    And we wait "30" second/seconds
    Then "nodeB-m6" is synced up to milestone 10323
    And A local snapshot was taken on "nodeB-m6" at index 10220

    When reading the local snapshot state on "nodeB-m6" returns with:
      |keys                       |values                   |type             |
      |address                    |LS_TEST_STATE_ADDRESSES  |staticValue      |

    And reading the local snapshot metadata on "nodeB-m6" returns with:
      |keys                       |values                   |type             |
      |hashes                     |LS_TEST_MILESTONE_HASHES |staticValue      |


  Scenario: LS DB node is synced
    Check that the node started with just a LS DB is synced correctly.

    #First make sure nodes are neighbored
    Given "nodeC-m6" and "nodeA-m6" are neighbors
    And "nodeC-m6" and "nodeB-m6" are neighbors

    #Default for test is to issue 10324
    When milestone 10324 is issued on "nodeA-m6"
    #Give the node time to finish syncing properly, then make sure that the node is synced to the latest milestone.
    And we wait "120" second/seconds
    Then "nodeC-m6" is synced up to milestone 10324


  Scenario: Check DB for milestone hashes
    Give the db-less node some time to receive the latest milestones from the permanode, then check if the milestones
    are present in the new node.

    #First make sure nodes are neighbored
    Given "nodeC-m6" and "nodeA-m6" are neighbors
     #Default for test is to issue 10325
    When milestone 10325 is issued on "nodeA-m6"
    And we wait "30" second/seconds

    Then "checkConsistency" is called on "nodeC-m6" with:
      |keys                       |values                   |type             |
      |tails                      |LS_TEST_MILESTONE_HASHES |staticValue      |

    And the response for "checkConsistency" should return with:
      |keys                       |values                   |type             |
      |state                      |True                     |bool             |


  Scenario: Old transactions are pruned
    Takes a node with a large db and transaction pruning enabled, and checks to make sure that the transactions below
    the pruning depth are no longer present.

    Given "checkConsistency" is called on "nodeD-m6" with:
      |keys                       |values                   |type             |
      |tails                      |LS_PRUNED_TRANSACTIONS   |staticValue      |

    Then the response for "checkConsistency" should return null


  Scenario: Check unconfirmed transaction is spent from
    Issues a value transaction that will be unconfirmed, and check that the address was spent from.

    Given a transaction is generated and attached on "nodeE-m6" with:
      |keys                       |values                   |type           |
      |address                    |TEST_ADDRESS             |staticValue    |
      |value                      |10                       |int            |
      |seed                       |UNCONFIRMED_TEST_SEED    |staticValue    |

    When "wereAddressesSpentFrom" is called on "nodeE-m6" with:
      |keys                       |values                   |type             |
      |addresses                  |UNCONFIRMED_TEST_ADDRESS |staticValue      |

    Then the response for "wereAddressesSpentFrom" should return with:
      |keys                       |values                   |type             |
      |addresses                  |True                     |boolList         |


  Scenario: Check addresses spent from after pruning
    Ensures that a node with a spent address registers that the address is spent from both before and after the
    transaction has been pruned from the DB.

    # Check that addresses were spent from before pruning
    Given "wereAddressesSpentFrom" is called on "nodeE-m6" with:
      |keys                       |values                   |type             |
      |addresses                  |LS_SPENT_ADDRESSES       |staticValue      |

    Then the response for "wereAddressesSpentFrom" should return with:
      |keys                       |values                   |type             |
      |addresses                  |True                     |boolList         |

    #Drop the spend transactions below the pruning depth
    When the next 30 milestones are issued

    # Check that addresses were spent after transaction have been pruned
    And "wereAddressesSpentFrom" is called on "nodeE-m6" with:
      |keys                       |values                   |type             |
      |addresses                  |LS_SPENT_ADDRESSES       |staticValue      |

    Then the response for "wereAddressesSpentFrom" should return with:
      |keys                       |values                   |type             |
      |addresses                  |True                     |boolList         |

    # Check that transactions from those addresses were pruned
    And "getTrytes" is called on "nodeE-m6" with:
      |keys                       |values                   |type             |
      |hashes                     |LS_SPENT_TRANSACTIONS    |staticValue      |

    Then the response for "getTrytes" should return with:
      |keys                       |values                   |type             |
      |trytes                     |NULL_TRANSACTION_LIST    |staticValue      |
