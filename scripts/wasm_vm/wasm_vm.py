import docker
from subprocess import call
import json
from pprint import pprint

class WASMVM(object):

    def __init__(self):
        self.client = docker.from_env()
        self.wasm_img = "eosio_notechain_container"

    # schema:
    # { "contract_name" : "name", "file_names": ["file1", "file2", ...], "contract_addrs" : ["addr1", "addr2", ...]}
    def load_contract_from_ipfs(self, ipfs_addr):
        call(['ipfs', 'get', ipfs_addr])
        with open(ipfs_addr) as data_file:
            data = json.load(data_file)
        call(['mv', ipfs_addr, data['contract_name']+'.json'])
        for i in range(len(data["contract_addrs"])):
            ipfs_file = data["contract_addrs"][i]
            file_name = data["file_names"][i]
            call(['ipfs', 'get', ipfs_file])
            call(['mv', ipfs_file, file_name])
        return data['contract_name']

    # schema:
    # { "contract_name" : "name", "action_name" : "name", "payload" : "payload_content", "privilege" : "priv_info"} 
    def load_action_from_ipfs(self, ipfs_addr):
        call(['ipfs', 'get', ipfs_addr])
        with open(ipfs_addr) as data_file:
            data = json.load(data_file)
        call(['mv', ipfs_addr, data['contract_name']+data['action_name']+'.json'])
        return [data['contract_name']), data['action_name']), data['payload']), data['privilege'])]

    def set_contract(self, contract_name):
        container = self.client.containers.get(self.wasm_img)
        ret = container.exec_run('bash /opt/eosio/bin/scripts/wallet_related.sh')
        if ret.exit_code == 0: 
            # store into docker 
            ret = container.exec_run('mkdir /opt/eosio/bin/contracts/'+contract_name)
            ret = call(['docker','cp',contract_name+'.cpp',self.wasm_img+':/opt/eosio/bin/contracts/'+contract_name])
            # set the contract
            ret = container.exec_run('bash /opt/eosio/bin/scripts/prepare_contract.sh '+contract_name)
            if ret.exit_code == 0:
                return ret 
        return ret

    def exec_action(self, contract_name, action_name, payload, privilege):
        container = self.client.containers.get(self.wasm_img)
        ret = container.exec_run('bash /opt/eosio/bin/scripts/wallet_related.sh')
        if ret.exit_code == 0: 
            # execute the action
            ret = container.exec_run('bash /opt/eosio/bin/scripts/execute_contract.sh '+contract_name + " " + action_name +" "+ payload +" "+ privilege)
            print(ret)
            if ret.exit_code == 0:
                return ret 
        return ret 



vm = WASMVM()
# vm.set_contract("Qmbo3S14QRvVUZ11NVKhmaLfSrND69epxNJMSUiTsHuQC8", "hello")
# vm.exec_action("hello", "hi", '[\\"alice\\"]', "alice@active")
# vm.exec_action("hello", "hi", '["zhaoming"]', "alice@active")
# vm.exec_action("hello", "hi", 'ab', "alice@active")
# vm.load_contract_from_ipfs('QmWpFWxsDdwdBwMFHvieWkquR599VXEAbs1Zqoah1xQx9q')
vm.load_action_from_ipfs('QmNnr2FHshCmLyDLFFDCHcsRdRStF5cziEpjRj7xV5r1mp')
