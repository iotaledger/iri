package com.iota.iri.pluggables.tee;

import java.util.*;

public class BatchTee {
    int tee_num;
    List<TEE> tee_content;

    public String getDigetst() {
        String ret = "";
        for(TEE tee : tee_content) {
            ret += tee.getDigetst();
        }
        return ret;
    }
}
