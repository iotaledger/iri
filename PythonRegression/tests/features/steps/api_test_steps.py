from aloe import *
from iota import Iota
from iota.commands.core import add_neighbors
from util import static_vals

neighbors = static_vals.TEST_NEIGHBORS
testHash = static_vals.TEST_HASH
testTrytes = static_vals.TEST_TRYTES

config = {}
responses = {'getNodeInfo':{},'getNeighbors':{},'getTips':{},'getTrytes':{}}   

###
#Register API call    
@step(r'"([^"]*)" is called on "([^"]*)"')
def getNodeInfo_is_called(step,apiCall,nodeName):
    config['apiCall'] = apiCall
    config['nodeId'] = nodeName
     
    api = prepare_api_call(nodeName)
    
    if apiCall == 'getNodeInfo':
        response = api.get_node_info()
    elif apiCall == 'getNeighbors':
        response = api.get_neighbors()
    elif apiCall == 'getTips':
        response = api.get_tips()
    elif apiCall == 'getTransactionsToApprove':
        response = api.get_transactions_to_approve(3)
    else:
        response = "Incorrect API call definition"
    
    assert type(response) is dict, "API call did not respond correctly: {}".format(response)
    
    responses[apiCall][nodeName] = response

###
#Response testing    
@step(r'a response with the following is returned:')
def compare_response(step):
    keys = step.hashes
    nodeId = config['nodeId']
    apiCall = config['apiCall']
    
    if apiCall == 'getNodeInfo' or apiCall == 'getTransactionsToApprove':
        response = responses['getNodeInfo'][nodeId]
        responseKeys = list(response.keys())
        responseKeys.sort()
        for i in range(len(response)):
            assert str(responseKeys[i]) == str(keys[i]['keys']), "There was an error with the response" 
    
    elif apiCall == 'getNeighbors' or apiCall == 'getTips':
        response = responses['getNeighbors'][nodeId] 
        responseKeys = list(response.keys())
        for x in range(len(response)):
            try:
                for i in range(len(response[x])):
                    assert str(responseKeys[i]) == str(keys[i])
            except:
                print("No neighbors to verify response with")        
 
 ###
 #Test GetTrytes 

@step(r'getTrytes is called with the hash static_vals.TEST_HASH')
def call_getTrytes(step):
    api = prepare_api_call(config['nodeId'])
    response = api.get_trytes(testHash)
    assert type(response) is dict, "Call may not have responded correctly: \n{}".format(response)
    responses['getTrytes'][config['nodeId']] = response

@step(r'the response should be equal to static_vals.TEST_TRYTES')
def check_trytes(step):
    response = responses['getTrytes'][config['nodeId']]
    if 'trytes' in response:
        assert response['trytes'][0] == testTrytes, "Trytes do not match"


###
#Test Add and Remove Neighbors
  
@step(r'2 neighbors are added with "([^"]*)" on "([^"]*)"')
def add_neighbors(step,apiCall,nodeName):
    config['nodeId'] = nodeName
    api = prepare_api_call(nodeName)
    response = api.add_neighbors(neighbors)
    
@step(r'"getNeighbors" is called, it should return the following neighbors:')
def check_neighbors_post_addition(step):
    containsNeighbor = check_neighbors(step)
    assert containsNeighbor[1] is True
    assert containsNeighbor[0] is True 
    
    
@step(r'"removeNeighbors" will be called to remove the same neighbors')
def remove_neighbors(step):
    api = prepare_api_call(config['nodeId'])
    response = api.remove_neighbors(neighbors)
    
@step(r'"getNeighbors" should not return the following neighbors:')
def check_neighbors_post_removal(step):
    containsNeighbor = check_neighbors(step)
    assert containsNeighbor[1] is False
    assert containsNeighbor[0] is False
            
 
    
    
      
  
  
                                
                    
    
def prepare_api_call(nodeName):
    host = world.machines[nodeName]
    address ="http://"+ host + ":14265"
    api = Iota(address)
    return api


def check_responses_for_call(apiCall):
    if len(responses[apiCall]) > 0:
        return True
    else:
        return False
    
def fetch_response(apiCall):
    return responses[apiCall]


def check_neighbors(step):
    api = prepare_api_call(config['nodeId'])
    response = api.getNeighbors()
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
     
    