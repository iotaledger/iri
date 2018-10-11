from aloe import *
from iota import ProposedTransaction,Address,Tag,TryteString,ProposedBundle,Transaction

from util import static_vals
from util.test_logic import api_test_logic as api_utils
from time import sleep
import threading
import queue

import logging 
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

testAddress = static_vals.TEST_ADDRESS

world.config = {}
world.responses = {}



'''
This is the general api calling function. There are 3 inputs

@param apiCAll:     The api call that will be requested
@param nodeName:    The name identifying the node you would like to make this request on
@param table:       A gherkin table outlining any arguments needed for the call
                    (See tests/features/machine1/1_api+tests.feature for examples) 

    The table parameter is unique in that there are several input types available depending on the call
    being made. 
    Types:
        string:         Basic string argument, will be taken as is
        int:            Basic integer argument, will be converted to int before call is made
        nodeAddress:    Node name identifier, will create address from node configuration 
        staticValue:    Static name identifier, will fetch value from util/static_vals.py 
        staticList:     Same as staticValue, except it places the results into a list 
        responseValue:  Identifier for api call response value
        responseList:   Same as responseValue, ecept it places the results into a list
        bool:           Bool argument, returns True or False
 
'''
@step(r'"([^"]+)" is called on "([^"]+)" with:')
def api_method_is_called(step,apiCall,nodeName):
    logger.info('%s is called on %s',apiCall,nodeName)
    world.config['apiCall'] = apiCall
    world.config['nodeId'] = nodeName
    arg_list = step.hashes

    options = {}
    api_utils.prepare_options(arg_list, options)

    api = api_utils.prepare_api_call(nodeName)
    response = api_utils.fetch_call(apiCall, api, options)

    assert type(response) is dict, 'There may be something wrong with the response format: {}'.format(response)
    
    world.responses[apiCall] = {}
    world.responses[apiCall][nodeName] = response

#This method is identical to the method above, but creates a new thread
@step(r'"([^"]+)" is called in parallel on "([^"]+)" with:')
def threaded_call(step,apiCall,node):
    logger.info("Creating thread for {}".format(apiCall))
    world.config['apiCall'] = apiCall
    world.config['nodeId'] = node
    arg_list = step.hashes

    options = {}
    api_utils.prepare_options(arg_list, options)
    api = api_utils.prepare_api_call(node)

    def make_call(api,options,q):
        response = api_utils.fetch_call(apiCall, api, options)
        responses = q.get()
        responses[apiCall] = {}
        responses[apiCall][node] = response
        return response

    q = queue.Queue()
    q.put(world.responses)
    new_thread = threading.Thread(target=make_call, args=(api,options,q))
    new_thread.setDaemon(True)
    new_thread.start()

    if 'threads' not in world.config:
        world.config['threads'] = {}

    world.config['threads'][apiCall] = new_thread


@step(r'we wait (\d+) seconds')
def wait_for_step(step,time):
    logger.debug('Waiting for {} seconds'.format(time))
    sleep(int(time))


@step(r'the "([^"]+)" parallel call should return with:')
def compare_thread_return(step,apiCall):
    #Prepare response list for comparison
    logger.debug(world.responses)
    response_list = world.responses[apiCall][world.config['nodeId']]
    #Exclude duration from response list
    if 'duration' in response_list:
        del response_list['duration']
    response_keys = response_list.keys()

    #Prepare expected values list for comparison
    expected_values = {}
    args = step.hashes
    api_utils.prepare_options(args,expected_values)
    keys = expected_values.keys()

    #Confirm that the lists are of equal length before comparing
    assert len(keys) == len(response_keys), 'Response: {} does not contain the same number of arguments: {}'.format(keys,response_keys)

    for count in range(len(keys)):
        response_key = response_keys[count]
        response_value = response_list[response_key]
        expected_value = expected_values[response_key]

        assert response_value == expected_value, \
            'Returned: {} does not match the expected value: {}'.format(response_value,expected_value)

    logger.info('Responses match')


@step(r'GTTA is called (\d+) times on "([^"]+)"')
def spam_call_gtta(step,numTests,node):
    apiCall = 'getTransactionsToApprove' 
    world.config['apiCall'] = apiCall
    world.config['nodeId'] = node
    
    api = api_utils.prepare_api_call(node)
    logging.info('Calls being made to %s',node)
    responseVal = []
    for i in range(int(numTests)):
        logging.debug("Call %d made", i+1)
        response = api.get_transactions_to_approve(depth=3)
        responseVal.append(response)
        
    world.responses[apiCall] = {}
    world.responses[apiCall][node] = responseVal


###
#Transaction Generator
#TODO: Merge Transaction Logic commit to modularise bundle generation
@step(r'a transaction is generated and attached on "([^"]+)" with:')
def generate_transaction_and_attach(step,node):
    arg_list = step.hashes
    world.config['nodeId'] = node
    world.config['apiCall'] = 'attachToTangle'
    options = {}
    api = api_utils.prepare_api_call(node)

    api_utils.prepare_options(arg_list, options)
    addresses = options.get('address')
    value = options.get('value')

    transaction = ProposedTransaction(address=Address(addresses[0]), value = value)

    bundle = ProposedBundle()
    bundle.add_transaction(transaction)
    bundle.finalize()
    trytes = str(bundle[0].as_tryte_string())

    gtta = api.get_transactions_to_approve(depth=3)
    branch = str(gtta['branchTransaction'])
    trunk = str(gtta['trunkTransaction'])

    sent = api.attach_to_tangle(trunk,branch,[trytes],9)
    world.responses['attachToTangle'] = {}
    world.responses['attachToTangle'][node] = sent
    logger.info('Transaction Sent')

    setattr(static_vals, "TEST_STORE_TRANSACTION", sent.get('trytes'))


@step(r'the response for "([^"]+)" should return with:')
def check_response_for_value(step,apiCall):
    response_values = world.responses[apiCall][world.config['nodeId']]
    expected_values = {}
    args = step.hashes
    api_utils.prepare_options(args,expected_values)


    for expected_value_key in expected_values:
        if expected_value_key in response_values:
            expected_value = expected_values[expected_value_key]
            response_value = response_values[expected_value_key]
            if type(response_value) is list:
                response_value = response_value[0]
            assert expected_value == response_value, \
                "The expected value {} does not match the response value: {}".format(expected_value,response_value)

    logger.info('Response contained expected values')



###
#Response testing    
@step(r'a response with the following is returned:')
def compare_response(step):
    logger.info('Validating response')
    keys = step.hashes
    nodeId = world.config['nodeId']
    apiCall = world.config['apiCall']
    
    if apiCall == 'getNodeInfo' or apiCall == 'getTransactionsToApprove':
        response = world.responses[apiCall][nodeId]
        responseKeys = list(response.keys())
        responseKeys.sort()
        logger.debug('Response Keys: %s', responseKeys)
    
        for response_key_val in range(len(response)):
            assert str(responseKeys[response_key_val]) == str(keys[response_key_val]['keys']), "There was an error with the response" 
    
    elif apiCall == 'getNeighbors' or apiCall == 'getTips':
        responseList = world.responses[apiCall][nodeId]
        responseKeys = list(responseList.keys())
        logger.debug('Response Keys: %s', responseKeys)
        
        for responseNumber in range(len(responseList)):
            try:
                for responseKeyVal in range(len(responseList[responseNumber])):
                    assert str(responseKeys[responseKeyVal]) == str(keys[responseKeyVal])
            except:
                logger.debug("No values to verify response with")        

'''
Creates an inconsistent transaction by generating a zero value transaction that references 
a non-existent transaction as its branch and trunk, thus not connecting with any other part 
of the tangle.
'''
#TODO: Merge Transaction Logic commit to modularise bundle generation
@step(r'an inconsistent transaction is generated on "([^"]+)"')
def create_inconsistent_transaction(step,node):
    world.config['nodeId'] = node
    api = api_utils.prepare_api_call(node)
    branch = getattr(static_vals,"NULL_HASH")
    trunk = branch
    trytes = getattr(static_vals,"EMPTY_TRANSACTION_TRYTES")

    transaction = api.attach_to_tangle(trunk,branch,[trytes],14)
    transaction_trytes = transaction.get('trytes')
    api.store_transactions(transaction_trytes)
    transaction_hash = Transaction.from_tryte_string(transaction_trytes[0])
    logger.info(transaction_hash.hash)

    if 'inconsistentTransactions' not in world.responses:
        world.responses['inconsistentTransactions'] = {}

    world.responses['inconsistentTransactions'][node] = transaction_hash.hash



 ###
 #Test GetTrytes 
@step(r'getTrytes is called on "([^"]+)" with the hash ([^"]+)')
def call_getTrytes(step,node,hash):
    api = api_utils.prepare_api_call(node)
    testHash = getattr(static_vals, hash)
    response = api.get_trytes([testHash])
    logger.debug("Call may not have responded correctly: \n%s",response)
    assert type(response) is dict
    world.responses['getTrytes'] = {}
    world.responses['getTrytes'][node] = response


@step(r'the response should be equal to ([^"]+)')
def check_trytes(step,trytes):
    response = world.responses['getTrytes'][world.config['nodeId']]
    testTrytes = getattr(static_vals,trytes)  
    if 'trytes' in response:
        assert response['trytes'][0] == testTrytes, "Trytes do not match"


###
#Test transactions
@step(r'"([^"]+)" and "([^"]+)" are neighbors')
def make_neighbors(step,node1,node2):
    host1 = world.machine['nodes'][node1]['podip']
    port1 = world.machine['nodes'][node1]['clusterip_ports']['gossip-udp']
    host2 = world.machine['nodes'][node2]['podip']
    port2 = world.machine['nodes'][node2]['clusterip_ports']['gossip-udp']
    
    hosts = [host1,host2]
    ports = [port1,port2]
    
    api1 = api_utils.prepare_api_call(node1)
    api2 = api_utils.prepare_api_call(node2)
        
    response1 = api1.get_neighbors()
    response2 = api2.get_neighbors()
    neighbors1 = list(response1['neighbors'])
    neighbors2 = list(response2['neighbors'])
    host = hosts[0]
    port = ports[0]
    address1 = "udp://" + str(host) + ":" + str(port)     
    host = hosts[1]
    port = ports[1]
    address2 = "udp://" + str(host) + ":" + str(port) 
    
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
        
     
@step(r'a transaction with the tag "([^"]+)" is sent from "([^"]+)"')
def send_transaction(step,tag,nodeName):
    logger.debug('Preparing Transaction...')
    logger.debug('Node: %s',nodeName)
    world.config['tag'] = tag
    api = api_utils.prepare_api_call(nodeName)
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
   
   
@step(r'findTransaction is called with the same tag on "([^"]+)"')
def find_transaction_is_called(step,nodeName):
    logger.debug(nodeName)
    api = api_utils.prepare_api_call(nodeName)
    logger.info("Searching for Transaction with the tag: {} on {}".format(world.config['tag'],nodeName))
    response = api.find_transactions(tags=[world.config['tag']])
    world.config['findTransactionResponse'] = response
    
@step(r'the Transaction should be found')
def check_transaction_response(step):
    logger.debug("Checking response...")
    response = world.config['findTransactionResponse']
    assert len(response['hashes']) != 0, 'Transactions not found'
    logger.info("{} Transactions found".format(len(response['hashes'])))  
                                  
## Find Transactions
@step(r'find transaction is called with the address:')
def find_transactions_from_address(step):
    logger.info('Finding milestones')
    node = world.config['nodeId']
    
    api = api_utils.prepare_api_call(node)
    transactions = api.find_transactions(addresses = [step.multiline])
    world.responses['findTransactions'] = transactions


def check_responses_for_call(apiCall):
    if len(world.responses[apiCall]) > 0:
        return True
    else:
        return False
    

def fill_response(apiCall,response):
    world.responses[apiCall] = response

def fill_config(key,value):
    world.config[key] = value

def fetch_config(key):
    return world.config[key]
    
def fetch_response(apiCall):
    return world.responses[apiCall]

'''
This method is used to determine if a node contains the neighbors specified in the steps feature list

@returns a list of two bools 

If the return contains a False response, then the neighbor associated with that bool will be added in the remaining
methods in the step.  
'''
#TODO: Move this function to a utility file along with all other functionality involving neighbors
def check_neighbors(step):
    api = api_utils.prepare_api_call(world.config['nodeId'])
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
     
    
