from aloe import *
from iota import Iota,ProposedTransaction,Address,Tag,TryteString,BundleHash

from util import static_vals
from time import sleep

import logging 
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

neighbors = static_vals.TEST_NEIGHBORS
testHash = static_vals.TEST_HASH
testTrytes = static_vals.TEST_TRYTES
testAddress = static_vals.TEST_ADDRESS

config = {}
responses = {'getNodeInfo':{},'getNeighbors':{},'getTips':{},'getTransactionsToApprove': {},'getTrytes':{}}   


###
#Register API call    
@step(r'"([^"]*)" is called on "([^"]*)"')
def api_method_is_called(step,apiCall,nodeName):
    logger.info('%s is called on %s',apiCall,nodeName)
    config['apiCall'] = apiCall
    config['nodeId'] = nodeName
    responses[apiCall] = {}
        
     
    api = prepare_api_call(nodeName)
        
    logger.debug('Assigning call list...')
        
    callList = {
        'getNodeInfo': api.get_node_info,
        'getNeighbors': api.get_neighbors,
        'getTips': api.get_tips,
        'getTransactionsToApprove': api.get_transactions_to_approve
    }
    
    if apiCall == 'getNodeInfo':
        response = api.get_node_info()
        logger.debug('Node Info Response: %s',response)
    elif apiCall == 'getNeighbors':
        response = api.get_neighbors()
        logger.debug('Neighbor Response: %s',response)
    elif apiCall == 'getTips':
        response = api.get_tips()
        logger.debug('Get Tips Response Error')
    elif apiCall == 'getTransactionsToApprove':
        response = api.get_transactions_to_approve(3)
        logger.debug('Get Transactions To Approve Error')
    else:
        response = "Incorrect API call definition"
    
    
    assert type(response) is dict, response
    
    responses[apiCall] = {}
    responses[apiCall][nodeName] = response



@step(r'GTTA is called (\d+) times on "([^"]*)"')
def spam_call_gtta(step,numTests,node):
    apiCall = 'getTransactionsToApprove' 
    config['apiCall'] = apiCall
    config['nodeId'] = node
    
    api = prepare_api_call(node)
    logging.info('Calls being made to %s',node)
    responseVal = []
    for i in range(int(numTests)):
        logging.debug("Call %d made", i+1)
        response = api.get_transactions_to_approve(depth=3)
        responseVal.append(response)
        
    responses[apiCall] = {}
    responses[apiCall][node] = responseVal

###
#Response testing    
@step(r'a response with the following is returned:')
def compare_response(step):
    logger.info('Validating response')
    keys = step.hashes
    nodeId = config['nodeId']
    apiCall = config['apiCall']
    
    if apiCall == 'getNodeInfo' or apiCall == 'getTransactionsToApprove':
        response = responses[apiCall][nodeId]
        responseKeys = list(response.keys())
        responseKeys.sort()
        logger.debug('Response Keys: %s', responseKeys)
    
        for response_key_val in range(len(response)):
            assert str(responseKeys[response_key_val]) == str(keys[response_key_val]['keys']), "There was an error with the response" 
    
    elif apiCall == 'getNeighbors' or apiCall == 'getTips':
        responseList = responses[apiCall][nodeId] 
        responseKeys = list(responseList.keys())
        logger.debug('Response Keys: %s', responseKeys)
        
        for responseNumber in range(len(responseList)):
            try:
                for responseKeyVal in range(len(responseList[responseNumber])):
                    assert str(responseKeys[responseKeyVal]) == str(keys[responseKeyVal])
            except:
                logger.debug("No values to verify response with")        
 
 ###
 #Test GetTrytes 
@step(r'getTrytes is called with the hash static_vals.TEST_HASH')
def call_getTrytes(step):
    api = prepare_api_call(config['nodeId'])
    logger.info('Testing getTrytes on static transaction')
    nodeId = config['nodeId']
      
    api = prepare_api_call(nodeId)
    response = api.get_trytes(testHash)
    logger.debug("Call may not have responded correctly: \n%s",response)
    assert type(response) is dict 
    responses['getTrytes'] = {}
    responses['getTrytes'][nodeId] = response



@step(r'the response should be equal to static_vals.TEST_TRYTES')
def check_trytes(step):
    logger.info('Validating response')
    nodeId = config['nodeId']  
    
    response = responses['getTrytes'][nodeId]
    if 'trytes' in response:
        assert response['trytes'][0] == testTrytes, "Trytes do not match"



###
#Test Add and Remove Neighbors
@step(r'2 neighbors are added with "([^"]*)" on "([^"]*)"')
def add_neighbors(step,apiCall,nodeName):
    config['nodeId'] = nodeName
    api = prepare_api_call(nodeName)
    response = api.add_neighbors(neighbors)
    logger.debug('Response: %s',response)

    
@step(r'"getNeighbors" is called, it should return the following neighbors:')
def check_neighbors_post_addition(step):
    logger.info('Ensuring Neighbors were added correctly')
    containsNeighbor = check_neighbors(step)
    assert containsNeighbor[1] is True
    assert containsNeighbor[0] is True 
    
    
@step(r'"removeNeighbors" will be called to remove the same neighbors')
def remove_neighbors(step):
    api = prepare_api_call(config['nodeId'])
    response = api.remove_neighbors(neighbors)
    logger.debug('Response: %s',response)
    
@step(r'"getNeighbors" should not return the following neighbors:')
def check_neighbors_post_removal(step):
    logger.info('Ensuring Neighbors were removed correctly')
    containsNeighbor = check_neighbors(step)
    assert containsNeighbor[1] is False
    assert containsNeighbor[0] is False


###
#Test transactions
@step(r'"([^"]*)" and "([^"]*)" are neighbors')
def make_neighbors(step,node1,node2):
    host1 = world.machine[node1]['host']
    port1 = world.machine[node1]['udpPort']
    host2 = world.machine[node2]['host']
    port2 = world.machine[node2]['udpPort']
    
    hosts = [host1,host2]
    ports = [port1,port2]
    
    api1 = prepare_api_call(node1)
    api2 = prepare_api_call(node2)
        
    response1 = api1.get_neighbors()
    response2 = api2.get_neighbors()
    neighbors1 = list(response1['neighbors'])
    neighbors2 = list(response2['neighbors'])
    host = hosts[0]
    port = ports[0]
    address1 = "udp://" + str(host) + ":" + port     
    host = hosts[1]
    port = ports[1]
    address2 = "udp://" + str(host) + ":" + port 
    
    logger.debug("Checking if nodes are paired")
    
    containsNeighbor = False
    for neighbor in range(len(neighbors1)):
        if neighbors1[neighbor]['address']:
            containsNeighbor = True
            logger.debug("Neighbor found")

    
    if containsNeighbor == False:
        api1.add_neighbors([address2.decode()])
        api2.add_neighbors([address1.decode()])
        logger.debug("Nodes paired")
    
        
    containsNeighbor = False
    for neighbor in range(len(neighbors2)):
        if neighbors2[neighbor]['address']:
            containsNeighbor = True 
            logger.debug("Neighbor found")

    if containsNeighbor == False:
        api2.add_neighbors([address1.decode()])
        api1.add_neighbors([address2.decode()]) 
        logger.debug("Nodes paired")
        
        
    response = api1.get_neighbors()
    logger.info(response)
    response = api2.get_neighbors()
    logger.info(response)
        
     
@step(r'a transaction with the tag "([^"]*)" is sent from "([^"]*)"')
def send_transaction(step,tag,nodeName):
    logger.debug('Preparing Transaction...')
    logger.debug('Node: %s',nodeName)
    config['tag'] = tag
    api = prepare_api_call(nodeName)  
    txn = \
        ProposedTransaction(
            address = 
            Address(testAddress),
            message = TryteString.from_unicode('Test Transaction propagation'),
            tag = Tag(tag),
            value = 0,
            )
    
    logger.info("Sending Transaction with tag '{}' to {}...".format(tag,nodeName))
    txn_sent = api.send_transfer(depth=3, transfers=[txn])
    logger.debug("Giving the transaction time to propagate...")
    sleep(10)
   
   
@step(r'findTransaction is called with the same tag on "([^"]*)"')
def find_transaction_is_called(step,nodeName):
    logger.debug(nodeName)
    api = prepare_api_call(nodeName)    
    logger.info("Searching for Transaction with the tag: {} on {}".format(config['tag'],nodeName))
    response = api.find_transactions(tags=[config['tag']])    
    config['findTransactionResponse'] = response
    
@step(r'the Transaction should be found')
def check_transaction_response(step):
    logger.debug("Checking response...")
    response = config['findTransactionResponse']
    assert len(response['hashes']) != 0, 'Transactions not found'
    logger.info("{} Transactions found".format(len(response['hashes'])))  
                                  
                    
    
def prepare_api_call(nodeName):
    host = world.machine['nodes'][nodeName]['host']
    address ="http://"+ host + ":14265"
    api = Iota(address)
    logger.info('API call prepared for %s',address)
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
     
    