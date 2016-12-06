package com.iota.iri;

import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import com.iota.iri.model.Hash;
import com.iota.iri.model.Transaction;
import com.iota.iri.service.storage.StorageAddresses;
import com.iota.iri.service.storage.StorageTransactions;

public class IotaTest {

	@Test
	public void shouldAddress() throws IOException {
		StorageAddresses storage = StorageAddresses.instance();
		storage.init();
		long p = storage.addressPointer((new Hash("UBMJSEJDJLPDDJ99PISPI9VZSWBWBPZWVVFED9EDXSU9BHQHKMBMVURSZOSBIXJ9MBEOHVDPV9CWV9ECF")).bytes());
		System.err.println(p);
		storage.shutdown();
	}
	
	@Test
	public void shouldTrytes() throws IOException {
		StorageTransactions.instance().init();
		String hash = "OAATQS9VQLSXCLDJVJJVYUGONXAXOFMJOZNSYWRZSWECMXAQQURHQBJNLD9IOFEPGZEPEMPXCIVRX9999";
		final Transaction transaction = StorageTransactions.instance()
			.loadTransaction((new Hash(hash)).bytes());
		System.err.println(transaction);
		StorageTransactions.instance().shutdown();
	}
	
	@Test
	public void test() {
		final String address = (StringUtils.repeat('9', 81));
		System.err.println(address);
	}
}
