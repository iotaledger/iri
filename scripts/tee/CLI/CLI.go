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
    "strconv"
    "strings"
)

type CLI struct {}

type Response struct {
    Blocks   string   `json:"blocks"`
    Duration int      `json:"duration"`
}

type block struct{
    TxnContent []content  `json:"txn_content"`
    TxNum      int        `json:"tx_num"`
}

type content struct{
    Inputs []ledger   `json:"inputs"`
    Outputs []ledger  `json:"outputs"`
}

type ledger struct{
    UserAccount string  `json:"userAccount"`
    Amount      int     `json:"amount"`
}

type message struct {
    TxNum int64            `json:"tx_num"`
    TxnContent []rawtxn    `json:"txn_content"`
}

type rawtxn struct {
    From string   `json:"from"`
    To string     `json:"to"`
    Amount int    `json:"amnt"`
}

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

    raw := new(rawtxn)
    raw.From = info[1]
    raw.To = info[2]
    raw.Amount,_ = strconv.Atoi(info[3])
    m := new(message)
    m.TxNum = 1
    m.TxnContent = []rawtxn{*raw}
    ms,err := json.Marshal(m)
    if err != nil {
        log.Panic(err)
    }

    data := "{\"command\":\"storeMessage\",\"address\":" + info[0] + ",\"message\":" + string(ms) + "}"
    r := doPost([]byte(data))
    fmt.Println(r)
}

func (cli *CLI) getRank(num string, period []string)  {
    data := "{\"command\":\"getBlocksInPeriodStatement\",\"period\":" + period[0] + "}"
    r := doPost([]byte(data))
    var result Response
    err := json.Unmarshal(r, &result)
    if err != nil {
        log.Fatal(err)
        fmt.Println(r)
    }
    fmt.Println(result.Duration)
    fmt.Println(result.Blocks)
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
        period := strings.Split(*flagPeriod, ",")
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
