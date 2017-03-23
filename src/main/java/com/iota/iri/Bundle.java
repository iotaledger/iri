package com.iota.iri;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.iota.iri.hash.Curl;
import com.iota.iri.hash.ISS;
import com.iota.iri.service.viewModels.BundleViewModel;
import com.iota.iri.service.viewModels.TransactionViewModel;
import com.iota.iri.utils.Converter;

/**
 * A bundle is a group of transactions that follow each other from
 * currentIndex=0 to currentIndex=lastIndex
 * 
 * several bundles can form a single branch if they are chained.
 */
public class Bundle {
}
