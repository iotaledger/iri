pragma solidity >=0.4.22 <0.6.0;

contract RPS is Console {

    struct Player {
	    uint choice;
	    address addr;
    }
    
    uint num_players = 0;
    uint reward = 10000;
    Player[] public players;
    mapping(string => uint) maps;
    
    function RPS () payable {
        maps["rock"] = 0;
        maps["paper"] = 1;
        maps["scissors"] = 2;
    }

    function add_player() public {
        require(num_players < 2);
        
        Player memory p;
        p.addr = msg.sender;
        p.choice = 0;
        players.push(p);
        num_players++;
    }

    function input(string choice) public {
        uint arrayLength = players.length;
        for (uint i=0; i<arrayLength; i++) {
            if(players[i].addr == msg.sender) {
                uint res = maps[choice];
                log(choice, res);
                players[i].choice = maps[choice];
                break;
            }
        }
    }
    
    function check_winner() public returns(int) {
	    if(players[0].choice==0 && players[1].choice==0) {
	        players[0].addr.transfer(reward/2);
	        players[1].addr.transfer(reward/2);
	    } 
	    else if(players[0].choice==0 && players[1].choice==1) {
	        players[1].addr.transfer(reward);
	    } else if(players[0].choice==0 && players[1].choice==2) {
	        players[0].addr.transfer(reward);
	    } else if(players[0].choice==1 && players[1].choice==0) {
	        players[0].addr.transfer(reward);
	    } else if(players[0].choice==1 && players[1].choice==1) {
	        players[0].addr.transfer(reward);
	        players[1].addr.transfer(reward/2);
	    } else if(players[0].choice==1 && players[1].choice==2) {
	        players[1].addr.transfer(reward);
	    } else if(players[0].choice==2 && players[1].choice==0) {
	        players[1].addr.transfer(reward);
	    } else if(players[0].choice==2 && players[1].choice==1) {
	        players[0].addr.transfer(reward);
	    } else if(players[0].choice==2 && players[1].choice==2) {
	        players[0].addr.transfer(reward/2);
	        players[1].addr.transfer(reward/2);
	    } else {}
    }
}

