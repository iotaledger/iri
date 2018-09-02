from aloe import before,world
from yaml import load, Loader



config = {}

#Configuration
@before.all
def configuration():
    machine = []   
         
    yamlPath = './output.yml'
    stream = open(yamlPath,'r')
    yamlFile = load(stream,Loader=Loader)
    world.seeds = yamlFile.get('seeds')
    
    nodes = {}
    keys = yamlFile.keys()  
    for key in keys:
        if key != 'seeds' and key != 'defaults':
            nodes[key] = yamlFile[key]

        machine = nodes
          
    world.machine = machine
    
