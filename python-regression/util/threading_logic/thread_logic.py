import threading, queue
from aloe import world


'''
Places the variables into a queue for threadsafe access

@param args:    Variables to be placed in queue, in the order they should be called on 
@return         Returns a populated queue
'''
def populate_queue(*args):
    q = queue.Queue()
    for arg in range(len(args)):
        q.put(args[arg])
    return q

'''
Makes a thread for an api call, and stores the thread in the world environment for later access
'''
def make_thread(function,*args):
    new_thread = threading.Thread(target=function, args=(args))
    new_thread.setDaemon(True)
    new_thread.start()

    apiCall = world.config['apiCall']
    if 'threads' not in world.config:
        world.config['threads'] = {}

    world.config['threads'][apiCall] = new_thread

