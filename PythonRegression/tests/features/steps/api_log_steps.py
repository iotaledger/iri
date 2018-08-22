from aloe import step
from tests.features.steps import api_test_steps
from util.test_logic import api_test_logic
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
        
    for i in response:
        nodeName = i
        responseVals = ""   
        for x in response[i]:
            responseVals += "\t" + x + ": " + str(response[i][x]) + "\n"
        statement = nodeName + ":\n" + responseVals
        logging.debug('Statement to write: %s',statement)
        file.write(statement)      
                  
    logging.info('Response logged')
    file.close()
    
    
@step(r'log the duration and responses to the file "([^"]*)"')
def log_gtta_response(step,fileName):
    logging.info('Attempting to log response in %s',fileName) 
    logConfig['apiCall'] = 'getTransactionsToApprove'




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

