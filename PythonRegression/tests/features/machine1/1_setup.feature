Feature: Set up machine 1 configuration	
	
	Scenario: Configure machine 1
		Given the node configuration outlined in "./tests/features/machine1/config.yml" 
		Then include this node in the global environment