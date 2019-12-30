package com.iota.iri.service.validation;

import com.iota.iri.conf.MainnetConfig;
import com.iota.iri.conf.ProtocolConfig;
import com.iota.iri.crypto.SpongeFactory;
import com.iota.iri.network.TransactionRequester;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.snapshot.impl.SnapshotMockUtils;
import com.iota.iri.storage.Tangle;
import com.iota.iri.storage.rocksDB.RocksDBPersistenceProvider;
import com.iota.iri.utils.Converter;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.Rule;
import org.junit.AfterClass;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static com.iota.iri.TransactionTestUtils.getTransactionTrits;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TransactionValidatorTest {

  private static final int MAINNET_MWM = 14;
  private static final TemporaryFolder dbFolder = new TemporaryFolder();
  private static final TemporaryFolder logFolder = new TemporaryFolder();
  private static Tangle tangle;
  private static TransactionValidator txValidator;

  @Rule
  public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock
  private static SnapshotProvider snapshotProvider;

  @Mock
  private static TransactionRequester txRequester;

  @BeforeClass
  public static void setUp() throws Exception {
    dbFolder.create();
    logFolder.create();
    tangle = new Tangle();
    tangle.addPersistenceProvider(
        new RocksDBPersistenceProvider(
            dbFolder.getRoot().getAbsolutePath(), logFolder.getRoot().getAbsolutePath(),1000, Tangle.COLUMN_FAMILIES, Tangle.METADATA_COLUMN_FAMILY));
    tangle.init();
  }

  @AfterClass
  public static void tearDown() throws Exception {
    tangle.shutdown();
    dbFolder.delete();
    logFolder.delete();
  }

  @Before
  public void setUpEach() {
    when(snapshotProvider.getInitialSnapshot()).thenReturn(SnapshotMockUtils.createSnapshot());
    txRequester = new TransactionRequester(tangle, snapshotProvider);
    txValidator = new TransactionValidator(snapshotProvider, txRequester, new MainnetConfig());
    txValidator.setMwm(false, MAINNET_MWM);
  }

  @Test
  public void testMinMwm() {
    ProtocolConfig protocolConfig = mock(ProtocolConfig.class);
    when(protocolConfig.getMwm()).thenReturn(5);
    TransactionValidator transactionValidator = new TransactionValidator(null, null, protocolConfig);
    assertEquals("Expected testnet minimum minWeightMagnitude", 13, transactionValidator.getMinWeightMagnitude());
  }

  @Test
  public void validateTrits() {
    byte[] trits = getTransactionTrits();
    Converter.copyTrits(0, trits, 0, trits.length);
    txValidator.validateTrits(trits, MAINNET_MWM);
  }

  @Test(expected = RuntimeException.class)
  public void validateTritsWithInvalidMetadata() {
    byte[] trits = getTransactionTrits();
    txValidator.validateTrits(trits, MAINNET_MWM);
  }

  @Test
  public void validateBytesWithNewCurl() {
    byte[] trits = getTransactionTrits();
    Converter.copyTrits(0, trits, 0, trits.length);
    byte[] bytes = Converter.allocateBytesForTrits(trits.length);
    Converter.bytes(trits, 0, bytes, 0, trits.length);
    txValidator.validateBytes(bytes, txValidator.getMinWeightMagnitude(), SpongeFactory.create(SpongeFactory.Mode.CURLP81));
  }
}
