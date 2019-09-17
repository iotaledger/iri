Feature: Test Bootstrapping With LS
  A test to determine whether or not nodes can bootstrap and sync correctly from Local Snapshot Files and DB's. One
  permanode will be started containing all the relevant files/folders for a full sync upon start. Two more nodes will
  be started, connected to this node and one another: One will have only a DB and snapshot file, while the other will
  have only the snapshot meta and state file, along with the spent addresses DB and the snapshot file. All three nodes
  should sync with one another. And a snapshot should be taken on the node started with just a DB.
  [NodeA-m6: Permanode, NodeB-m6: Just DB, NodeC-m6: Just LS Files]

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
      |keys                       |values                   |type                   |
      |address                    |LS_TEST_STATE_ADDRESSES  |staticValue            |

    And reading the local snapshot metadata on "nodeB-m6" returns with:
      |keys                       |values                   |type                   |
      |hashes                     |LS_TEST_MILESTONE_HASHES |staticValue            |


  Scenario: LS File node is synced
    Check that the node started with just LS Files is synced correctly.

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
      |keys                       |values                   |type                   |
      |tails                      |LS_TEST_MILESTONE_HASHES |staticValue            |

    And the response for "checkConsistency" should return with:
      |keys                       |values                   |type                   |
      |state                      |True                     |bool                   |


  Scenario: Old transactions are pruned
    Takes a node with a large db and transaction pruning enabled, and checks to make sure that the transactions below
    the pruning depth are no longer present.

    Given "checkConsistency" is called on "nodeD-m6" with:
      |keys                       |values                   |type                   |
      |tails                      |LS_PRUNED_TRANSACTIONS   |staticValue            |

    Then the response for "checkConsistency" should return null


  Scenario: Spent Addresses are exported and imported correctly
    Using the export-spent tool in the iri-extensions library, the spent addresses of the db will be exported to a file.
    This file will then be checked to make sure the correct values have been exported to it. It will then merge these
    spent addresses into an empty node

    Given the spent addresses are exported from "nodeA-m6"
    When reading the exported spent addresses file on "nodeA-m6" returns with:
      |keys                       |values                   |type                   |
      |addresses                  |SPENT_ADDRESSES          |staticValue            |

    # This part uses a default provided spentAddresses.txt file to import
    And the spent addresses are imported on "nodeF-m6" from:
      |keys                       |values                   |
      |basePath                   |/tmp/                    |
      |file                       |spentAddresses.txt       |

    When "wereAddressesSpentFrom" is called on "nodeF-m6" with:
      |keys                       |values                   |type                   |
      |addresses                  |SPENT_ADDRESSES          |staticValue            |

    Then the response for "wereAddressesSpentFrom" should return with:
      |keys                       |values                   |type                   |
      |states                     |True                     |bool                   |



    Scenario: Spent addresses merged from several inputs
      Using the merge-spent tool in the iri-extensions library, several spent address text files will be merged into
      an empty node

      Given the spent addresses are imported on "nodeG-m6" from:
        |keys                       |values                                         |
        |basePath                   |/cache/iri/python-regression/IXI/merge-spent/  |
        |file                       |MultiSpentAddresses.txt                        |

      And the spent addresses are imported on "nodeG-m6" from:
        |keys                       |values                                         |
        |basePath                   |/cache/iri/python-regression/IXI/merge-spent/  |
        |file                       |MultiSpentAddresses2.txt                       |

      And the spent addresses are imported on "nodeG-m6" from:
        |keys                       |values                                         |
        |basePath                   |/cache/iri/python-regression/IXI/merge-spent/  |
        |file                       |MultiSpentAddresses3.txt                       |

      When "wereAddressesSpentFrom" is called on "nodeG-m6" with:
        |keys                       |values                   |type                 |
        |addresses                  |LATER_SPENT_ADDRESSES    |staticValue          |

      Then the response for "wereAddressesSpentFrom" should return with:
        |keys                       |values                   |type                 |
        |states                     |True                     |bool                 |

