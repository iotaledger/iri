import logging
from util.threading_logic import pool_logic as pool

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


def find_in_response(key, response):
    """
    Searches through layers of the response to determine if a given key is present.
    Used for api response testing mainly.

    :param key: The key you would like to determine is present in the response
    :param response: The response you would like to find the key in
    """
    is_present = False
    for k in response:
        if k == key:
            is_present = True
            break

        elif isinstance(response[k], dict):
            if key in response[k]:
                is_present = True
                break

        elif isinstance(response[k], list):
            for d in response[k]:
                if not isinstance(d, bool) and key in d:
                    is_present = True
                    break
            if is_present:
                break

    assert is_present is True, '{} does not appear to be present in the response: {}'.format(key, response)


def fetch_future_results(future_results, num_tests, response_vals):
    """
    Fetches the results of a given set of future reference points and stores them in the provided list.

    :param future_results: The list containing the future_result references for response fetching.
    :param num_tests: The maximum number of tests.
    :param response_vals: The list to place the response values in.
    """

    instance = 0
    for result in future_results:
        instance += 1
        if instance % 25 == 0:
            logger.info('Fetching result: {}/{}'.format(instance, num_tests))
        response = pool.fetch_results(result, 30)
        response_vals.append(response)
