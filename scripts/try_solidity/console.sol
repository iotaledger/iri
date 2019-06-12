pragma solidity >=0.4.22 <0.6.0;

contract Console {
    event LogUint(string, uint);
    function log(string s , uint x) internal {
        LogUint(s, x);
    }
    
    event LogInt(string, int);
    function log(string s , int x) internal {
        LogInt(s, x);
    }
    
    event LogBytes(string, bytes);
    function log(string s , bytes x) internal {
        LogBytes(s, x);
    }
    
    event LogBytes32(string, bytes32);
    function log(string s , bytes32 x) internal {
        LogBytes32(s, x);
    }

    event LogAddress(string, address);
    function log(string s , address x) internal {
        LogAddress(s, x);
    }

    event LogBool(string, bool);
    function log(string s , bool x) internal {
        LogBool(s, x);
    }
}
