package com.iota.iri.utils.dag;

import com.iota.iri.controllers.MilestoneViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.storage.Tangle;

import java.util.*;

/**
 * This class offers generic functions for recurring tasks that are related to the tangle and that otherwise would have
 * to be implemented over and over again in different parts of the code.
 */
public class DAGUtils {
    /**
     * Holds references to the singleton DAGUtils instances.
     */
    protected static HashMap<Object, DAGUtils> instances = new HashMap<>();

    /**
     * Holds a reference to the tangle instance which acts as an interface to the used database.
     */
    protected Tangle tangle;

    /**
     * This method allows us to retrieve the DAGUtils instance that corresponds to the given parameters.
     *
     * To save memory and to be able to use things like caches effectively, we do not allow to create multiple instances
     * of this class for the same set of parameters, but create a singleton instance of it.
     *
     * @param tangle database interface used to retrieve data
     * @return DAGUtils instance that allows to use the implemented methods.
     */
    public static DAGUtils get(Tangle tangle) {
        DAGUtils instance = instances.get(tangle);
        if(instance == null) {
            instances.put(tangle, instance = new DAGUtils(tangle));
        }

        return instance;
    }

    /**
     * Constructor of the class which allows to provide the required dependencies for the implemented methods.
     *
     * Since the constructor is protected we are not able to manually create instances of this class and have to
     * retrieve them through their singleton accessor {@link #get}.
     *
     * @param tangle database interface used to retrieve data
     */
    protected DAGUtils(Tangle tangle) {
        this.tangle = tangle;
    }

    //region TRAVERSE APPROVERS (BOTTOM -> TOP) ////////////////////////////////////////////////////////////////////////

    /**
     * This method provides a generic interface for traversing all approvers of a transaction up to a given point.
     *
     * To make the use of this method as easy as possible we provide a broad range of possible parameter signatures, so
     * we do not have to manually convert between milestones, transactions and hashes (see other methods with the same
     * name).
     *
     * It uses an non-recursive iterative algorithm that is able to handle huge chunks of the tangle without running out
     * of memory. It creates a queue of transactions that are being examined and processes them one by one. As new
     * approvers are found, they will be added to the queue and processed accordingly.
     *
     * Every found transaction is passed into the provided condition lambda, to determine if it still belongs to the
     * desired set of transactions and only then will be passed on to the currentTransactionConsumer lambda.
     *
     * @param startingTransaction the starting point of the traversal (does not get passed to the consumer)
     * @param condition a lambda function that allows to control how long the traversal should continue
     * @param currentTransactionConsumer a lambda function that allows us to "process" the found transactions
     * @param processedTransactions a set of hashes that shall be considered as "processed" already and that will
     *                              consequently be ignored in the traversal
     * @throws TraversalException if anything goes wrong while traversing the graph and processing the transactions
     */
    public void traverseApprovers(TransactionViewModel startingTransaction,
                                  TraversalCondition condition,
                                  TraversalConsumer currentTransactionConsumer,
                                  Set<Hash> processedTransactions) throws TraversalException {
        final Queue<TransactionViewModel> transactionsToExamine = new LinkedList<>();
        try {
            startingTransaction.getApprovers(tangle).getHashes().stream().forEach(approverHash -> {
                try {
                    transactionsToExamine.add(TransactionViewModel.fromHash(tangle, approverHash));
                } catch(Exception e) {
                    throw new RuntimeException(e);
                }
            });

            TransactionViewModel currentTransaction;
            while((currentTransaction = transactionsToExamine.poll()) != null) {
                if(
                processedTransactions.add(currentTransaction.getHash()) &&
                currentTransaction.getType() != TransactionViewModel.PREFILLED_SLOT &&
                condition.check(currentTransaction)
                ) {
                    currentTransactionConsumer.consume(currentTransaction);

                    currentTransaction.getApprovers(tangle).getHashes().stream().forEach(approverHash -> {
                        try {
                            transactionsToExamine.add(TransactionViewModel.fromHash(tangle, approverHash));
                        } catch(Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
                }
            }
        } catch (Exception e) {
            throw new TraversalException("error while traversing the approvers of " + startingTransaction, e);
        }
    }

    /**
     * Works like {@link DAGUtils#traverseApprovers(TransactionViewModel, TraversalCondition, TraversalConsumer, Set)}
     * but defaults to an empty set of processed transactions to consider all transactions.
     *
     * @see DAGUtils#traverseApprovers(TransactionViewModel, TraversalCondition, TraversalConsumer, Set)
     *
     * @param startingTransaction the starting point of the traversal (does not get passed to the consumer)
     * @param condition a lambda function that allows to control how long the traversal should continue
     * @param currentTransactionConsumer a lambda function that allows us to "process" the found transactions
     * @throws TraversalException if anything goes wrong while traversing the graph and processing the transactions
     */
    public void traverseApprovers(TransactionViewModel startingTransaction,
                                  TraversalCondition condition,
                                  TraversalConsumer currentTransactionConsumer) throws TraversalException {
        traverseApprovers(startingTransaction, condition, currentTransactionConsumer, new HashSet<>());
    }

    /**
     * Works like {@link DAGUtils#traverseApprovers(TransactionViewModel, TraversalCondition, TraversalConsumer, Set)}
     * but retrieves the starting transaction by the given hash.
     *
     * @see DAGUtils#traverseApprovers(TransactionViewModel, TraversalCondition, TraversalConsumer, Set)
     *
     * @param startingTransactionHash the starting point of the traversal (does not get passed to the consumer)
     * @param condition a lambda function that allows to control how long the traversal should continue
     * @param currentTransactionConsumer a lambda function that allows us to "process" the found transactions
     * @param processedTransactions a set of hashes that shall be considered as "processed" already and that will
     *                              consequently be ignored in the traversal
     * @throws TraversalException if anything goes wrong while traversing the graph and processing the transactions
     */
    public void traverseApprovers(Hash startingTransactionHash,
                                  TraversalCondition condition,
                                  TraversalConsumer currentTransactionConsumer,
                                  Set<Hash> processedTransactions) throws TraversalException {
        try {
            TransactionViewModel startingTransaction = TransactionViewModel.fromHash(tangle, startingTransactionHash);

            traverseApprovers(startingTransaction, condition, currentTransactionConsumer, processedTransactions);
        } catch (Exception e) {
            throw new TraversalException("error while retrieving the starting transaction " + startingTransactionHash, e);
        }
    }

    /**
     * Works like {@link DAGUtils#traverseApprovers(TransactionViewModel, TraversalCondition, TraversalConsumer, Set)}
     * but defaults to an empty set of processed transactions to consider all transactions and retrieves the starting
     * transaction by the given hash.
     *
     * @see DAGUtils#traverseApprovers(TransactionViewModel, TraversalCondition, TraversalConsumer, Set)
     *
     * @param startingTransactionHash the starting point of the traversal (does not get passed to the consumer)
     * @param condition a lambda function that allows to control how long the traversal should continue
     * @param currentTransactionConsumer a lambda function that allows us to "process" the found transactions
     * @throws TraversalException if anything goes wrong while traversing the graph and processing the transactions
     */
    public void traverseApprovers(Hash startingTransactionHash,
                                  TraversalCondition condition,
                                  TraversalConsumer currentTransactionConsumer) throws TraversalException {
        traverseApprovers(startingTransactionHash, condition, currentTransactionConsumer, new HashSet<>());
    }

    /**
     * Works like {@link DAGUtils#traverseApprovers(TransactionViewModel, TraversalCondition, TraversalConsumer, Set)}
     * but retrieves the starting transaction belonging to the given milestone.
     *
     * @see DAGUtils#traverseApprovers(TransactionViewModel, TraversalCondition, TraversalConsumer, Set)
     *
     * @param startingMilestone the starting point of the traversal (does not get passed to the consumer)
     * @param condition a lambda function that allows to control how long the traversal should continue
     * @param currentTransactionConsumer a lambda function that allows us to "process" the found transactions
     * @param processedTransactions a set of hashes that shall be considered as "processed" already and that will
     *                              consequently be ignored in the traversal
     * @throws TraversalException if anything goes wrong while traversing the graph and processing the transactions
     */
    public void traverseApprovers(MilestoneViewModel startingMilestone,
                                  TraversalCondition condition,
                                  TraversalConsumer currentTransactionConsumer,
                                  Set<Hash> processedTransactions) throws TraversalException {
        traverseApprovers(startingMilestone.getHash(), condition, currentTransactionConsumer, processedTransactions);
    }

    /**
     * Works like {@link DAGUtils#traverseApprovers(TransactionViewModel, TraversalCondition, TraversalConsumer, Set)}
     * but defaults to an empty set of processed transactions to consider all transactions and retrieves the starting
     * transaction belonging to the given milestone.
     *
     * @see DAGUtils#traverseApprovers(TransactionViewModel, TraversalCondition, TraversalConsumer, Set)
     *
     * @param startingMilestone the starting point of the traversal (does not get passed to the consumer)
     * @param condition a lambda function that allows to control how long the traversal should continue
     * @param currentTransactionConsumer a lambda function that allows us to "process" the found transactions
     * @throws TraversalException if anything goes wrong while traversing the graph and processing the transactions
     */
    public void traverseApprovers(MilestoneViewModel startingMilestone,
                                  TraversalCondition condition,
                                  TraversalConsumer currentTransactionConsumer) throws TraversalException {
        traverseApprovers(startingMilestone.getHash(), condition, currentTransactionConsumer, new HashSet<>());
    }

    //endregion ////////////////////////////////////////////////////////////////////////////////////////////////////////

    //region TRAVERSE APPROVEES (TOP -> BOTTOM) ////////////////////////////////////////////////////////////////////////

    /**
     * This method provides a generic interface for traversing all approvees of a transaction down to a given point.
     *
     * To make the use of this method as easy as possible we provide a broad range of possible parameter signatures, so
     * we do not have to manually convert between milestones, transactions and hashes (see other methods with the same
     * name).
     *
     * It uses an non-recursive iterative algorithm that is able to handle huge chunks of the tangle without running out
     * of memory. It creates a queue of transactions that are being examined and processes them one by one. As new
     * approvees are found, they will be added to the queue and processed accordingly.
     *
     * Every found transaction is passed into the provided condition lambda, to determine if it still belongs to the
     * desired set of transactions and only then will be passed on to the currentTransactionConsumer lambda.
     *
     * @param startingTransaction the starting point of the traversal (does not get passed to the consumer)
     * @param condition a lambda function that allows to control how long the traversal should continue
     * @param currentTransactionConsumer a lambda function that allows us to "process" the found transactions
     * @param processedTransactions a set of hashes that shall be considered as "processed" already and that will
     *                              consequently be ignored in the traversal
     * @throws TraversalException if anything goes wrong while traversing the graph and processing the transactions
     */
    public void traverseApprovees(TransactionViewModel startingTransaction,
                                  TraversalCondition condition,
                                  TraversalConsumer currentTransactionConsumer,
                                  Set<Hash> processedTransactions) throws TraversalException {
        final Queue<TransactionViewModel> transactionsToExamine = new LinkedList<>();
        try {
            transactionsToExamine.add(TransactionViewModel.fromHash(tangle, startingTransaction.getBranchTransactionHash()));
            transactionsToExamine.add(TransactionViewModel.fromHash(tangle, startingTransaction.getTrunkTransactionHash()));

            TransactionViewModel currentTransaction;
            while((currentTransaction = transactionsToExamine.poll()) != null) {
                if(
                processedTransactions.add(currentTransaction.getHash()) &&
                currentTransaction.getType() != TransactionViewModel.PREFILLED_SLOT &&
                condition.check(currentTransaction)
                ) {
                    currentTransactionConsumer.consume(currentTransaction);

                    transactionsToExamine.add(TransactionViewModel.fromHash(tangle, currentTransaction.getBranchTransactionHash()));
                    transactionsToExamine.add(TransactionViewModel.fromHash(tangle, currentTransaction.getTrunkTransactionHash()));
                }
            }
        } catch (Exception e) {
            throw new TraversalException("error while traversing the approvees of " + startingTransaction, e);
        }
    }

    /**
     * Works like {@link DAGUtils#traverseApprovees(TransactionViewModel, TraversalCondition, TraversalConsumer, Set)}
     * but defaults to an empty set of processed transactions to consider all transactions.
     *
     * @see DAGUtils#traverseApprovees(TransactionViewModel, TraversalCondition, TraversalConsumer, Set)
     *
     * @param startingTransaction the starting point of the traversal (does not get passed to the consumer)
     * @param condition a lambda function that allows to control how long the traversal should continue
     * @param currentTransactionConsumer a lambda function that allows us to "process" the found transactions
     * @throws TraversalException if anything goes wrong while traversing the graph and processing the transactions
     */
    public void traverseApprovees(TransactionViewModel startingTransaction,
                                  TraversalCondition condition,
                                  TraversalConsumer currentTransactionConsumer) throws Exception {
        traverseApprovees(startingTransaction, condition, currentTransactionConsumer, new HashSet<>());
    }

    /**
     * Works like {@link DAGUtils#traverseApprovees(TransactionViewModel, TraversalCondition, TraversalConsumer, Set)}
     * but retrieves the starting transaction by the given hash.
     *
     * @see DAGUtils#traverseApprovees(TransactionViewModel, TraversalCondition, TraversalConsumer, Set)
     *
     * @param startingTransactionHash the starting point of the traversal (does not get passed to the consumer)
     * @param condition a lambda function that allows to control how long the traversal should continue
     * @param currentTransactionConsumer a lambda function that allows us to "process" the found transactions
     * @param processedTransactions a set of hashes that shall be considered as "processed" already and that will
     *                              consequently be ignored in the traversal
     * @throws TraversalException if anything goes wrong while traversing the graph and processing the transactions
     */
    public void traverseApprovees(Hash startingTransactionHash,
                                  TraversalCondition condition,
                                  TraversalConsumer currentTransactionConsumer,
                                  Set<Hash> processedTransactions) throws TraversalException {
        try {
            TransactionViewModel startingTransaction = TransactionViewModel.fromHash(tangle, startingTransactionHash);

            traverseApprovees(startingTransaction, condition, currentTransactionConsumer, processedTransactions);
        } catch (Exception e) {
            throw new TraversalException("error while retrieving the starting transaction " + startingTransactionHash, e);
        }
    }

    /**
     * Works like {@link DAGUtils#traverseApprovees(TransactionViewModel, TraversalCondition, TraversalConsumer, Set)}
     * but defaults to an empty set of processed transactions to consider all transactions and retrieves the starting
     * transaction by the given hash.
     *
     * @see DAGUtils#traverseApprovees(TransactionViewModel, TraversalCondition, TraversalConsumer, Set)
     *
     * @param startingTransactionHash the starting point of the traversal (does not get passed to the consumer)
     * @param condition a lambda function that allows to control how long the traversal should continue
     * @param currentTransactionConsumer a lambda function that allows us to "process" the found transactions
     * @throws TraversalException if anything goes wrong while traversing the graph and processing the transactions
     */
    public void traverseApprovees(Hash startingTransactionHash,
                                  TraversalCondition condition,
                                  TraversalConsumer currentTransactionConsumer) throws TraversalException {
        traverseApprovees(startingTransactionHash, condition, currentTransactionConsumer, new HashSet<>());
    }

    /**
     * Works like {@link DAGUtils#traverseApprovees(TransactionViewModel, TraversalCondition, TraversalConsumer, Set)}
     * but retrieves the starting transaction belonging to the given milestone.
     *
     * @see DAGUtils#traverseApprovees(TransactionViewModel, TraversalCondition, TraversalConsumer, Set)
     *
     * @param startingMilestone the starting point of the traversal (does not get passed to the consumer)
     * @param condition a lambda function that allows to control how long the traversal should continue
     * @param currentTransactionConsumer a lambda function that allows us to "process" the found transactions
     * @param processedTransactions a set of hashes that shall be considered as "processed" already and that will
     *                              consequently be ignored in the traversal
     * @throws TraversalException if anything goes wrong while traversing the graph and processing the transactions
     */
    public void traverseApprovees(MilestoneViewModel startingMilestone,
                                  TraversalCondition condition,
                                  TraversalConsumer currentTransactionConsumer,
                                  Set<Hash> processedTransactions) throws TraversalException {
        traverseApprovees(startingMilestone.getHash(), condition, currentTransactionConsumer, processedTransactions);
    }

    /**
     * Works like {@link DAGUtils#traverseApprovees(TransactionViewModel, TraversalCondition, TraversalConsumer, Set)}
     * but defaults to an empty set of processed transactions to consider all transactions and retrieves the starting
     * transaction belonging to the given milestone.
     *
     * @see DAGUtils#traverseApprovees(TransactionViewModel, TraversalCondition, TraversalConsumer, Set)
     *
     * @param startingMilestone the starting point of the traversal (does not get passed to the consumer)
     * @param condition a lambda function that allows to control how long the traversal should continue
     * @param currentTransactionConsumer a lambda function that allows us to "process" the found transactions
     * @throws TraversalException if anything goes wrong while traversing the graph and processing the transactions
     */
    public void traverseApprovees(MilestoneViewModel startingMilestone,
                                  TraversalCondition condition,
                                  TraversalConsumer currentTransactionConsumer) throws TraversalException {
        traverseApprovees(startingMilestone.getHash(), condition, currentTransactionConsumer, new HashSet<Hash>());
    }

    //endregion ////////////////////////////////////////////////////////////////////////////////////////////////////////
}
