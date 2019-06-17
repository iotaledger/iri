var System = java.lang.System;

var snapshotState = com.iota.iri.service.snapshot.snapshotState;
var snapshotMeta = com.iota.iri.service.snapshot.snapshotMetaData;

var snapshotProvider = IOTA.snapshotProvider;

var iri = com.iota.iri;
var Callable = iri.service.CallableRequest;
var Response = iri.service.dto.IXIResponse;
var ErrorResponse = iri.service.dto.ErrorResponse;


function getInitialSnapshotClone(){
    return snapshotProvider.getInitialSnapshot().clone();
}

function getLatestSnapshotClone(){
    return snapshotProvider.getLatestSnapshot().clone();
}


function getSnapshotState(){
    var ledgerState = getInitialSnapshotClone();

    return Response.create({
        index: ledgerState.getIndex(),
        state: ledgerState.getBalances()
    });

}

function getSnapshotMetaData(){
    var ledgerState = getInitialSnapshotClone();

    return Response.create({
        index: ledgerState.getIndex(),
        metaData: ledgerState.getSeenMilestones()
    });
}


function getSnapshotIndexes(){
    //console.log("Fetching balances");
    var ledgerState = getLatestSnapshotClone();

    return Response.create({
        initialIndex: ledgerState.getInitialIndex(),
        currentIndex: ledgerState.getIndex(),
    });
}




API.put("getIndexes", new Callable({ call: getSnapshotIndexes }))
API.put("getState", new Callable({ call: getSnapshotState }))
API.put("getMetaData", new Callable({ call: getSnapshotMetaData }))
