from aloe import world
from iota import Iota

import logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)



def prepare_api_call(nodeName):
    logger.info('Preparing api call')
    host = world.machine[nodeName]['host']
    port = world.machine[nodeName]['ports']['api']
    address ="http://"+ host + ":" + port
    api = Iota(address)
    logger.info('API call prepared for %s',address)
    return api


def check_responses_for_call(apiCall):
    steps = import_steps()
    if len(steps.responses[apiCall]) > 0:
        return True
    else:
        return False
    
def fetch_response(apiCall):
    steps = import_steps()
    return steps.responses[apiCall]


def fetch_config(key):
    steps = import_steps()
    return steps.config[key]


def check_neighbors(step,node):
    steps = import_steps()
    api = prepare_api_call(node)
    response = api.getNeighbors()
    logger.info('Response: %s',response)
    containsNeighbor = [False,False]
    
    for i in response:
        expectedNeighbors = step.hashes
        if type(response[i]) != int:
            for x in range(len(response[i])):    
                if expectedNeighbors[0]['neighbors'] == response[i][x]['address']:
                    containsNeighbor[0] = True  
                if expectedNeighbors[1]['neighbors'] == response[i][x]['address']:
                    containsNeighbor[1] = True  
    
    return containsNeighbor

def import_steps():
    import tests.features.steps.api_test_steps as steps
    return steps
     