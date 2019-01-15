from aloe import world, step
from util.test_logic import api_test_logic as api_utils
from util.response_logic import response_handling as response_handling

import logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

world.test_vars = {}


@step(r'the "([^"]*)" parallel call should return with:')
def compare_thread_return(step, api_call):
    """
    Compares the response of a particular asynchronous API call with the expected list of response keys.

    :param api_call: The API call that you would like to check the response of
    :param step.hashes: A gherkin table outlining any arguments that are expected to be in the response
    """

    # Prepare response list for comparison
    response_list = world.responses[api_call][world.config['nodeId']]
    # Exclude duration from response list
    if 'duration' in response_list:
        del response_list['duration']
    response_keys = response_list.keys()

    # Prepare expected values list for comparison
    expected_values = {}
    args = step.hashes
    api_utils.prepare_options(args,expected_values)
    keys = expected_values.keys()

    # Confirm that the lists are of equal length before comparing
    assert len(keys) == len(response_keys), "Response: {} does not contain""\
                                            ""the same number of arguments: {}".format(keys,response_keys)

    for count in range(len(keys)):
        response_key = response_keys[count]
        response_value = response_list[response_key]
        expected_value = expected_values[response_key]

        assert response_value == expected_value, "Returned: {} does not match the expected"" \
        ""value: {}".format(response_value, expected_value)

    logger.info('Responses match')


@step(r'a response for "([^"]*)" should exist')
def response_exists(step,apiCall):
    """
    Determines if a response for a given API call exists in the world environment. If it doesn't, that means that the
    call was not completed correctly, and contains no data to be extracted.

    :param apiCall: The API call you would like to see exists.
    """
    response = world.responses[apiCall][world.config['nodeId']]
    empty_values = {}
    for key in response:
        if key != 'duration':
            is_empty = api_utils.check_if_empty(response[key])
            if is_empty:
                empty_values[key] = response[key]

    assert len(empty_values) == 0, "There was an empty value in the response {}".format(empty_values)


@step(r'the response for "([^"]*)" should return with:')
def check_response_for_value(step, api_call):
    """
    Compares the response of a particular API call with the expected list of response keys.

    :param api_call: The API call that you would like to check the response of
    :param step.hashes: A gherkin table outlining any arguments that are expected to be in the response
    """
    response_values = world.responses[api_call][world.config['nodeId']]
    expected_values = {}
    args = step.hashes
    api_utils.prepare_options(args, expected_values)

    for expected_value_key in expected_values:
        if expected_value_key in response_values:
            expected_value = expected_values[expected_value_key]
            response_value = response_values[expected_value_key]

            if isinstance(response_value, list):
                response_value = response_value[0]

            assert expected_value == response_value, "The expected value {} does not match"" \
            ""the response value: {}".format(expected_value, response_value)

    logger.info('Response contained expected values')


@step(r'a response with the following is returned:')
def compare_response(step):
    """
    Checks the response value for the latest API call and compares it with a given set of expected keys

    :param step.hashes: The list of keys expected to be found in the response
    """
    logger.info('Validating response')
    keys = step.hashes
    node_id = world.config['nodeId']
    api_call = world.config['apiCall']

    response = world.responses[api_call][node_id]
    key_list = []

    for key in keys:
        key_list.append(key['keys'])

    for key in key_list:
        response_handling.find_in_response(key, response)


@step(r'the returned GTTA transactions will be compared with the milestones')
def compare_gtta_with_milestones(step):
    """Compares the getTransactionsToApprove response values with the collected milestone issuing address"""
    logger.info("Compare GTTA response with milestones")
    gtta_responses = api_utils.fetch_response('getTransactionsToApprove')
    find_transactions_responses = api_utils.fetch_response('findTransactions')
    node = world.config['nodeId']
    milestones = list(find_transactions_responses[node]['hashes'])
    world.config['max'] = len(gtta_responses[node])

    transactions = []
    transactions_count = []
    milestone_transactions = []
    milestone_transactions_count = []
    world.test_vars['milestone_count'] = 0

    for node in gtta_responses:
        if isinstance(gtta_responses[node], list):
            for response in gtta_responses[node]:
                branch_transaction = response['branchTransaction']
                trunk_transaction = response['trunkTransaction']

                compare_responses(branch_transaction, milestones, transactions, transactions_count,
                                  milestone_transactions, milestone_transactions_count)
                compare_responses(trunk_transaction, milestones, transactions, transactions_count,
                                  milestone_transactions, milestone_transactions_count)

        logger.info("Milestone count: " + str(world.test_vars['milestone_count']))

    f = open('blowball_log.txt', 'w')
    for index, transaction in enumerate(transactions):
        transaction_string = 'Transaction: ' + str(transaction) + " : " + \
                             str(transactions_count[index])
        logger.debug(transaction_string)
        f.write(transaction_string + "\n")

    for index, milestone in enumerate(milestone_transactions):
        milestone_string = 'Milestone: ' + str(milestone) + \
                           " : " + str(milestone_transactions_count[index])
        logger.debug(milestone_string)
        f.write(milestone_string + "\n")

    f.close()
    logger.info('Transactions logged in /tests/features/machine3/blowball_logs.txt')


@step(r'less than (\d+) percent of the returned transactions should reference milestones')
def less_than_max_percent(step, max_percent):
    """
    Checks the number of returned milestones and ensures that the total number of milestones returned is below a
    given threshold
    """
    percentage = (float(world.test_vars['milestone_count'])/(world.config['max'] * 2)) * 100.00
    logger.info(str(percentage) + "% milestones")

    assert percentage < float(max_percent), "The returned percentage exceeds {}%".format(float(max_percent))


def compare_responses(value, milestone_list, transaction_list, transaction_counter_list,
                      milestone_transaction_list, milestone_transaction_count):
    """
    A comparison method to check a given response value with a list of collected milestones. It checks if the value is
    present in the milestone list. If it is present in the milestone list, it then determines if this is the first time
    this transaction has been returned, and adjusts the milestone_transaction_list and milestone_transaction_count lists
    accordingly. The overall milestone count is incremented each time a value is found in the milestone list. If the
    value is not present in the milestone list, then it does the same check for the transaction_list and
    transaction_counter_list. These lists will be used later for logging the responses and the number of times a given
    response has been returned.

    :param value: The value you would like to check against the lists
    :param milestone_list: The list of found milestones you will compare the value to
    :param transaction_list: A list of recorded transaction values that have been found
    :param transaction_counter_list: A list of the number of times a value has been returned
    :param milestone_transaction_list: A list of returned milestone values that have been found
    :param milestone_transaction_count: A list of the number of times a milestone value has been returned
    """

    if value in milestone_list:
        if value in milestone_transaction_list:
            milestone_transaction_count[milestone_transaction_list.index(value)] += 1
        else:
            milestone_transaction_list.append(value)
            milestone_transaction_count.append(1)
            logger.debug('added transaction "{}" to milestone list'.format(value))

        world.test_vars['milestone_count'] += 1
        logger.debug('"{}" is a milestone'.format(value))
    else:
        if value in transaction_list:
            transaction_counter_list[transaction_list.index(value)] += 1
        else:
            transaction_list.append(value)
            transaction_counter_list.append(1)
            logger.debug('added transaction "{}" to transaction list'.format(value))
