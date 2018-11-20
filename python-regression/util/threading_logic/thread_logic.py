import threading, queue
from aloe import world


def populate_queue(*args):
    """
    Places the variables into a queue for threadsafe access

    :param args: Variables to be placed in queue, in the order they should be called on
    :returns q: Returns a populated queue
    """
    q = queue.Queue()
    for arg in args:
        q.put(args[arg])
    return q


def make_thread(function, *args):
    """Makes a thread for an api call, and stores the thread in the world environment for later access"""
    new_thread = threading.Thread(target=function, args=args)
    new_thread.setDaemon(True)
    new_thread.start()

    api_call = world.config['apiCall']
    if 'threads' not in world.config:
        world.config['threads'] = {}

    world.config['threads'][api_call] = new_thread

