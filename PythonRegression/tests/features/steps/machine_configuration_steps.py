from aloe import before,world, after
from yaml import load, Loader

#Configuration
@before.each_feature
def configuration(feature):
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
    world.config = {}
    world.responses = {}

@after.each_example
def deconfiguration(scenario,outline,steps):
    machine = world.machine
    for key in world.__dict__:
        setattr(world,key,{})

    world.machine = machine

