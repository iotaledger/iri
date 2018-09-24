package com.iota.iri.service.garbagecollector;

import com.iota.iri.controllers.MilestoneViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.model.IntegerIndex;
import com.iota.iri.model.Milestone;
import com.iota.iri.model.Transaction;
import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Persistable;
import com.iota.iri.utils.Pair;
import com.iota.iri.utils.dag.DAGHelper;

import java.util.*;

/**
 * This class represents a cleanup job for the {@link GarbageCollector}.
 *
 * It removes milestones and all of their directly and indirectly referenced transactions ( and the orphaned subtangles
 * branching off of the deleted transactions). Therefore it is used by the
 * {@link com.iota.iri.service.snapshot.SnapshotManager} to clean up milestones prior to a snapshot.
 *
 * It gets processed one milestone at a time persisting the progress after each step.
 */
public class MilestonePrunerJob extends GarbageCollectorJob {
    /**
     * Holds the milestone index where this job starts cleaning up (read only).
     */
    private final int startingIndex;

    /**
     * Holds the milestone index where this job stops cleaning up (read only).
     */
    private int targetIndex;

    /**
     * Holds the milestone index of the oldest milestone that was cleaned up already (the current progress).
     */
    private int currentIndex;

    /**
     * This method registers this job type in a {@link GarbageCollector}.
     *
     * It registers the {@link JobParser}, the {@link QueueProcessor} and the {@link QueueConsolidator} which will
     * allow us to process jobs of this particular type.
     *
     * @param garbageCollector {@link GarbageCollector} that shall be able to process {@link MilestonePrunerJob}s
     */
    static void registerInGarbageCollector(GarbageCollector garbageCollector) {
        garbageCollector.registerParser(MilestonePrunerJob.class, MilestonePrunerJob::parse);
        garbageCollector.registerQueueProcessor(MilestonePrunerJob.class, MilestonePrunerJob::processQueue);
        garbageCollector.registerQueueConsolidator(MilestonePrunerJob.class, MilestonePrunerJob::consolidateQueue);
    }

    /**
     * This method consolidates the cleanup jobs by merging two or more jobs together if they are either both done or
     * pending.
     *
     * It is used to clean up the queue and the corresponding garbage collector file, so it always has a size of less
     * than 4 jobs.
     *
     * Since the jobs are getting processed from the beginning of the queue, we first check if the first two jobs are
     * "done" and merge them into a single one that reflects the "done" status of both jobs. Consecutively we check if
     * there are two or more pending jobs at the end that can also be consolidated.
     *
     * It is important to note that the jobs always clean from their startingPosition to the startingPosition of the
     * previous job (or the {@code milestoneStartIndex} of the last global snapshot if there is no previous one) without
     * any gaps in between which is required to be able to merge them.
     *
     * @throws GarbageCollectorException if an error occurs while persisting the state
     */
    private static void consolidateQueue(GarbageCollector garbageCollector, ArrayDeque<GarbageCollectorJob> jobQueue) throws GarbageCollectorException {
        // if we have at least 2 jobs -> check if we can consolidate them at the beginning (both done)
        if(jobQueue.size() >= 2) {
            MilestonePrunerJob job1 = (MilestonePrunerJob) jobQueue.removeFirst();
            MilestonePrunerJob job2 = (MilestonePrunerJob) jobQueue.removeFirst();

            // if both first job are done -> consolidate them and persists the changes
            if(job1.currentIndex == garbageCollector.snapshotManager.getConfiguration().getMilestoneStartIndex() && job2.currentIndex == job1.startingIndex) {
                MilestonePrunerJob consolidatedJob = new MilestonePrunerJob(job2.startingIndex, job1.currentIndex);
                consolidatedJob.registerGarbageCollector(garbageCollector);
                jobQueue.addFirst(consolidatedJob);

                garbageCollector.persistChanges();
            }

            // otherwise just add them back to the queue
            else {
                jobQueue.addFirst(job2);
                jobQueue.addFirst(job1);
            }
        }

        // if we have at least 2 jobs -> check if we can consolidate them at the end (both pending)
        boolean cleanupSuccessful = true;
        while(jobQueue.size() >= 2 && cleanupSuccessful) {
            MilestonePrunerJob job1 = (MilestonePrunerJob) jobQueue.removeLast();
            MilestonePrunerJob job2 = (MilestonePrunerJob) jobQueue.removeLast();

            // if both jobs are pending -> consolidate them and persists the changes
            if(job1.currentIndex == job1.startingIndex && job2.currentIndex == job2.startingIndex) {
                MilestonePrunerJob consolidatedJob = new MilestonePrunerJob(job1.startingIndex, job1.currentIndex);
                consolidatedJob.registerGarbageCollector(garbageCollector);
                jobQueue.addLast(consolidatedJob);

                garbageCollector.persistChanges();
            }

            // otherwise just add them back to the queue
            else {
                jobQueue.addLast(job2);
                jobQueue.addLast(job1);

                cleanupSuccessful = false;
            }
        }

        int previousStartIndex = garbageCollector.snapshotManager.getConfiguration().getMilestoneStartIndex();
        for(GarbageCollectorJob currentJob : jobQueue) {
            ((MilestonePrunerJob) currentJob).targetIndex = previousStartIndex;

            previousStartIndex = ((MilestonePrunerJob) currentJob).startingIndex;
        }
    }

    /**
     * This method processes the entire queue of this job.
     *
     * It first retrieves the first job and processes it until it is completely done. If it finds a second job, it
     * immediately tries to process that one as well. If both jobs are done it triggers a consolidation to merge both
     * done jobs into a single one.
     *
     * We keep the first done job in the queue (even though it might have been consolidated) because we need its
     * {@link #startingIndex} to determine the next jobs {@link #targetIndex}.
     *
     * @param garbageCollector {@link GarbageCollector} that this job belongs to
     * @param jobQueue queue of {@link MilestonePrunerJob} jobs that shall get processed
     * @throws GarbageCollectorException if anything goes wrong while processing the jobs
     */
    private static void processQueue(GarbageCollector garbageCollector, ArrayDeque<GarbageCollectorJob> jobQueue) throws GarbageCollectorException {
        while(!Thread.interrupted() && jobQueue.size() >= 2 || ((MilestonePrunerJob) jobQueue.getFirst()).targetIndex < ((MilestonePrunerJob) jobQueue.getFirst()).currentIndex) {
            MilestonePrunerJob firstJob = (MilestonePrunerJob) jobQueue.removeFirst();
            firstJob.process();

            if(jobQueue.size() >= 1) {
                MilestonePrunerJob secondJob = (MilestonePrunerJob) jobQueue.getFirst();
                jobQueue.addFirst(firstJob);

                secondJob.process();

                consolidateQueue(garbageCollector, jobQueue);
            } else {
                jobQueue.addFirst(firstJob);

                break;
            }
        }
    }

    /**
     * This method parses the string representation of a {@link MilestonePrunerJob} and creates the corresponding object.
     *
     * It splits the String input in two parts (delimited by a ";") and passes them into the constructor of a new
     * {@link MilestonePrunerJob} by interpreting them as {@link #startingIndex} and {@link #currentIndex} of the job.
     *
     * The {@link #targetIndex} is derived by the jobs position in the queue and the fact that milestone deletions
     * happen sequentially from the newest to the oldest milestone.
     *
     * @param input serialized String representation of a {@link MilestonePrunerJob}
     * @return a new {@link MilestonePrunerJob} with the provided details
     * @throws GarbageCollectorException if anything goes wrong while parsing the input
     */
    private static MilestonePrunerJob parse(String input) throws GarbageCollectorException {
        String[] parts = input.split(";", 2);
        if(parts.length >= 2) {
            return new MilestonePrunerJob(Integer.valueOf(parts[0]), Integer.valueOf(parts[1]));
        }

        throw new GarbageCollectorException("failed to parse garbage collector file - invalid input: " + input);
    }

    /**
     * Does same as {@link #MilestonePrunerJob(int, int)} but defaults to the {@link #currentIndex} being the same as
     * the {@link #startingIndex}. This is usually the case when we create a new job programmatically that does not get
     * restored from a state file.
     *
     * @param startingIndex milestone index that defines where to start cleaning up
     */
    public MilestonePrunerJob(int startingIndex) {
        this(startingIndex, startingIndex);
    }

    /**
     * Constructor of the job receiving both values that are relevant for the job.
     *
     * It simply stores the provided parameters in its according protected properties.
     *
     * Since cleanup jobs can be consolidated (to reduce the size of the garbage collector state file), we need to be
     * able provide both parameters even tho the job usually always "starts" with its {@code currentIndex} being equal
     * to the {@code startingIndex}.
     *
     * @param startingIndex milestone index that defines where to start cleaning up
     * @param currentIndex milestone index that defines the next milestone that should be cleaned up by this job
     */
    private MilestonePrunerJob(int startingIndex, int currentIndex) {
        this.startingIndex = startingIndex;
        this.currentIndex = currentIndex;
    }

    /**
     * This method starts the processing of the job which triggers the actual removal of database entries.
     *
     * It iterates from the {@link #currentIndex} to the provided {@link #targetIndex} and processes every milestone
     * one by one. After each step is finished we persist the progress to be able to continue with the current progress
     * upon IRI restarts.
     *
     * @throws GarbageCollectorException if anything goes wrong while cleaning up or persisting the changes
     */
    public void process() throws GarbageCollectorException {
        while(!Thread.interrupted() && targetIndex < currentIndex) {
            cleanupMilestoneTransactions();

            currentIndex--;

            garbageCollector.persistChanges();
        }
    }

    /**
     * This method takes care of cleaning up a single milestone and all of its transactions and performs the actual
     * database operations.
     *
     * This method performs the deletions in an atomic way, which means that either the full processing succeeds or
     * fails. It does that by iterating through all the transactions that belong to the current milestone and first
     * collecting them in a List of items to delete. Once all transactions where found we issue a batchDelete.
     *
     * While processing the transactions that are directly or indirectly referenced by the chosen milestone, we also
     * issue additional {@link UnconfirmedSubtanglePrunerJob}s that remove the orphaned parts of the tangle that branch off
     * the deleted transactions because they would otherwise loose their connection to the rest of the tangle unless
     * they are branching off a solid entry point (in which case we wait with the deletion until the solid entry point
     * becomes irrelevant).
     *
     * After removing the entries from the database it also removes the entries from the relevant runtime caches.
     *
     * @throws GarbageCollectorException if something goes wrong while cleaning up the milestone
     */
    private void cleanupMilestoneTransactions() throws GarbageCollectorException {
        try {
            // collect elements to delete
            List<Pair<Indexable, ? extends Class<? extends Persistable>>> elementsToDelete = new ArrayList<>();
            MilestoneViewModel milestoneViewModel = MilestoneViewModel.get(garbageCollector.tangle, currentIndex);
            if (milestoneViewModel != null) {
                elementsToDelete.add(new Pair<>(milestoneViewModel.getHash(), Transaction.class));
                elementsToDelete.add(new Pair<>(new IntegerIndex(milestoneViewModel.index()), Milestone.class));
                if (!garbageCollector.snapshotManager.getInitialSnapshot().hasSolidEntryPoint(milestoneViewModel.getHash())) {
                    garbageCollector.addJob(new UnconfirmedSubtanglePrunerJob(milestoneViewModel.getHash()));
                }
                DAGHelper.get(garbageCollector.tangle).traverseApprovees(
                milestoneViewModel.getHash(),
                approvedTransaction -> approvedTransaction.snapshotIndex() >= milestoneViewModel.index(),
                approvedTransaction -> {
                    elementsToDelete.add(new Pair<>(approvedTransaction.getHash(), Transaction.class));

                    if (!garbageCollector.snapshotManager.getInitialSnapshot().hasSolidEntryPoint(approvedTransaction.getHash())) {
                        try {
                            garbageCollector.addJob(new UnconfirmedSubtanglePrunerJob(approvedTransaction.getHash()));
                        } catch(GarbageCollectorException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
                );
            }

            // clean database entries
            garbageCollector.tangle.deleteBatch(elementsToDelete);

            // clean runtime caches
            elementsToDelete.forEach(element -> {
                if(Transaction.class.equals(element.hi)) {
                    garbageCollector.tipsViewModel.removeTipHash((Hash) element.low);
                } else if(Milestone.class.equals(element.hi)) {
                    MilestoneViewModel.clear(((IntegerIndex) element.low).getValue());
                }
            });
        } catch(Exception e) {
            throw new GarbageCollectorException("failed to cleanup milestone #" + currentIndex, e);
        }
    }

    /**
     * This method creates the serialized representation of the job, that is used to persist the state of the
     * {@link GarbageCollector}.
     *
     * It simply concatenates the {@link #startingIndex} and the {@link #currentIndex} as they are necessary to fully
     * describe the job.
     *
     * @return serialized representation of this job that can be used to persist its state
     */
    String serialize() {
        return startingIndex + ";" + currentIndex;
    }
}
