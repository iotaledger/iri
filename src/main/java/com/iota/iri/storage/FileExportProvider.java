package com.iota.iri.storage;

import com.iota.iri.model.Transaction;
import com.iota.iri.utils.Converter;
import com.iota.iri.utils.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.iota.iri.controllers.TransactionViewModel.TRINARY_SIZE;

/**
 * Created by paul on 4/18/17.
 */
public class FileExportProvider implements PersistenceProvider {
    private static final Logger log = LoggerFactory.getLogger(FileExportProvider.class);
    private static final String FOLDER_EXPORT = "export";
    private static final String FOLDER_EXPORT_SOLID = "export-solid";
    private static long lastFileNumber = 0L;
    private static final Object lock = new Object();

    Path createDirectory(Path exportPath) {
        if (exportPath == null) {
            throw new IllegalArgumentException("exportPath should not be null");
        }
        if (!Files.exists(exportPath)) {
            log.info("Create directory '{}'", exportPath);
            try {
                return Files.createDirectory(exportPath);
            } catch (IOException e) {
                log.error("Could not create directory", e);
            }
        }
        return exportPath;
    }

    @Override
    public void init() {
        createDirectory(Paths.get(FOLDER_EXPORT));
        createDirectory(Paths.get(FOLDER_EXPORT_SOLID));
    }

    @Override
    public boolean isAvailable() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void shutdown() {
        // not yet implemented
    }

    @Override
    public boolean save(Persistable model, Indexable index) {
        return false;
    }

    @Override
    public void delete(Class<?> model, Indexable index) {
        // not yet implemented
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
        Transaction transaction = ((Transaction) model);
        File exportFile = new File(FOLDER_EXPORT, String.valueOf(getFileNumber()) + ".tx");
        try(PrintWriter writer = new PrintWriter(exportFile, StandardCharsets.UTF_8.name())) {
            writer.println(index.toString());
            writer.println(Converter.trytes(trits(transaction)));
            writer.println(transaction.sender);
            return true;
        } catch (UnsupportedEncodingException | FileNotFoundException e) {
            log.error("File export failed", e);
        } catch (Exception e) {
            log.error("Transaction load failed", e);
        }
        return false;
    }

    @Override
    public boolean exists(Class<?> model, Indexable key) {
        return false;
    }

    @Override
    public Pair<Indexable, Persistable> latest(Class<?> model, Class<?> indexModel) {
        return null;
    }

    @Override
    public Set<Indexable> keysWithMissingReferences(Class<?> modelClass, Class<?> other) {
        return new HashSet<>();
    }

    @Override
    public Persistable get(Class<?> model, Indexable index) {
        return null;
    }

    @Override
    public boolean mayExist(Class<?> model, Indexable index) {
        return false;
    }

    @Override
    public long count(Class<?> model) {
        return 0;
    }

    @Override
    public Set<Indexable> keysStartingWith(Class<?> modelClass, byte[] value) {
        return new HashSet<>();
    }

    @Override
    public Persistable seek(Class<?> model, byte[] key) {
        return null;
    }

    @Override
    public Pair<Indexable, Persistable> next(Class<?> model, Indexable index) {
        return null;
    }

    @Override
    public Pair<Indexable, Persistable> previous(Class<?> model, Indexable index) {
        return null;
    }

    @Override
    public Pair<Indexable, Persistable> first(Class<?> model, Class<?> indexModel) {
        return null;
    }

    @Override
    public boolean saveBatch(List<Pair<Indexable, Persistable>> models) {
        return false;
    }

    @Override
    public void deleteBatch(Collection<Pair<Indexable, ? extends Class<? extends Persistable>>> models) {
        // not yet implemented
    }

    @Override
    public void clear(Class<?> column) {
        // not yet implemented
    }

    @Override
    public void clearMetadata(Class<?> column) {
        // not yet implemented
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