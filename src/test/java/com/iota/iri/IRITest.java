package com.iota.iri;

import static org.junit.Assert.*;

import org.junit.Test;

public class IRITest {

	@Test(expected=IllegalStateException.class)
	public void failedStart1() {
		IRI.main(new String[] {});
	}
	
	@Test(expected=IllegalStateException.class)
	public void failedStart2() {
		IRI.main(new String[] {"1"});
	}

}
