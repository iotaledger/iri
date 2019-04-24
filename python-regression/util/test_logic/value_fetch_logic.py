from aloe import world
from util import static_vals as static


def fetch_config(key):
    """
    Retrieves a stored configuration object from the aloe.world global variable.
    :param key: The key of the object that will be retrieved
    :return: The stored object
    """
    return world.config[key]


def fetch_response(api_call):
    """
    Retrieves a stored response from the aloe.world global variable.
    :param api_call: The api call key for the response
    :return: The stored response
    """
    return world.responses[api_call]


def fetch_int(value):
    """
    Returns an int representation of the input value.
    :param value: The input value
    :return: The int representation
    """
    return int(value)


def fetch_string(value):
    """
    Returns a string representation of the input value.
    :param value: The input value
    :return: The string representation
    """
    return str(value)


def fetch_list(value):
    """
    Returns the input value as a list.
    :param value: The input value
    :return: The list representation
    """
    return [value]


def fetch_config_value(value):
    """
    Fetches the configuration object referenced by the input value from the stored configuration values.
    :param value: The configuration key to be fetched
    :return: The referenced configuration object
    """
    node = fetch_config('nodeId')
    return world.config[value][node]


def fetch_config_list(value):
    """
    Fetches the configuration object referenced by the input value and returns the object as a list representation.
    :param value: The configuration key to be fetched
    :return: The referenced configuration object in list format
    """
    node = fetch_config('nodeId')
    return [world.config[value][node]]


def fetch_node_address(value):
    """
    Fetches the node address of the given node reference value from the stored machine configuration.
    :param value: The name of the node you wish to pull the address for
    :return: The address of the referenced node in list format
    """
    host = world.machine['nodes'][value]['host']
    port = world.machine['nodes'][value]['ports']['gossip-udp']
    address = "udp://" + host + ":" + str(port)
    return [address.decode()]


def fetch_static_value(value):
    """
    Retrieves the referenced object from the util/static_vals.py file.
    :param value: The reference for the object to be retrieved
    :return: The stored object
    """
    return getattr(static, value)


def fetch_static_list(value):
    """
    Retrieves the referenced object from the util/static_vals.py file and returns it as a list.
    :param value: The reference for the object to be retrieved
    :return: The stored object in list format
    """
    static_value = getattr(static, value)
    return [static_value]


def fetch_bool(value):
    """
    Returns the bool conversion of the input string. The input value should only ever be "True" or "False".
    :param value: The input value
    :return: The proper bool conversion of th input string
    """
    if value == "False":
        return False
    else:
        return True


def fetch_response_value(value):
    """
    Retrieves the response object referenced by the input value from the aloe.world variable.
    :param value: The api_call reference for the response object
    :return: The stored response object
    """
    config = fetch_config('nodeId')
    response = fetch_response(value)
    return response[config]


def fetch_response_list(value):
    """
    Retrieves the response object referenced by the input value from the aloe.world variable and returns it as a list.
    :param value: The api_call reference for the response object
    :return: The stored response object in list format
    """
    config = fetch_config('nodeId')
    response = fetch_response(value)
    return [response[config]]


def fetch_response_value_hashes(value):
    """
    Retrieves the response object referenced by the input value from the aloe.world variable, and returns the 'hashes'
    object from within it.
    :param value: The api_call reference for the response object ['findTransactions' for this particular call]
    :return: The 'hashes' list stored within the response object
    """
    config = fetch_config('nodeId')
    response = fetch_response(value)
    return response[config]['hashes']

