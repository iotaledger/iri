var System = java.lang.System;

var spentAddressProvider = IOTA.spentAddressesProvider

var iri = com.iota.iri;
var Callable = iri.service.CallableRequest;
var Response = iri.service.dto.IXIResponse;
var ErrorResponse = iri.service.dto.ErrorResponse;

var fileName = "spentAddresses.txt";

// Log using logger of the ixi class
var log = org.slf4j.LoggerFactory.getLogger(iri.IXI.class);

/*
curl http://localhost:14265 -X POST -H 'X-IOTA-API-Version: 1.4.1' -H 'Content-Type: application/json' -d '{"command": "Spent.generateSpentAddressesFile"}'
*/
function generate(request) {
    var file = new java.io.File(fileName);
    if (file.exists()){
        if (file.isDirectory()){
            log.error("Found a directory called {}, aborting!");
            return ErrorResponse.create("Failed to create spent address due to {} beeing a folder", fileName);
        } else {
            log.info("{} already exists.. Overwriting!", fileName);
        }
    } else {
        file.createNewFile();
    }

    var hashes = spentAddressProvider.getAllAddresses();
    var writer;
    var checksum;
    try {
        // False for always overwriting
        writer = new java.io.FileWriter(file, false); 

        var separator = System.getProperty("line.separator");
        
        // Start with the time
        writer.write(System.currentTimeMillis() + separator);

        // Create a digest
        var digest = java.security.MessageDigest.getInstance("SHA-256");
        for (var i=0; i<hashes.length; i++){
            // Update with hash bytes
            digest.update(hashes[i].bytes());
        }

        // Write checksum
        checksum = java.lang.String.format("%064x", new java.math.BigInteger(1, digest.digest()));
        writer.write(checksum + separator);

        // Write amount of addresses
        writer.write(hashes.length.toString() + separator);

        // Write spent addresses
        for (var i=0; i<hashes.length; i++){
            var hash = hashes[i].toString();

            writer.write(hash + separator);
        }

        writer.close();
    } catch (error){
        if (writer){
            writer.close();
        }
    }

    return Response.create({
        amount: hashes.length,
        fileName: fileName,
        sizeInMb: new java.lang.Integer(file.length() / (1024 * 1024)),
        checksum: checksum
    });
}

API.put("generateSpentAddressesFile", new Callable({ call: generate }))