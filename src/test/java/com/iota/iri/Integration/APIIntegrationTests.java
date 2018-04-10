package com.iota.iri.Integration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.iota.iri.IRI;
import com.iota.iri.IXI;
import com.iota.iri.Iota;
import com.iota.iri.conf.Configuration;
import com.iota.iri.service.API;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.builder.ResponseSpecBuilder;
import com.jayway.restassured.config.HttpClientConfig;
import com.jayway.restassured.path.json.JsonPath;
import com.jayway.restassured.response.Response;
import com.jayway.restassured.specification.RequestSpecification;
import com.jayway.restassured.specification.ResponseSpecification;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;

public class APIIntegrationTests {

    private static final Boolean spawnNode = true; //can be changed to false to use already deployed node
    private static final String portStr = "14266";
    private static final String hostName = "http://localhost";

    // No result should ever take a minute
    private static final int SOCKET_TIMEOUT = 60_000;

    // Expect to connect to any service worldwide in under 100 ms
    // and to any online machine local in 1 ms. The 50 ms default value is a suggested compromise.
    private static final int CONNECTION_TIMEOUT = 50;
    private static ResponseSpecification responseSpec;
    // Constants used in tests
    private static final String[] URIS = {"udp://8.8.8.8:14266", "udp://8.8.8.5:14266"};
    private static final String[] ADDRESSES = {"RVORZ9SIIP9RCYMREUIXXVPQIPHVCNPQ9HZWYKFWYWZRE9JQKG9REPKIASHUUECPSQO9JT9XNMVKWYGVA"};
    private static final String[] HASHES = {"OAATQS9VQLSXCLDJVJJVYUGONXAXOFMJOZNSYWRZSWECMXAQQURHQBJNLD9IOFEPGZEPEMPXCIVRX9999"};
    //Trytes of "VHBRBB9EWCPDKYIBEZW9XVX9AOBQKSCKSTMJLGBANQ99PR9HGYNH9AJWTMHJQBDJHZVWHZMXPILS99999"
    private static final String[] TRYTES = {"QBTCHDEADDPCXCSCEAXCBDEAXCCDHDPCGDEAUCCDFDEAGDIDDDDDCDFDHDXCBDVCEAHDWCTCEAHDPCBDVC9DTCEABDTCHDKDCDFDZCEAQCMDEAGDDDPCADADXCBDVCEAHDFDPCBDGDPCRCHDXCCDBDGDSAEAPBCDFDEAADCDFDTCEAXCBDUCCDFDADPCHDXCCDBDQAEAJDXCGDXCHDDBEAWCHDHDDDDBTATAXCCDHDPCGDDDPCADSARCCDADTASAEAHBHBHBHBHBEAFDPCBDSCCDADEAKDXCZCXCDDTCSCXCPCEAPCFDHDXCRC9DTCDBEAJGDHACDHUBBCDCVBDCEAWBKBRBWBDCNBEAZBKBBCRBKBEAHBHBHBHBHBEAJGIIFDIIZCGDID9DIDEAWBPCWCADIDSCEAZBPCGDWCPCEAMACCIDFDZCXCGDWCDBEAJGIIFDIIZCGDID9DIDEAWBPCWCADIDHDEAZBPCEAPCEBEAVABB9BYAEAEAEAXAVAEATBID9DMDEAVACBXAVANAQAEAKDPCGDEAPCBDEAYBHDHDCDADPCBDEAPCFDADMDEAVCTCBDTCFDPC9DEAPCBDSCEAGDHDPCHDTCGDADPCBDEACDUCEATCHDWCBDXCRCEAQBTCCDFDVCXCPCBDEAQCPCRCZCVCFDCDIDBDSCSAJ9J9J9GBGBEAOBPCFD9DMDEA9DXCUCTCEAPCBDSCEARCPCFDTCTCFDEAGBGBJ9WBPCWCADIDSCEAZBPCGDWCPCEAKDPCGDEAQCCDFDBDEAXCBDEAVABB9BYAEAXCBDEAUBCDQCID9DTCHDXCQAEAHDWCTCBDEADDPCFDHDEACDUCEAHDWCTCEAYBHDHDCDADPCBDEAOBADDDXCFDTCEAZCBDCDKDBDEAQCMDEAXCHDGDEACCIDFDZCXCGDWCEABDPCADTCEAJGIIFDIIZCGDIDQAEAXCBDEAHDWCTCEADDFDTCGDTCBDHDRASCPCMDEAKBSCYCPCFDPCEAFDTCVCXCCDBDEACDUCEAHDWCTCEAACTCDDIDQC9DXCRCEACDUCEAQBTCCDFDVCXCPCSAJ9KBUCHDTCFDEAVACBUACBQAEAWBPCWCADIDSCEAZBPCGDWCPCEAHDCDCDZCEADDPCFDHDEAXCBDEAHDWCTCEAADCDSCTCFDBDXCNDPCHDXCCDBDEACDUCEAHDWCTCEAYBHDHDCDADPCBDEAPCFDADMDEAIDBDSCTCFDEAHDWCTCEAPCIDGDDDXCRCTCGDEACDUCEAQBTCFDADPCBDEARBXCVCWCEAMBCDADADPCBDSCSAEARBTCEAGDTCFDJDTCSCEAPCGDEAHDWCTCEAWBXCBDXCGDHDTCFDEACDUCEAZBIDQC9DXCRCEAFCCDFDZCGDEAXCBDEAHDWCTCEAMBDCZBEAVCCDJDTCFDBDADTCBDHDSAJ9FCWCTCBDEAFCCDFD9DSCEAFCPCFDEASBEAQCFDCDZCTCEACDIDHDEAXCBDEAVACBVAYAQAEAWBPCWCADIDSCEAZBPCGDWCPCEACDDDDDCDGDTCSCEAHDWCTCEAYBHDHDCDADPCBDEADDPCFDHDXCRCXCDDPCHDXCCDBDEAXCBDEAJDXCTCKDEACDUCEAHDWCTCEAIDBDDDFDTCDDPCFDTCSCBDTCGDGDEACDUCEAHDWCTCEAPCFDADTCSCEAUCCDFDRCTCGDSAEARBTCEAKDPCGDEAZCBDCDKDBDEAPCGDEAPCBDEACDIDHDGDDDCDZCTCBDEAQCIDHDEAPCEAFDTCGDDDTCRCHDTCSCEAUCXCVCIDFDTCEAXCBDEAHDWCTCEAMBCDADADXCHDHDTCTCEACDUCEADCBDXCCDBDEAPCBDSCEAZBFDCDVCFDTCGDGDEAMAMBDCZBNASAEAVBPCHDTCFDEAXCBDEAHDWCTCEAKDPCFDQAEAWBPCWCADIDSCEAZBPCGDWCPCEAGDTCFDJDTCSCSASASA99999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999OFFLINE9SPAM9ADDRESS99999999999999999999999999999999999999999999999999999999TYPPI999999999999999999999999999SWTASPAM9DOT9COM9999TYPPI99ZDDIDYD99999999999999999999CKGSVHJSB9ULTHWRTKZBLXQRTZUVLYJDTGUFSIPZDDZWGOLHSUBYVFQDJLJQVID9UYIYZYSNXCKJWHP9WPYVGHICFZRMUWPLH9NNBWGXRXBCOYXCYQHSVGUGJJ9PJBSQLGUHFXAKFYCMLWSEWTDZTQMCJWEXS999999LBYUIRQ9GUXYQSJGYDPKTBZILTCYQIXFFIZECBMECIIXBOVY9SDTYQKGNKBDBLRCOBBQGSJTVGMA9999IOTASPAM9DOT9COM9999TYPPI99CDQASXQKE999999999MMMMMMMMMNZB9999999UME99999999999999"};
    private static final String NULL_HASH = "999999999999999999999999999999999999999999999999999999999999999999999999999999999";
    private static final String[] NULL_HASH_LIST = {NULL_HASH};


    private static Iota iota;
    private static API api;
    private static IXI ixi;
    private static Configuration configuration;
    private static Logger log = LoggerFactory.getLogger(APIIntegrationTests.class);


    @BeforeClass
    public static void setUp() throws Exception {
        if (spawnNode) {
            //configure node parameters
            log.info("IRI integration tests - initializing node.");
            configuration = new Configuration();
            String[] args = {"-p", portStr};
            configuration.put(Configuration.DefaultConfSettings.TESTNET, "true");
            IRI.validateParams(configuration, args);
            TemporaryFolder dbFolder = new TemporaryFolder();
            TemporaryFolder logFolder = new TemporaryFolder();
            dbFolder.create();
            logFolder.create();
            configuration.put(Configuration.DefaultConfSettings.DB_PATH.name(), logFolder.getRoot().getAbsolutePath());
            configuration.put(Configuration.DefaultConfSettings.DB_LOG_PATH.name(), logFolder.getRoot().getAbsolutePath());
            configuration.put(Configuration.DefaultConfSettings.MWM, "1");

            //create node
            iota = new Iota(configuration);
            ixi = new IXI(iota);
            api = new API(iota, ixi);

            //init
            try {
                iota.init();
                api.init();
                ixi.init(configuration.string(Configuration.DefaultConfSettings.IXI_DIR));
            } catch (final Exception e) {
                log.error("Exception during IOTA node initialisation: ", e);
            }
            log.info("IOTA Node initialised correctly.");
        }
    }

    @AfterClass
    public static void tearDown() throws Exception {
        if (spawnNode) {
            try {
                ixi.shutdown();
                api.shutDown();
                iota.shutdown();
            } catch (final Exception e) {
                log.error("Exception occurred shutting down IOTA node: ", e);
                System.exit(-1);
            }
        }
    }

    static {
        RestAssured.port = Integer.parseInt(portStr);
        RestAssured.baseURI = hostName;

        ResponseSpecBuilder builder = new ResponseSpecBuilder();
        builder.expectStatusCode(200);
        builder.expectBody(containsString("duration"));
        responseSpec = builder.build();
    }

    /**
     * Tests can choose to use this method instead of the no-args given() static method
     * if they want to manually specify custom timeouts.
     *
     * @param socket_timeout     The Remote host response time.
     * @param connection_timeout Remote host connection time & HttpConnectionManager connection return time.
     * @return The RequestSpecification to use for the test.
     */
    private static RequestSpecification given(int socket_timeout, int connection_timeout) {
        return RestAssured.given().config(RestAssured.config()
            .httpClient(HttpClientConfig.httpClientConfig()
                .setParam("http.conn-manager.timeout", (long) connection_timeout)
                .setParam("http.connection.timeout", connection_timeout)
                .setParam("http.socket.timeout", socket_timeout)))
                .contentType("application/json").header("X-IOTA-API-Version", 1);
    }

    private static RequestSpecification given() {
        return given(SOCKET_TIMEOUT, CONNECTION_TIMEOUT);
    }

    private static Gson gson() {
        return new GsonBuilder().create();
    }

    @Test
    public void shouldTestGetNodeInfo() {

        final Map<String, Object> request = new HashMap<>();
        request.put("command", "getNodeInfo");

        given().
            body(gson().toJson(request)).
            when().
            post("/").
            then().
            spec(responseSpec).
            body(containsString("appName")).
            body(containsString("appVersion")).
            body(containsString("duration")).
            body(containsString("jreAvailableProcessors")).
            body(containsString("jreFreeMemory")).
            body(containsString("jreMaxMemory")).
            body(containsString("jreTotalMemory")).
            body(containsString("jreVersion")).
            body(containsString("latestMilestone")).
            body(containsString("latestMilestoneIndex")).
            body(containsString("jreAvailableProcessors")).
            body(containsString("latestSolidSubtangleMilestone")).
            body(containsString("latestSolidSubtangleMilestoneIndex")).
            body(containsString("neighbors")).
            body(containsString("packetsQueueSize")).
            body(containsString("time")).
            body(containsString("tips")).
            body(containsString("transactionsToRequest"));
    }

    @Test
    public void shouldTestGetNeighbors() {

        final Map<String, Object> request = new HashMap<>();
        request.put("command", "getNeighbors");

        given().
            body(gson().toJson(request)).
            when().
            post("/").
            then().
            spec(responseSpec).
            body(containsString("neighbors")).
            body(containsString("address")).
            body(containsString("numberOfAllTransactions")).
            body(containsString("numberOfInvalidTransactions")).
            body(containsString("numberOfNewTransactions"));
    }

    @Test
    public void shouldTestAddNeighbors() {

        final Map<String, Object> request = new HashMap<>();
        request.put("command", "addNeighbors");
        request.put("uris", URIS);

        given().
            body(gson().toJson(request)).
            when().
            post("/").
            then().
            spec(responseSpec).
            body(containsString("addedNeighbors"));
    }

    @Test
    public void shouldTestRemoveNeighbors() {

        final Map<String, Object> request = new HashMap<>();
        request.put("command", "removeNeighbors");
        request.put("uris", URIS);
        given().
            body(gson().toJson(request)).
            when().
            post("/").
            then().
            spec(responseSpec).
            body(containsString("removedNeighbors"));
    }

    @Test
    public void shouldTestGetTips() {

        final Map<String, Object> request = new HashMap<>();
        request.put("command", "getTips");

        given().
            body(gson().toJson(request)).
            when().
            post("/").
            then().
            spec(responseSpec).
            body(containsString("hashes"));
    }

    @Test
    public void shouldTestFindTransactions() {

        final Map<String, Object> request = new HashMap<>();
        request.put("command", "findTransactions");
        request.put("addresses", ADDRESSES);
        given().
            body(gson().toJson(request)).
            when().
            post("/").
            then().
            spec(responseSpec).
            body(containsString("hashes"));
    }

    @Test
    public void shouldTestGetTrytes() {

        final Map<String, Object> request = new HashMap<>();
        request.put("command", "getTrytes");
        request.put("hashes", HASHES);
        given().
            body(gson().toJson(request)).
            when().
            post("/").
            then().
            spec(responseSpec).
            body(containsString("trytes"));
    }

    //@Test
    //empty database returns {"error":"This operations cannot be executed: The subtangle has not been updated yet.","duration":0}
    public void shouldTestGetInclusionStates() {

        final Map<String, Object> request = new HashMap<>();
        request.put("command", "getInclusionStates");
        request.put("transactions", NULL_HASH_LIST);
        request.put("tips", NULL_HASH_LIST);
        given().
            body(gson().toJson(request)).
            when().
            post("/").
            then().
            spec(responseSpec).
            body(containsString("states"));
    }

    //@Test
    //FIXME: pending https://github.com/iotaledger/iri/issues/618
    public void shouldTestGetBalances() {

        final Map<String, Object> request = new HashMap<>();
        request.put("command", "getBalances");
        request.put("addresses", ADDRESSES);
        request.put("threshold", 100);

        given().
            body(gson().toJson(request)).
            when().
            post("/").
            then().
            spec(responseSpec).
            body(containsString("milestone"));
    }

    //@Test
    //empty database returns {"error":"This operations cannot be executed: The subtangle has not been updated yet.","duration":0}
    public void shouldTestGetTransactionsToApprove() {

        final Map<String, Object> request = new HashMap<>();
        request.put("command", "getTransactionsToApprove");
        request.put("depth", 27);

        given().
            body(gson().toJson(request)).
            when().
            post("/").
            then().
            spec(responseSpec).
            body(containsString("trunkTransaction")).
            body(containsString("branchTransaction"));
    }

    @Test
    public void shouldTestBroadcastTransactions() {

        final Map<String, Object> request = new HashMap<>();
        request.put("command", "broadcastTransactions");
        request.put("trytes", TRYTES);

        given().
            body(gson().toJson(request)).
            when().
            post("/").
            then().
            spec(responseSpec).
            log().all().and();
    }

    @Test
    public void shouldTestStoreTransactions() {

        final Map<String, Object> request = new HashMap<>();
        request.put("command", "storeTransactions");
        request.put("trytes", TRYTES);

        given().
            body(gson().toJson(request)).
            when().
            post("/").
            then().
            spec(responseSpec).
            log().all().and();
    }

    @Test
    public void shouldTestattachToTangle() {

        final Map<String, Object> request = new HashMap<>();
        request.put("command", "attachToTangle");
        request.put("trytes", TRYTES);
        request.put("trunkTransaction", NULL_HASH);
        request.put("branchTransaction", NULL_HASH);
        request.put("minWeightMagnitude", configuration.integer(Configuration.DefaultConfSettings.MWM));

        given().
                body(gson().toJson(request)).
                when().
                post("/").
                then().
                spec(responseSpec).
                body(containsString("trytes"));
    }

    @Test
    public void shouldSendTransactionAndFetch() throws InterruptedException {
        //do PoW
        final Map<String, Object> request = new HashMap<>();
        request.put("command", "attachToTangle");
        request.put("trytes", TRYTES);
        request.put("trunkTransaction", NULL_HASH);
        request.put("branchTransaction", NULL_HASH);
        request.put("minWeightMagnitude", configuration.integer(Configuration.DefaultConfSettings.MWM));

        Response response = given().
                body(gson().toJson(request)).
                when().
                post("/");
        response.getBody();
        JsonPath responseJson = response.jsonPath();
        List<Object> trytes = responseJson.getList("trytes");

        //Store
        request.clear();
        request.put("command", "storeTransactions");
        request.put("trytes", trytes);

        given().
                body(gson().toJson(request)).
                when().
                post("/").
                then().
                log().all().and().spec(responseSpec);

        //Fetch
        String temp = (String) trytes.get(0);
        String[] addresses = {temp.substring(2187,2268)}; //extract address from trytes
        request.clear();
        request.put("command", "findTransactions");
        request.put("addresses", addresses);
        response = given().
                body(gson().toJson(request)).
                when().
                post("/");
        response.getBody();
        responseJson = response.jsonPath();
        List<Object> hashes = responseJson.getList("hashes");
        Assert.assertTrue(!hashes.isEmpty());
    }
}