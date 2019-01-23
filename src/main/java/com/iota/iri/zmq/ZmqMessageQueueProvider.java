package com.iota.iri.zmq;

import com.iota.iri.conf.ZMQConfig;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.model.persistables.Transaction;
import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Persistable;
import com.iota.iri.utils.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Use <a href="http://zeromq.org/" target="_top">zeromq</a> to create a MessageQueue that publishes messages.
 */
public class ZmqMessageQueueProvider implements MessageQueueProvider {

    private static final Logger log = LoggerFactory.getLogger(ZmqMessageQueueProvider.class);
    private final MessageQ messageQ;

    /**
     * Factory method to create a new ZmqMessageQueue with the given configuration.
     *
     * @param configuration with the zmq properties used to create MessageQueue
     */
    public ZmqMessageQueueProvider(ZMQConfig configuration) {
        this.messageQ = MessageQ.createWith(configuration);
    }

    @Override
    public boolean publishTransaction(Persistable model, Indexable index, String item) {
        if(!(model instanceof Transaction)) {
            return false;
        }
        if(!item.contains("sender")) {
            return false;
        }

        Transaction transaction = ((Transaction) model);
        TransactionViewModel transactionViewModel = new TransactionViewModel(transaction, (Hash)index);

        publishTx(transactionViewModel);
        publishTxTrytes(transactionViewModel);

        return true;
    }

    private void publishTx(TransactionViewModel transactionViewModel) {
        StringBuilder txStringBuilder = new StringBuilder(600);

        try {
            txStringBuilder.append("tx ");
            txStringBuilder.append(transactionViewModel.getHash()); txStringBuilder.append(" ");
            txStringBuilder.append(transactionViewModel.getAddressHash()); txStringBuilder.append(" ");
            txStringBuilder.append(String.valueOf(transactionViewModel.value())); txStringBuilder.append(" ");
            txStringBuilder.append(transactionViewModel.getObsoleteTagValue().toString(), 0, 27); txStringBuilder.append(" ");
            txStringBuilder.append(String.valueOf(transactionViewModel.getTimestamp())); txStringBuilder.append(" ");
            txStringBuilder.append(String.valueOf(transactionViewModel.getCurrentIndex())); txStringBuilder.append(" ");
            txStringBuilder.append(String.valueOf(transactionViewModel.lastIndex())); txStringBuilder.append(" ");
            txStringBuilder.append(transactionViewModel.getBundleHash()); txStringBuilder.append(" ");
            txStringBuilder.append(transactionViewModel.getTrunkTransactionHash()); txStringBuilder.append(" ");
            txStringBuilder.append(transactionViewModel.getBranchTransactionHash()); txStringBuilder.append(" ");
            txStringBuilder.append(String.valueOf(transactionViewModel.getArrivalTime())); txStringBuilder.append(" ");
            txStringBuilder.append(transactionViewModel.getTagValue().toString(), 0, 27);

            messageQ.publish(txStringBuilder.toString());
        } catch (Exception e) {
            log.error(txStringBuilder.toString());
            log.error("Error publishing tx to zmq.", e);
        }
    }

    private void publishTxTrytes(TransactionViewModel transactionViewModel) {
        StringBuilder txTrytesStringBuilder = new StringBuilder(TransactionViewModel.TRINARY_SIZE/3);

        try {
            txTrytesStringBuilder.append("tx_trytes ");
            txTrytesStringBuilder.append(Converter.trytes(transactionViewModel.trits())); txTrytesStringBuilder.append(" ");
            txTrytesStringBuilder.append(transactionViewModel.getHash());

            messageQ.publish(txTrytesStringBuilder.toString());
        } catch (Exception e) {
            log.error(txTrytesStringBuilder.toString());
            log.error("Error publishing tx_trytes to zmq.", e);
        }
    }

    /**
     * Publishes the message to the MessageQueue.
     *
     * @param message that can be formatted by {@link String#format(String, Object...)}
     * @param objects that should replace the placeholder in message.
     * @see String#format(String, Object...)
     */
    @Override
    public void publish(String message, Object... objects) {
        this.messageQ.publish(message, objects);
    }

    @Override
    public void shutdown() {
        this.messageQ.shutdown();
    }
}