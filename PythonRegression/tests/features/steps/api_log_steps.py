from aloe import step
from tests.features.steps import api_test_steps
import os 

logConfig = {}

@step(r'a response for "([^"]*)" exists')
def api_exists(step,apiCall):
    logConfig['apiCall'] = apiCall
    exists = api_test_steps.check_responses_for_call(apiCall)
    assert exists is True
 
@step(r'create the log directory "([^"]*)"')
def create_log_directory(step,path):
    logConfig['logDirPath'] = path
    try:
        os.makedirs(path)
    except:
        print("Path {} already exists".format(path))
        
@step(r'log the response to the file "([^"]*)"')
def create_log_file(step,fileName):
    config = setup_logs(fileName)
    file = config[1]
    response = config[0]
        
    for i in response:
        nodeName = i
        responseVals = ""   
        for x in response[i]:
            responseVals += "\t" + x + ": " + str(response[i][x]) + "\n"
        statement = nodeName + ":\n" + responseVals
        file.write(statement)        
    
    file.close()
    
      
@step(r'log the neighbor response to the file "([^"]*)"')
def create_neighbor_log_file(step,fileName):
    config = setup_logs(fileName)
    file = config[1]
    response = config[0]
    
    for i in response:
        nodeName = i
        for x in response[i]:
            if type(response[i][x]) != int:
                responseVals = ""
                for y in range(len(response[i][x])):
                    for a in response[i][x][y]: 
                        responseVals += "\t" + a + ": " + str(response[i][x][y][a]) + "\n"
                    responseVals += "\n"
                    
        statement = nodeName + ":\n" + responseVals + "\n\n"
        file.write(statement)


@step(r'log the tips response to the file "([^"]*)"')
def create_tips_log_file(step,fileName):
    config = setup_logs(fileName)
    file = config[1]
    response = config[0]
        
    for i in response:
        nodeName = i
        responseVals = ""   
        for x in response[i]:
            responseVals += "\n\t" + x + ": " 
            if type(response[i][x]) != int:
                    #Maximum 250 entries for the log
                    responseVals += "\n"
                    if len(response[i][x]) > 250:
                        max = 250
                    else: 
                        max = len(response[i][x])
                    
                    for y in range(max):
                        responseVals += "\t\tTip: " + str(response[i][x][y]) + "\n"
                       
            else: 
                responseVals += str(response[i][x])
        statement = nodeName + ":\n" + responseVals
        print(statement)
        
        file.write(statement)        

    file.close()
    







def setup_logs(fileName):
    path = logConfig['logDirPath'] + fileName
    file = open(path,'w')
    apiCall = logConfig['apiCall']
    response = api_test_steps.fetch_response(apiCall)
    config = [response,file]
    
    return config

