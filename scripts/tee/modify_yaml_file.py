import socket
import ConfigParser
import yaml
import sys
cf = ConfigParser.ConfigParser()
modify_param = sys.argv[1]

def get_host_ip():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(('8.8.8.8', 80))
        ip = s.getsockname()[0]
    finally:
        s.close()
    return ip

def modify_iota_cli_conf():
    cf.read("conf")
    cf.set('iota','addr','http://'+get_host_ip()+':14700')
    confile = open('conf','wb')
    cf.write(confile)
    cf.close()
def modify_go_yaml():
    with open('config.yaml') as f:
        content = yaml.safe_load(f)
        content.update({'url': 'http://'+get_host_ip()+':14700'})
    with open('config.yaml', 'w') as nf:
        yaml.dump(content, nf,default_flow_style=False)
    f.close()
    nf.close()

if __name__ == '__main__':
    if "modify_go_yaml" in modify_param:
        modify_go_yaml()
    elif "modify_iota_cli_conf" in modify_param:
        modify_iota_cli_conf()
