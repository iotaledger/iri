package com.iota.iri.service.dto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.model.Transaction;
import com.iota.iri.service.storage.AbstractStorage;
import com.iota.iri.service.storage.Storage;
import com.iota.iri.service.storage.StorageTransactions;
import com.iota.iri.utils.Converter;

public class ExportToExternalDBResponse extends AbstractResponse {

    private static final Logger log = LoggerFactory.getLogger(ExportToExternalDBResponse.class);
    
    private int addedTransactions;
    
    public static AbstractResponse create(int numberOfAddedTransactions) {
        ExportToExternalDBResponse res = new ExportToExternalDBResponse();
        res.addedTransactions = numberOfAddedTransactions;
        return res;
    }
    
    public int exportTransactions() {
        
        long pointer = AbstractStorage.CELLS_OFFSET - AbstractStorage.SUPER_GROUPS_OFFSET;
        
        while (pointer < StorageTransactions.transactionsNextPointer) {
            Transaction transaction = StorageTransactions.instance().loadTransaction(pointer);
            if (transaction.type != Storage.PREFILLED_SLOT) {
                addedTransactions++;
                Long itsArrivalTime = Converter.longValue(transaction.trits(), Transaction.TIMESTAMP_TRINARY_OFFSET, 27);
                log.info(String.valueOf(itsArrivalTime));
            }
            pointer += AbstractStorage.CELL_SIZE;
        }
        
        return addedTransactions;
    }
    
}