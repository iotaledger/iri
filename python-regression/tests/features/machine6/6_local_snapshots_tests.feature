Feature: Test Bootstrapping With LS
  A test to determine whether or not nodes can bootstrap and sync correctly from Local Snapshot Files and DB's. One
  permanode will be started containing all the relevant files/folders for a full sync upon start. Two more nodes will
  be started, connected to this node and one another: One will have only a DB and snapshot file, while the other will
  have only the snapshot meta and state file, along with the spent addresses DB and the snapshot file. All three nodes
  should sync with one another. And a snapshot should be taken on the node started with just a DB.
  [NodeA: Permanode, NodeB: Just DB, NodeC: Just LS Files]

  Scenario: PermaNode is synced
    Check that the permanode has been started correctly and is synced.

    #First make sure nodes are neighbored
    Given "nodeA" and "nodeB" are neighbors
    And "nodeA" and "nodeC" are neighbors

    # Wait to ensure node has time to sync
    When we wait "10" second/seconds
    And "getNodeInfo" is called on "nodeA" with:
      |keys                       |values			|type   	      |

    Then the response for "getNodeInfo" should return with:
      |keys                       |values           |type             |
      |latestMilestoneIndex       |10321            |int              |
      |latestSolidSubtangleIndex  |10321            |int              |


  Scenario: DB node is synced
    Check that the node started with just a DB is synced correctly.

    #First make sure nodes are neighbored
    Given "nodeB" and "nodeA" are neighbors
    And "nodeB" and "nodeC" are neighbors

    # Wait to ensure node has time to sync
    When we wait "10" second/seconds
    And "getNodeInfo" is called on "nodeB" with:
      |keys                       |values			|type   	      |

    Then the response for "getNodeInfo" should return with:
      |keys                       |values           |type             |
      |latestMilestoneIndex       |10321            |int              |
      |latestSolidSubtangleIndex  |10321            |int              |


  Scenario: LS File node is synced
  Check that the node started with just LS Files is synced correctly.

    #First make sure nodes are neighbored
    Given "nodeC" and "nodeA" are neighbors
    And "nodeC" and "nodeB" are neighbors

    # Wait to ensure node has time to sync
    When we wait "10" second/seconds
    And "getNodeInfo" is called on "nodeC" with:
      |keys                       |values			|type   	      |

    Then the response for "getNodeInfo" should return with:
      |keys                       |values           |type             |
      |latestMilestoneIndex       |10321            |int              |
      |latestSolidSubtangleIndex  |10321            |int              |
