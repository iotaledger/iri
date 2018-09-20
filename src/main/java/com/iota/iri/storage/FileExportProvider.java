package com.iota.iri.storage;

import com.iota.iri.model.Transaction;
import com.iota.iri.utils.Converter;
import com.iota.iri.utils.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.iota.iri.controllers.TransactionViewModel.TRINARY_SIZE;

/**
 * Created by paul on 4/18/17.
 */
public class FileExportProvider implements PersistenceProvider {
    private static final Logger log = LoggerFactory.getLogger(FileExportProvider.class);
    private static long lastFileNumber = 0L;
    private final static Object lock = new Object();

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
    public boolean save(Persistable model, Indexable index) throws Exception {
        return false;
    }

    @Override
    public void delete(Class<?> model, Indexable index) throws Exception {

    }

    @Override
    public boolean update(Persistable model, Indexable index, String item) {

        if (item == null || !item.contains("sender")) {
            log.error("Item does not contain sender: {}", item);
            return false;
        }
        if (!(model instanceof Transaction)) {
            log.error("Model is not instance of Transaction");
            return false;
        }

        // create export folder and export file
        File exportFile = new File("export", String.valueOf(getFileNumber()) + ".tx");
        if(!exportFile.getParentFile().exists() && exportFile.getParentFile().mkdirs()) {
            log.error("Export folder can not be created");
            return false;
        }

        // print data to file
        Transaction transaction = ((Transaction) model);
        try(PrintWriter writer = new PrintWriter(exportFile, StandardCharsets.UTF_8.name())) {
            writer.println(index.toString());
            writer.println(Converter.trytes(trits(transaction)));
            writer.println(transaction.sender);
            writer.println("Height: ");

            return true;
        } catch (UnsupportedEncodingException | FileNotFoundException e) {
            log.error("File export failed", e);
        } catch (Exception e) {
            log.error("Transaction load failed", e);
        }
        return false;
    }

    @Override
    public boolean exists(Class<?> model, Indexable key) throws Exception {
        return false;
    }

    @Override
    public Pair<Indexable, Persistable> latest(Class<?> model, Class<?> indexModel) throws Exception {
        return null;
    }

    @Override
    public Set<Indexable> keysWithMissingReferences(Class<?> modelClass, Class<?> other) throws Exception {
        return null;
    }

    @Override
    public Persistable get(Class<?> model, Indexable index) throws Exception {
        return null;
    }

    @Override
    public boolean mayExist(Class<?> model, Indexable index) throws Exception {
        return false;
    }

    @Override
    public long count(Class<?> model) throws Exception {
        return 0;
    }

    @Override
    public Set<Indexable> keysStartingWith(Class<?> modelClass, byte[] value) {
        return null;
    }

    @Override
    public Persistable seek(Class<?> model, byte[] key) throws Exception {
        return null;
    }

    @Override
    public Pair<Indexable, Persistable> next(Class<?> model, Indexable index) throws Exception {
        return null;
    }

    @Override
    public Pair<Indexable, Persistable> previous(Class<?> model, Indexable index) throws Exception {
        return null;
    }

    @Override
    public Pair<Indexable, Persistable> first(Class<?> model, Class<?> indexModel) throws Exception {
        return null;
    }

    public boolean merge(Persistable model, Indexable index) throws Exception {
        return false;
    }

    @Override
    public boolean saveBatch(List<Pair<Indexable, Persistable>> models) throws Exception {
        return false;
    }

    @Override
    public void deleteBatch(Collection<Pair<Indexable, ? extends Class<? extends Persistable>>> models) throws Exception {

    }

    @Override
    public void clear(Class<?> column) throws Exception {

    }

    @Override
    public void clearMetadata(Class<?> column) throws Exception {

    }

    private static long getFileNumber() {
        long now = System.currentTimeMillis() * 1000;
        synchronized (lock) {
            if (now <= lastFileNumber) {
                return ++lastFileNumber;
            }
            lastFileNumber = now;
        }
        return now;
    }
    private byte[] trits(Transaction transaction) {
        byte[] trits = new byte[TRINARY_SIZE];
        if(transaction.bytes != null) {
            Converter.getTrits(transaction.bytes, trits);
        }
        return trits;
    }
}