package CLI

import (
	"bytes"
	"encoding/json"
	"flag"
	"fmt"
	"github.com/alixaxel/pagerank"
	"io/ioutil"
	"log"
	"net/http"
	url2 "net/url"
	"os"
	"sort"
	"strconv"
	"strings"
)

type CLI struct{}

type Response struct {
	Blocks   string `json:"blocks"`
	Duration int    `json:"duration"`
}

type message struct {
	TxNum      int64    `json:"tx_num"`
	TxnContent []rawtxn `json:"txn_content"`
}

type rawtxn struct {
	Attester uint32  `json:"attester"`
	Attestee uint32  `json:"attestee"`
	Score    float64 `json:"score"`
}

type rawtxnslice []rawtxn

var url = "http://localhost:14700"
var addr = "JVSVAFSXWHUIZPFDLORNDMASGNXWFGZFMXGLCJQGFWFEZWWOA9KYSPHCLZHFBCOHMNCCBAGNACPIGHVYX"

func printUsage() {

	fmt.Println("Usage:")
	fmt.Println("\taddattestationinfo -info <detail> -- add attestation information.")
	fmt.Println("\tgetrank -num <NUMBER> -period <WHICH_PERIOD> -- get TEE rank of a specific period.")
	fmt.Println("\tprinthcgraph -period <WHICH_PEIOD> -- output HCGraph information.")

}

func isValidArgs() {
	if len(os.Args) < 2 {
		printUsage()
		os.Exit(1)
	}
}

func (cli *CLI) addAttestationInfo(info []string) {
	raw := new(rawtxn)
	num, err := strconv.ParseUint(info[0], 10, 64)
	raw.Attester = uint32(num)
	num, err = strconv.ParseUint(info[1], 10, 64)
	raw.Attestee = uint32(num)
	num, err = strconv.ParseUint(info[2], 10, 64)
	raw.Score = float64(num)
	m := new(message)
	m.TxNum = 1
	m.TxnContent = []rawtxn{*raw}
	ms, err := json.Marshal(m)
	if err != nil {
		log.Panic(err)
	}

	data := "{\"command\":\"storeMessage\",\"address\":" + addr + ",\"message\":" + url2.QueryEscape(string(ms[:])) + ",\"tag\":\"TEE\"}"
	fmt.Println("data : " + data)
	r := doPost([]byte(data))
	fmt.Println(r)
}

func (cli *CLI) getRank(num string, period []string) {
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

	var msgArr []string
	err = json.Unmarshal([]byte(result.Blocks), &msgArr)
	if err != nil {
		log.Panic(err)
	}

	graph := pagerank.NewGraph()

	for _, m2 := range msgArr {
		msgT, err := url2.QueryUnescape(m2)
		if err != nil {
			log.Panicln(err)
		}
		var msg message
		err = json.Unmarshal([]byte(msgT), &msg)
		if err != nil {
			log.Panic(err)
		}

		rArr := msg.TxnContent
		for _, r := range rArr {
			graph.Link(r.Attester, r.Attestee, r.Score)
		}
	}

	var rst []rawtxn
	graph.Rank(0.85, 0.0001, func(attestee uint32, score float64) {
		fmt.Println("attestee ", attestee, " has a score of", score)
		tee := rawtxn{0, attestee, score}
		rst = append(rst, tee)
	})
	sort.Sort(rawtxnslice(rst))
	fmt.Println(rst[0:7])
}

func (cli *CLI) printHCGraph(period string) {
	data := "{\"command\":\"getBlocksInPeriod\",\"period\":" + period + "}"
	r := doPost([]byte(data))
	fmt.Println(r)
}

func doPost(d []byte) []byte {
	req, err := http.NewRequest("POST", url, bytes.NewBuffer(d))
	if err != nil {
		// error
		log.Panic(err)
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-IOTA-API-Version", "1")

	client := &http.Client{}
	res, err := client.Do(req)
	if err != nil {
		log.Panic(err)
	}

	defer res.Body.Close()
	r, err := ioutil.ReadAll(res.Body)
	if err != nil {
		log.Panic(err)
	}
	return r
}

func (r rawtxnslice) Len() int {
	return len(r)
}

func (r rawtxnslice) Swap(i, j int) {
	r[i], r[j] = r[j], r[i]
}

func (r rawtxnslice) Less(i, j int) bool {
	return r[j].Score < r[i].Score
}

func (cli *CLI) Run() {

	isValidArgs()

	addAttestationInfoCmd := flag.NewFlagSet("addattestationinfo", flag.ExitOnError)
	getRankCmd := flag.NewFlagSet("getrank", flag.ExitOnError)
	printHCGraphCmd := flag.NewFlagSet("printhcgraph", flag.ExitOnError)

	flagInfo := addAttestationInfoCmd.String("info", "", "TEE info......")
	flagNum := getRankCmd.String("num", "", "number of ranked nodes......")
	flagPeriod := getRankCmd.String("period", "", "which period to rank......")
	flagPeriod1 := printHCGraphCmd.String("period", "", "which period to rank......")

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
		if *flagInfo == "" {
			printUsage()
			os.Exit(1)
		}

		info := strings.Split(*flagInfo, ",")
		cli.addAttestationInfo(info)
	}

	if getRankCmd.Parsed() {
		if *flagNum == "" || *flagPeriod == "" {
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
