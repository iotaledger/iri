from aloe import step,world
from yaml import load, Loader



config = {}

#Configuration
@step(r'the node configuration outlined in "([^"]*)"')
def configuration(step,yamlPath):
    stream = open(yamlPath,'r')
    machine1 = load(stream,Loader=Loader)
    config['seeds'] = machine1.get('seeds')
    
    nodes = {}
    keys = machine1.keys()    
    for i in keys:
        if i != 'seeds':
            name = i
            host = machine1[i]['host']
            nodes[name] = host
            
                
    config['nodes'] = nodes
    
@step(r'include this node in the global environment')
def set_up_global(step):
    world.machines = config['nodes']
    world.seeds = config['seeds']
    
