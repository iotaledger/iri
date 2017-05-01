package com.iota.iri.storage;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.model.Transaction;
import com.iota.iri.utils.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.iota.iri.controllers.TransactionViewModel.TRINARY_SIZE;

/**
 * Created by paul on 4/18/17.
 */
public class FileExportProvider implements PersistenceProvider {
    private static final Logger log = LoggerFactory.getLogger(FileExportProvider.class);

    @Override
    public void init() throws Exception {

    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public void shutdown() {

    }

    @Override
    public boolean save(Object o) throws Exception {
        return false;
    }

    @Override
    public void delete(Object o) throws Exception {

    }

    @Override
    public boolean update(Object model, String item) throws Exception {
        if(model instanceof Transaction) {
            Transaction transaction = ((Transaction) model);
            if(item.equals("sender") || item.equals("height")) {
                try {
                    PrintWriter writer;
                    Path path = Paths.get(item.equals("sender")? "export": "export-solid", String.valueOf(getFileNumber()) + ".tx");
                    writer = new PrintWriter(path.toString(), "UTF-8");
                    writer.println(transaction.hash.toString());
                    writer.println(Converter.trytes(trits(transaction)));
                    writer.println(transaction.sender);
                    if(item.equals("height")) {
                        long height = transaction.height;
                        //log.debug("Height: " + height);
                        writer.println("Height: " + String.valueOf(height));
                    } else {
                        writer.println("Height: ");
                    }
                    writer.close();
                    return true;
                } catch (UnsupportedEncodingException | FileNotFoundException e) {
                    log.error("File export failed", e);
                } catch (Exception e) {
                    log.error("Transaction load failed. ", e);
                } finally {

                }
            }
        }
        return false;
    }

    @Override
    public boolean exists(Class<?> model, Hash key) throws Exception {
        return false;
    }

    @Override
    public Object latest(Class<?> model) throws Exception {
        return null;
    }

    @Override
    public Object[] keysWithMissingReferences(Class<?> modelClass) throws Exception {
        return new Object[0];
    }

    @Override
    public boolean get(Object model) throws Exception {
        return false;
    }

    @Override
    public boolean mayExist(Object model) throws Exception {
        return false;
    }

    @Override
    public long count(Class<?> model) throws Exception {
        return 0;
    }

    @Override
    public Hash[] keysStartingWith(Class<?> modelClass, byte[] value) {
        return new Hash[0];
    }

    @Override
    public Object seek(Class<?> model, byte[] key) throws Exception {
        return null;
    }

    @Override
    public Object next(Class<?> model, int index) throws Exception {
        return null;
    }

    @Override
    public Object previous(Class<?> model, int index) throws Exception {
        return null;
    }

    @Override
    public Object first(Class<?> model) throws Exception {
        return null;
    }

    private static long lastFileNumber = 0L;
    private static Object lock = new Object();

    public static long getFileNumber() {
        long now = System.currentTimeMillis()*1000;
        synchronized (lock) {
            if (now <= lastFileNumber) {
                return ++lastFileNumber;
            }
            lastFileNumber = now;
        }
        return now;
    }
    int[] trits(Transaction transaction) {
        int[] trits = new int[TRINARY_SIZE];
        if(transaction.bytes != null) {
            Converter.getTrits(transaction.bytes, trits);
        }
        return trits;
    }
}
