package com.iota.iri.service.tipselection;


import com.iota.iri.model.Hash;

public interface EntryPoint{


    Hash getEntryPoint(int depth)throws Exception;

}
