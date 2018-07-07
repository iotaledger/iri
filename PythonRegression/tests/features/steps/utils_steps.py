from aloe import step, world
from util.file_handling import directory_handling,file_handling
from util.logging import file_editing
import os

dir = directory_handling.DirectoryHandling()
file = file_handling.FileHandling()
edit = file_editing.FileEditing()

testVar = {'key':'value'}


@step(r'a test is started')
def test_started(step):
    testVar.update({'testdir':"./single_test_log"})
    testVar.update({'logdir':"./test%dLogs" % 1})
 
@step(r'a file directory should be created')    
def file_directory_created(step):
    dir.make_directory(testVar['testdir'])
    
@step(r'a log directory should be created inside it')    
def log_directory_created(step):
    dir.change_directory(testVar['testdir'])
    dir.make_directory(testVar['logdir'])
    dir.change_directory("../") 

 
##Scenario 2 
@step(r'(\d+) tests are started')   
def tests_started(step,number):
    testVar["testdir"] = "./multiple_test_logs"
    testVar["logdir"] = []
    max = int(number)
    testVar["numTests"] = max

    for i in range(max):
        lognum = i + 1
        testVar['logdir'].append("./test%dLogs" % lognum)

@step(r'a separate subdirectory should be created for each test')        
def log_directories_created(step):
    dir.change_directory(testVar['testdir'])
    max = len(testVar['logdir'])
    for i in range(max):
        dir.make_directory(testVar['logdir'][i])
            
    dir.change_directory("../") 
  
  
##Scenario 3  
@step(r'the test log directory exists')    
def test_log_exists(step):
    testVar["dirLoc"] = './single_test_log/test1Logs'
    exists = os.path.isdir(testVar['dirLoc'])
    assert exists is True

@step(r'create a test log file and write "([^"]*)" to it')
def create_test_file(step,content):
    print(testVar)
    dir.change_directory(testVar['dirLoc'])
    testFile = file.make_file('Test1')
    testVar['testFile'] = []
    testVar['testFile'[0]] = testFile
    edit.write_to_file(testVar['testFile'[0]], content)
    file.close_file(testVar['testFile'[0]])
 
         
@step(r'check that the file contains "([^"]*)"')         
def check_file(step,output):
    testFile = file.open_file_read('Test1')
    testVar['testFile'[0]] = testFile
    fileContents = edit.read_from_file(testVar['testFile'[0]])
    file.close_file(testVar['testFile'[0]])
    dir.change_directory("../../")
    assert fileContents == output, 'fileContents = {} \nOutput = {}'.format(fileContents,output) 


##Scenario 4 
@step(r'(\d+) test log directories exist')
def log_directories_exist(step,num):
    logDirs = []
    testVar['numTests'] = int(num)
    for i in range(testVar['numTests']):
        logNum = i+1
        logDirs.append('./multiple_test_logs/test%dLogs' % logNum) 
    
    testVar['logDirs'] = logDirs
    
    for i in range(len(testVar['logDirs'])):
        exists = os.path.exists(testVar['logDirs'][i])
        assert exists == True, "Path doesn't exist {}".format(testVar['logDirs'][i])
        
@step(r'create a log file in each directory')  
def create_log_directories(step):
    logFiles = []
    for i in range(len(testVar['logDirs'])):
        logFile = testVar['logDirs'][i] + "/TestLog"
        logFiles.append(logFile)
    
    testVar['logFiles'] = logFiles
    logFilesId = []
    assert len(testVar['logFiles']) > 0, "logFiles is empty {}".format(testVar['logFiles'])    
    
    for i in range(len(testVar['logFiles'])):
        logFileId = file.make_file(testVar['logFiles'][i])
        logFilesId.append(logFileId)
        exists = os.path.exists(testVar['logFiles'][i])
        assert exists is True, "Log file not created"

    testVar['logFilesId'] = logFilesId 

@step(r'write "([^"]*)" with test tag into each file')        
def write_to_log_files(step,content):
    for i in range(len(testVar['logFilesId'])):
        edit.write_to_file(testVar['logFilesId'][i], content) 
        file.close_file(testVar['logFilesId'][i])
 
@step(r'check that each file has "([^"]*)" as its contents')        
def check_log_files(step,output):
    for i in range(len(testVar['logFiles'])):
        logFile = file.open_file_read(testVar['logFiles'][i])
        lines = edit.read_from_file(logFile)
        assert lines == output, "Lines = {}\nOutput = {}".format(lines,output) 
        
        
