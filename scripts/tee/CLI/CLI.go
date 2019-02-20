package CLI

import (
    "bytes"
    "encoding/json"
    "flag"
    "fmt"
    "io/ioutil"
    "log"
    "net/http"
    "os"
    "strings"
)

type CLI struct {}
var url = "http://localhost:14700"

func printUsage()  {

    fmt.Println("Usage:")
    fmt.Println("\taddattestationinfo -info <detail> -- add attestation information.")
    fmt.Println("\tgetrank -num <NUMBER> -period <WHICH_PERIOD> -- get TEE rank of a specific period.")
    fmt.Println("\tprinthcgraph -period <WHICH_PEIOD> -- output HCGraph information.")

}

func isValidArgs()  {
    if len(os.Args) < 2 {
        printUsage()
        os.Exit(1)
    }
}

func (cli *CLI) addAttestationInfo(info []string)  {
//storeMessage
//http.PostForm
//    req := urllib.Post(url)
//    req.Param("command","storeMessage")
//    req.Param("address",info[0])
//    req.Param("message",info[1])
//    req.Header("content-type","application/json")
//    req.Header("X-IOTA-API-Version","1")
//    str, err := req.String()

    data := "{\"command\":\"storeMessage\",\"address\":" + info[0] + ",\"message\":" + info[1] + "}"
    r := doPost([]byte(data))
    fmt.Println(r)
}

func (cli *CLI) getRank(num string, period []string)  {
    data := "{\"command\":\"getBlocksInPeriod\",\"period\":" + period[0] + "}"
    r := doPost([]byte(data))
    var result []string
    err := json.Unmarshal(r, &result)
    if err != nil {
        log.Fatal(err)
        fmt.Println(r)
    }
    fmt.Println(result)
}

func (cli *CLI) printHCGraph(period string){
    data := "{\"command\":\"getBlocksInPeriod\",\"period\":" + period + "}"
    r := doPost([]byte(data))
    fmt.Println(r)
}

func doPost(d []byte) []byte{
    req,err := http.NewRequest("POST",url, bytes.NewBuffer(d))
    if err != nil {
        // error
        log.Panic(err)
    }
    req.Header.Set("Content-Type", "application/json")
    req.Header.Set("X-IOTA-API-Version", "1")

    client := &http.Client{}
    res,err := client.Do(req)
    if err != nil {
        log.Panic(err)
    }

    defer res.Body.Close()
    r,err := ioutil.ReadAll(res.Body)
    if err != nil {
        log.Panic(err)
    }
    return r
}

func (cli *CLI) Run()  {

    isValidArgs()

    addAttestationInfoCmd := flag.NewFlagSet("addattestationinfo",flag.ExitOnError)
    getRankCmd := flag.NewFlagSet("getrank",flag.ExitOnError)
    printHCGraphCmd := flag.NewFlagSet("printhcgraph",flag.ExitOnError)

    flagInfo := addAttestationInfoCmd.String("info","","TEE info......")
    flagNum := getRankCmd.String("num","","number of ranked nodes......")
    flagPeriod := getRankCmd.String("period","","which period to rank......")
    flagPeriod1 := printHCGraphCmd.String("period","","which period to rank......")

    switch os.Args[1] {
    case "addattestationinfo":
        err := addAttestationInfoCmd.Parse(os.Args[2:])
        if err != nil {
            log.Panic(err)
        }
    case "getrank":
        err := getRankCmd.Parse(os.Args[2:])
        if err != nil {
            log.Panic(err)
        }
    case "printhcgraph":
        err := printHCGraphCmd.Parse(os.Args[2:])
        if err != nil {
            log.Panic(err)
        }
    default:
        printUsage()
        os.Exit(1)
    }

    if addAttestationInfoCmd.Parsed() {
        if *flagInfo == ""{
            printUsage()
            os.Exit(1)
        }

        info := strings.Split(*flagInfo,",")
        cli.addAttestationInfo(info)
    }

    if getRankCmd.Parsed() {
        if *flagNum == "" ||  *flagPeriod == "" {
            printUsage()
            os.Exit(1)
        }
        period := strings.Split(*flagPeriod, " ")
        cli.getRank(*flagNum, period)
    }

    if printHCGraphCmd.Parsed() {
        if *flagPeriod1 == "" {
            printUsage()
            os.Exit(1)
        }
        period := *flagPeriod1
        cli.printHCGraph(period)
    }
}
