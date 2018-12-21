import docker
from subprocess import call
import json
from pprint import pprint

class WASMVM(object):

    def __init__(self):
        self.client = docker.from_env()
        self.wasm_img = "eosio_notechain_container"

    def set_contract(self, ipfs_addr):
        call(['ipfs', 'get', ipfs_addr])
        call(['mv', ipfs_addr, ipfs_addr+'.tar.gz'])
        ret = call(['sudo','docker','cp',ipfs_addr+'.tar.gz',self.wasm_img+':/opt/eosio/bin/contracts/'])
        ret = call(['sudo','docker','exec', self.wasm_img, 'sh', '-c', '/opt/eosio/bin/prepare_contract.sh %s'%ipfs_addr])
        return ret

    def exec_action(self, ipfs_addr):
        call(['ipfs', 'get', ipfs_addr])
        call(['mv', ipfs_addr, ipfs_addr+".sh"])
        ret = call(['sudo','docker','cp',ipfs_addr+'.sh',self.wasm_img+':/opt/eosio/bin/actions/'])
        ret = call(['sudo','docker','exec', self.wasm_img, 'chmod', '+x', '/opt/eosio/bin/actions/%s.sh'%ipfs_addr])
        ret = call(['sudo','docker','exec', self.wasm_img, 'sh', '-c', '/opt/eosio/bin/actions/%s.sh'%ipfs_addr])
        return ret
