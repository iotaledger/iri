package CLI

import (
	"bytes"
	"encoding/json"
	"flag"
	"fmt"
	"github.com/awalterschulze/gographviz"
	"github.com/kylelemons/go-gypsy/yaml"
	"io/ioutil"
	"log"
	"net/http"
	url2 "net/url"
	"os"
	"github.com/triasteam/pagerank"
	"sort"
	"strconv"
	"strings"
	"time"
)

type CLI struct{}

type Response struct {
	Blocks   string `json:"blocks"`
	Duration int    `json:"duration"`
}

type message struct {
	TeeNum     int64    `json:"tee_num"`
	TeeContent []rawtxn `json:"tee_content"`
}

type rawtxn struct {
	Attester string  `json:"attester"`
	Attestee string  `json:"attestee"`
	Score    float64 `json:"score"`
}

type rawtxnslice []rawtxn

var url = "http://localhost:14700"
var addr = "JVSVAFSXWHUIZPFDLORNDMASGNXWFGZFMXGLCJQGFWFEZWWOA9KYSPHCLZHFBCOHMNCCBAGNACPIGHVYX"

var (
	file = flag.String("file", "config.yaml", "IOTA CONFIGURATION")
)

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
	raw.Attester = info[0]
	raw.Attestee = info[1]
	num, err := strconv.ParseUint(info[2], 10, 64)
	raw.Score = float64(num)
	m := new(message)
	m.TeeNum = 1
	m.TeeContent = []rawtxn{*raw}
	ms, err := json.Marshal(m)
	if err != nil {
		log.Panic(err)
	}

	addr1 := getConfigParam("addr")
	if addr1 == "" {
		log.Fatal(err)
		addr1 = addr
	}

	d := time.Now()
	ds := d.Format("20190227")
	data := "{\"command\":\"storeMessage\",\"address\":" + addr1 + ",\"message\":" + url2.QueryEscape(string(ms[:])) + ",\"tag\":\"" + ds + "TEE\"}"
	fmt.Println("data : " + data)
	r := doPost([]byte(data))
	fmt.Println(r)
}

func (cli *CLI) getRank(period string, numRank int64) []rawtxn {
	data := "{\"command\":\"getBlocksInPeriodStatement\",\"period\":" + period + "}"
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

		rArr := msg.TeeContent
		for _, r := range rArr {
			graph.Link(r.Attester, r.Attestee, r.Score)
		}
	}

	var rst []rawtxn
	graph.Rank(0.85, 0.0001, func(attestee string, score float64) {
		fmt.Println("attestee ", attestee, " has a score of", score)
		tee := rawtxn{"", attestee, score}
		rst = append(rst, tee)
	})
	sort.Sort(rawtxnslice(rst))
	fmt.Println(rst[0:numRank])
	return rst[0:numRank]
}

func (cli *CLI) printHCGraph(period string) {
	data := "{\"command\":\"getBlocksInPeriodStatement\",\"period\":" + period + "}"
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

	graph := gographviz.NewGraph()

	for _, m2 := range msgArr {
		msgT, err := url2.QueryUnescape(m2)
		if err != nil {
			log.Panicln(err)
		}
		fmt.Println("message : " + msgT)
		var msg message
		err = json.Unmarshal([]byte(msgT), &msg)
		if err != nil {
			log.Panic(err)
		}

		rArr := msg.TeeContent
		for _, r := range rArr {
			//score := strconv.FormatUint(uint64(r.Score), 10) // TODO add this score info
			graph.AddNode("G", r.Attestee, nil)
			graph.AddNode("G", r.Attester, nil)
			graph.AddEdge(r.Attester, r.Attestee, true, nil)
			if err != nil {
				log.Panic(err)
			}
		}
	}

	output := graph.String()
	fmt.Println(output)
}

func doPost(d []byte) []byte {
	uri := getConfigParam("url")
	if uri == "" {
		uri = url
	}
	req, err := http.NewRequest("POST", uri, bytes.NewBuffer(d))
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

func getConfigParam(p string) string {
	config, err := yaml.ReadFile(*file)
	if err != nil {
		log.Panicln(err)
	}
	result, err := config.Get(p)
	if err != nil {
		log.Panicln(err)
	}
	return result
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
		rankNum, err := strconv.ParseInt(*flagNum, 10, 64)

		if err != nil {
			log.Panic(err)
		}

		cli.getRank(*flagPeriod, rankNum)
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
