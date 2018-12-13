# 1. Download and install jMeter
You can download the compressed package from the official website, and then use the decompression command to extract it to the corresponding file. After decompression, go to /jmeter/bin in the decompression directory and use the command: `chmod 777 jmeter.sh` to assign jmeter.sh. Insufficient permissions are generated, add sudo in front of the command. Then use the command: `sh jmeter.sh -v` to check if it is available.

# 2. Start jMeter
The command to start jMeter is: `java -jar ApacheJMeter.jar`
 - A graphical interface will appear after successful launch of jMeter:
 ![Image start](image_start.png)

# 3. New test plan
Click file-->new to create a new test plan and name the test plan: IRI_Test_Plan

# 4. Create HTTP Header Manager
Right click Add-->Config Element-->Http Header Manager and name it: IRI Headers
- The header manager has the following functions: It is used to customize the content of the request header of the HTTP request sent by Sampler. HTTP requests from different browsers have different Agents. The correct Refer is required to access certain pages with anti-theft chains... In these cases, the HTTP Header Manager is required to ensure that the HTTP request sent is correct.
- Then you need to fill in the name of the message header and the corresponding value. When I test it, the name is filled with Content-Type. The meaning can be understood as the parameter name and type. Enter the corresponding parameter type below the value. Here I need to transfer the test. The json type, so the value is filled in application/json, and a value of 1 for X-IOTA-API-Version corresponds to 1. 
- Its parameter settings are as shown:
  ![Image header](image_header.png)

# 5. Create a thread group
Right click Add-->Threads(Users)-->Thread Group and name it: spammers
  - Where you need to fill in some parameters as follows:
    - number of Threads (Users): number of virtual users (Set as 4)
    - Ramp-Up Period: Equivalent to the time range of the first loop used by the thread (Set as 5)
    - Loop count: the number of virtual times (Set as 30)
    - What it means is: 1 person requested 30 times in 5 seconds, 10 people requested 120 times in 5 seconds

# 6.Create View Results Tree.......
- Create a View Results Tree and Graph Results and Aggregate Graph at the same level as the Thread Group.
- The View Results Tree contains three module blocks: sampler results, request and response data.
  - After the test is over, if our request is successfully sent to the server, the simulation request in the result tree will be displayed in green. It can be judged by the response status code information in the sampler result or by clicking the request module to view the request we sent. If the request fails, the simulation request will be displayed in red and feedback error
- After the test, Graph Results will display the results of our test in a linear graph; the Aggregate Graph listener can see the results displayed in the table and the graphical results, and the graphical results record the response time.

# 7. Create Http Rquest
  - Create an Http Request under the Thread Group (spammers).
  - Right click Add-->Sampler-->Http Request and name it getTransactionsToApprove. Its IP is set to localhost and the port number is 14700.
  - Server name or IP: This refers to the domain name of the target host you want to access. Note that since http (or https is used, the default is http) is defined in the previous protocol, so when you write the name, don't Add http:// in front, if you are local to write localhost directly, followed by the port number of the target server.
  - At the bottom are Parameters, Body Data, and Files Upload.
    - Parameters refers to the parameters in the function definition, and argument refers to the actual parameters when the function is called. In general, the two can be mixed.
    - Files Upload refers to: Get all the embedded resources from the HTML file: when selected, send the HTTP request and get the response of the HTML file content, then parse the HTML and get all the resources contained in the HTML (image, Flash, etc.): (not selected by default)
    - Body Data refers to the entity data, which is the content of the subject entity in the request message. Generally, we send the request to the server, and the entity body parameters carried can be written here. In general, Body Data is used.
  - Its parameter settings are as shown:
  ![GetTransactionsToApprove request](getTransactionsToApprove_request.png)

# 8. Create If Controller
- Create a logic controller If Controller under Thread Group (spammers) with the condition set to: ${JMeterThread.last_sample_ok}

# 9. Create three Http Requests under If Controller
- Create three HttpRequests under this logic controller IfController, named: attachToTangle, storeTransactions and broadcastTransactions, whose parameters are the same as getTransactionsToApprove except BodyData.
- Its Body Data is:
  -     {
	      "command": "attachToTangle",
    	   "trunkTransaction": "${trunk_1}",
	       "branchTransaction": "${branch_1}",
	       "minWeightMagnitude": ${mwm},
	       "trytes": ["${bundleTrytes}"]
        }
  - 
        {
	        "command": "storeTransactions", 
	        "trytes": ${attached_trytes_1}
        }
  - 
        {
	        "command": "broadcastTransactions", 
	        "trytes": ${attached_trytes_1}
        }

# 10.Create a Summary Report...
- Continue to create the Summary Report and Aggregate Report and View Results in Table under the If Controller.
- Summary Report parameter description:
  - Successes: Save the successful part of the log.
  - Configure: Set the result attribute, which is the result field to save to the file. Generally, the necessary field information can be saved. The more you save, the more impact it will have on the loader's IO.
  - Label: Sampler name (or transaction name).
  - Samples: Number of sampler runs (how many transactions were submitted).
  - Average: The average response time of the request (transaction) in milliseconds.
  - Min: The minimum response time of the request, in milliseconds.
  - Max: The maximum response time of the request, in milliseconds.
  - Std.Dev: The standard deviation of response time.
  - Error%: Transaction error rate.
  - Throughput: throughput rate (TPS).
  - KB/sec: Packet traffic per second in KB.
  - Avg.Bytes: Average data traffic in Bytes
- There is also a CSV Data Set Config to parameterize the request parameters.
  - Its parameter settings are as shown (note: where filename refers to the directory where the mwm.cvs file is located):
    ![CSV Data Set](CSV_Data_Set.png)

# 11.Create a User Defined Variables
- Create a User Defined Variables under getTransactionsToApprove to set the parameters. When the test environment changes, we only need to modify the IP of one place to allow the script to be applied to the test of another environment immediately, without having to modify it one by one.
- Its parameter settings are as follows:
    ![User Defined Variables](User_Defined_Variables.png)

# 12.Create a Debug PostProcessor
- Create a Debug PostProcessor under getTransactionsToApprove, which is a commonly used debugging tool. It can get the relevant information of the sample thread for display. You can view the corresponding sample thread request, response and variables by viewing the response data in the result tree. The parameter can be selected by default.

# 13.Create a Json Extractor
- Continue to create a Json Extractor under getTransactionsToApprove and name it attachedTrytes. Extract data from the data used to extract the json response.
- Its parameter settings are as follows:
  ![Json attachedTrytes](json_attachedTrytes.png)

# 14. Test results
- At this point the test file has been created successfully. Let's take a look at the overall picture after creation: ![Iri panorama](iri_panorama.png)
- Let's test it. The test results are shown in the form of an image:
  ![Image result1](image_result1.png)
  ![Image result1](image_result3.png)
  ![Image result1](image_result5.png)