from multiprocessing.dummy import Pool
import logging
logging.basicConfig(level=logging.DEBUG)
logger = logging.getLogger(__name__)


def start_pool(function, iterations, args):
    """
    Starts a pool to run the given function a given number of times.

    :param function: The function you wish to iterate through
    :param iterations: The number of times you would like the function to run
    :param args: The arguments you would like to run the function with

    :return future_results: A list of references to the threads containing the asynchronous call results
    """

    '''Assigns the process worker pool to the number of nodes in the current cluster for the call'''
    pool = Pool(processes=len(args))
    future_results = []
    iteration = 0
    while iteration < int(iterations):
        run_list = (node for node in args if iteration < int(iterations))
        for node in run_list:
            iteration += 1
            arg_list = (node, args[node])
            future_results.append(pool.apply_async(function, arg_list))
    return future_results


def fetch_results(future_result, timeout):
    """
    Tries to fetch the result of the given thread and return it

    :param future_result: The thread reference containing the result of the asynchronous call
    :param timeout: The timeout period in seconds before returning an error
    :return response: The result of the given thread

    :except err: If checking the result fails, it will log the exception
    """
    try:
        response = future_result.get(timeout)
        logger.debug('Response: {}'.format(response))
        return response
    except Exception as err:
        logger.debug(err)
        logger.info(err)
