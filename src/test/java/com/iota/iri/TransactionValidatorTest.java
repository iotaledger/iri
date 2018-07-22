package com.iota.iri;

import com.iota.iri.conf.Configuration;
import com.iota.iri.controllers.TipsViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.hash.SpongeFactory;
import com.iota.iri.model.Hash;
import com.iota.iri.network.TransactionRequester;
import com.iota.iri.storage.Tangle;
import com.iota.iri.storage.rocksDB.RocksDBPersistenceProvider;
import com.iota.iri.utils.Converter;
import com.iota.iri.zmq.MessageQ;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static com.iota.iri.controllers.TransactionViewModelTest.getRandomTransactionTrits;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Created by paul on 5/14/17. */
public class TransactionValidatorTest {

  private static final int MAINNET_MWM = 14;
  private static final TemporaryFolder dbFolder = new TemporaryFolder();
  private static final TemporaryFolder logFolder = new TemporaryFolder();
  private static Tangle tangle;
  private static TransactionValidator txValidator;

  @BeforeClass
  public static void setUp() throws Exception {
    dbFolder.create();
    logFolder.create();
    tangle = new Tangle();
    tangle.addPersistenceProvider(
        new RocksDBPersistenceProvider(
            dbFolder.getRoot().getAbsolutePath(), logFolder.getRoot().getAbsolutePath(),1000));
    tangle.init();
    TipsViewModel tipsViewModel = new TipsViewModel();
    MessageQ messageQ = new MessageQ(0, "", 0, false);
    TransactionRequester txRequester = new TransactionRequester(tangle, messageQ);
    txValidator = new TransactionValidator(tangle, tipsViewModel, txRequester, messageQ,
            Long.parseLong(Configuration.GLOBAL_SNAPSHOT_TIME));
    txValidator.init(false, MAINNET_MWM);
  }

  @AfterClass
  public static void tearDown() throws Exception {
    txValidator.shutdown();
    tangle.shutdown();
    dbFolder.delete();
    logFolder.delete();
  }

  @Test
  public void testMinMwm() throws InterruptedException {
    txValidator.shutdown();
    txValidator.init(false, 5);
    assertTrue(txValidator.getMinWeightMagnitude() == 13);
    txValidator.shutdown();
    txValidator.init(false, MAINNET_MWM);
  }

  @Test
  public void validateBytes() throws Exception {
    byte[] trits = getRandomTransactionTrits();
    Converter.copyTrits(0, trits, 0, trits.length);
    byte[] bytes = Converter.allocateBytesForTrits(trits.length);
    Converter.bytes(trits, bytes);
    TransactionValidator.validateBytes(bytes, MAINNET_MWM);
  }

  @Test
  public void validateTrits() {
    byte[] trits = getRandomTransactionTrits();
    Converter.copyTrits(0, trits, 0, trits.length);
    TransactionValidator.validateTrits(trits, MAINNET_MWM);
  }

  @Test(expected = RuntimeException.class)
  public void validateTritsWithInvalidMetadata() {
    byte[] trits = getRandomTransactionTrits();
    TransactionValidator.validateTrits(trits, MAINNET_MWM);
  }

  @Test
  public void validateBytesWithNewCurl() throws Exception {
    byte[] trits = getRandomTransactionTrits();
    Converter.copyTrits(0, trits, 0, trits.length);
    byte[] bytes = Converter.allocateBytesForTrits(trits.length);
    Converter.bytes(trits, 0, bytes, 0, trits.length);
    TransactionValidator.validateBytes(bytes, txValidator.getMinWeightMagnitude(), SpongeFactory.create(SpongeFactory.Mode.CURLP81));
  }

  @Test
  public void verifyTxIsSolid() throws Exception {
    TransactionViewModel tx = getTxWithBranchAndTrunk();
    assertTrue(txValidator.checkSolidity(tx.getHash(), false));
    assertTrue(txValidator.checkSolidity(tx.getHash(), true));
  }

  @Test
  public void verifyTxIsNotSolid() throws Exception {
    TransactionViewModel tx = getTxWithoutBranchAndTrunk();
    assertFalse(txValidator.checkSolidity(tx.getHash(), false));
    assertFalse(txValidator.checkSolidity(tx.getHash(), true));
  }

  @Test
  public void addSolidTransactionWithoutErrors() {
    byte[] trits = getRandomTransactionTrits();
    Converter.copyTrits(0, trits, 0, trits.length);
    txValidator.addSolidTransaction(Hash.calculate(SpongeFactory.Mode.CURLP81, trits));
  }

  private TransactionViewModel getTxWithBranchAndTrunk() throws Exception {
    TransactionViewModel tx, trunkTx, branchTx;
    String trytes = "999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999CFDEZBLZQYA9999999999999999999999999999999999999999999ZZWQHWD99C99999999C99999999CKWWDBWSCLMQULCTAAJGXDEMFJXPMGMAQIHDGHRBGEMUYNNCOK9YPHKEEFLFCZUSPMCJHAKLCIBQSGWAS999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999";

    byte[] trits = Converter.allocateTritsForTrytes(trytes.length());
    Converter.trits(trytes, trits, 0);
    trunkTx = new TransactionViewModel(trits, Hash.calculate(SpongeFactory.Mode.CURLP81, trits));
    branchTx = new TransactionViewModel(trits, Hash.calculate(SpongeFactory.Mode.CURLP81, trits));

    byte[] childTx = getRandomTransactionTrits();
    System.arraycopy(trunkTx.getHash().trits(), 0, childTx, TransactionViewModel.TRUNK_TRANSACTION_TRINARY_OFFSET, TransactionViewModel.TRUNK_TRANSACTION_TRINARY_SIZE);
    System.arraycopy(branchTx.getHash().trits(), 0, childTx, TransactionViewModel.BRANCH_TRANSACTION_TRINARY_OFFSET, TransactionViewModel.BRANCH_TRANSACTION_TRINARY_SIZE);
    tx = new TransactionViewModel(childTx, Hash.calculate(SpongeFactory.Mode.CURLP81, childTx));

    trunkTx.store(tangle);
    branchTx.store(tangle);
    tx.store(tangle);

    return tx;
  }

  private TransactionViewModel getTxWithoutBranchAndTrunk() throws Exception {
    byte[] trits = getRandomTransactionTrits();
    TransactionViewModel tx = new TransactionViewModel(trits, Hash.calculate(SpongeFactory.Mode.CURLP81, trits));

    tx.store(tangle);

    return tx;
  }
}
