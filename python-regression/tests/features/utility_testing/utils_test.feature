@utilTests
Feature: Test file and directory creation 
	Test the file and directory creation utilities to make sure 
	they functions as intended
	
	@dirTests
	Scenario: Create a test log directory 
		Given a test is started 
		Then a file directory should be created
		And a log directory should be created inside it
		
	@dirTests
	Scenario: Create multiple test log directories
		Given 5 tests are started
		Then a file directory should be created
		And a separate subdirectory should be created for each test
		
	@fileTests
	Scenario: Create and write to file 
		Given the test log directory exists
		Then create a test log file and write "Testing" to it
		And check that the file contains "Testing"
	
	@fileTests
	Scenario: Create and write to several files
		Given 5 test log directories exist
		Then create a log file in each directory
		And Write "Testing" with test tag into each file
		Then check that each file has "Testing" as its contents
		
		