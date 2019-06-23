from __future__ import print_function
import sys
from flask import Flask, request
import sh
import json
shcmd = sh.Command("/bin/sh")

app = Flask(__name__)

@app.route('/cluster_deploy', methods=['POST'])
def cluster_deploy():
    rgd = request.get_data()
    print("rgd:",rgd)
    req_json = json.loads(rgd)
    print("req_json:",req_json)
    if req_json is None:
        return 'error'
    if not req_json.has_key(u'topology'):
        print("[ERROR] topology are needed.", file=sys.stderr)
        return 'error'
    topology = req_json[u'topology']
    image_tag = req_json[u'image_tag']
    enableflag = req_json[u'flag']
    print(topology,image_tag,enableflag)
    try:
        log_file = "cluster_deploy.log"
        shcmd('run_experiment.sh', topology, image_tag, enableflag,_out=log_file,_bg=False)
        return 'sucess'
    except Exception:
        return 'error'

@app.route('/stress_experiment', methods=['POST'])
def stress_experiment():
    rgd = request.get_data()
    print("rgd:",rgd)
    req_json = json.loads(rgd)
    print("req_json:",req_json)
    if req_json is None:
        return 'error'
    if not req_json.has_key(u'topology'):
        print("[ERROR] topology are needed.", file=sys.stderr)
        return 'error'
    topology = req_json[u'topology']
    ptype = req_json[u'type']
    if topology == u'all_topology':
        try:
            enableflag = req_json[u'flag']
            image_tag = req_json[u'image_tag']
            print(topology, image_tag, enableflag,ptype)
            file_log = "stress_test_total.log"
            shcmd('run_test.sh',image_tag,enableflag,ptype,_out=file_log,_bg=True)
            return 'sucess'
        except Exception:
            return 'false'
    else:
        try:
            exp_data = req_json[u'stress_data']
            print("stress test begin")
            file_log = "stress_test.log"
            print("input param %s %s %s"%(topology,exp_data,ptype))
            shcmd('run_stress_test.sh',topology,exp_data,ptype,_out=file_log,_bg=True)
            return 'sucess'
        except Exception:
            return 'false'


if __name__ == '__main__':
    app.run(host='127.0.0.1', port=8080)
