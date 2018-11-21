from aloe import before,world, after
from yaml import load, Loader


# Configuration
@before.each_feature
def configuration(feature):
    machine = []   
         
    yaml_path = './output.yml'
    stream = open(yaml_path,'r')
    yaml_file = load(stream,Loader=Loader)
    world.seeds = yaml_file.get('seeds')
    
    nodes = {}
    keys = yaml_file.keys()
    for key in keys:
        if key != 'seeds' and key != 'defaults':
            nodes[key] = yaml_file[key]

        machine = nodes
          
    world.machine = machine
    world.config = {}
    world.responses = {}


@after.each_example
def deconfiguration(scenario, outline, steps):

    machine = world.machine
    for key in world.__dict__:
        setattr(world, key, {})

    world.machine = machine

