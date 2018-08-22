from aloe import step
from tests.features.steps import api_test_steps
import os 

import logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

logConfig = {}
 
@step(r'create the log directory "([^"]*)"')
def create_log_directory(step,path):
    logger.info('Creating Log directory %s',path)
    logConfig['logDirPath'] = path
    try:
        os.makedirs(path)
        logger.info('Log directory created')
    except:
        logger.info('%s already exists',path)
       
        
@step(r'log the response to the file "([^"]*)"')
def create_log_file(step,fileName):
    logging.info('Attempting to log response in %s',fileName)
    config = setup_logs(fileName)
    file = config[1]
    response = config[0]
        
    for node in response:
        nodeName = node
        responseVals = ""   
        for key in response[node]:
            responseVals += "\t" + key + ": " + str(response[node][key]) + "\n"
        statement = nodeName + ":\n" + responseVals
        logging.debug('Statement to write: %s',statement)
        file.write(statement)      
                  
    logging.info('Response logged')
    file.close()
    
    
    
@step(r'log the duration and responses to the file "([^"]*)"')
def log_gtta_response(step,fileName):
    logConfig['apiCall'] = 'getTransactionsToApprove'

    config = setup_logs(fileName)
    file = config[1]
    response = config[0] 

    durationTotal = 0
    numTests = 0   
    responseVals = ""
    
    for i in response:
        nodeName = i 
        responseVals += nodeName + ":"
        for y in range(len(response[i])):
            numTests = len(response[i])
            keys = response[i][y].keys() 
            responseVals += "\n\n\tResponse {}: ".format(y + 1)
            durationTotal += response[i][y]['duration']
            for x in response[i][y]:
                responseVal = x + ": " + str(response[i][y][x])
                responseVals += "\n\t\t" + responseVal   
                            
        responseVals += "\n\n\tAverage duration: " + str(durationTotal/numTests) + "\n\n"
    
    file.write(responseVals)        
    file.close()






def setup_logs(fileName):
    logging.info('Setting up log file')
    path = logConfig['logDirPath'] + fileName
    file = open(path,'w')
    logging.debug('File path: %s',path)
   
    apiCall = logConfig['apiCall'] 
    logging.info('Fetching %s response', apiCall)
    response = api_test_steps.fetch_response(apiCall)
    logging.debug('API Response: %s', response)
    config = [response,file]
    
    logging.info('Log file and response set up')
    return config


