import logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(name)s: %(message)s')

def getLogger(name):
    return logging.getLogger(name)