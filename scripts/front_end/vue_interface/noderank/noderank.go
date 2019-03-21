package noderank

import (
	"bytes"
	"encoding/json"
	"flag"
	"fmt"
	"github.com/awalterschulze/gographviz"
	//"github.com/wunder3605/pagerank"
	"../pagerank"
	"io/ioutil"
	"log"
	"net/http"
	url2 "net/url"
	"sort"
	"strconv"
	"time"
)

type Response struct {
	Blocks   string `json:"blocks"`
	Duration int    `json:"duration"`
}

type message struct {
	TeeNum     int64    `json:"tee_num"`
	TeeContent []teectx `json:"tee_content"`
}

type teectx struct {
	Attester string  `json:"attester"`
	Attestee string  `json:"attestee"`
	Score    float64 `json:"score"`
}

type teescore struct {
	Attestee string  `json:"attestee"`
	Score    float64 `json:"score"`
}

type teescoreslice []teescore


var url = "http://localhost:14700"
var addr = "JVSVAFSXWHUIZPFDLORNDMASGNXWFGZFMXGLCJQGFWFEZWWOA9KYSPHCLZHFBCOHMNCCBAGNACPIGHVYX"

var (
	file = flag.String("file", "noderank/config.yaml", "IOTA CONFIGURATION")
)

func AddAttestationInfo(info []string) error {
	//addr1 string, url string, 参数（暂时写死）
	raw := new(teectx)
	raw.Attester = info[0]
	raw.Attestee = info[1]
	num, err := strconv.ParseUint(info[2], 10, 64)
	if err != nil {
		return err
	}
	raw.Score = float64(num)
	m := new(message)
	m.TeeNum = 1
	m.TeeContent = []teectx{*raw}
	ms, err := json.Marshal(m)
	if err != nil {
		return err
	}

	//if addr1 == "" {
	//	addr1 = addr
	//}
	addr1:=addr

	d := time.Now()
	ds := d.Format("20190227")
	data := "{\"command\":\"storeMessage\",\"address\":" + addr1 + ",\"message\":" + url2.QueryEscape(string(ms[:])) + ",\"tag\":\"" + ds + "TEE\"}"
	fmt.Println("data : " + data)
	r, err := doPost(url, []byte(data))
	if err != nil {
		return err
	}
	fmt.Println(r)
	return nil
}

func GetRank(period int64, numRank int64) ([]teescore, []teectx, error) {
	//uri string, ( 参数，暂时写死，)
	data := "{\"command\":\"getBlocksInPeriodStatement\",\"period\":" + strconv.FormatInt(period, 10) + "}"
	uri:=url
	r, err := doPost(uri, []byte(data))
	if err != nil {
		return nil, nil, err
	}
	var result Response
	err = json.Unmarshal(r, &result)
	if err != nil {
		return nil, nil, err
	}

	var msgArr []string
	err = json.Unmarshal([]byte(result.Blocks), &msgArr)
	if err != nil {
		return nil, nil, err
	}

	graph := pagerank.NewGraph()

	cm := make(map[string]teectx)

	for _, m2 := range msgArr {
		msgT, err := url2.QueryUnescape(m2)
		if err != nil {
			return nil, nil, err
		}
		var msg message
		err = json.Unmarshal([]byte(msgT), &msg)
		if err != nil {
			return nil, nil, err
		}

		rArr := msg.TeeContent

		for _, r := range rArr {
			graph.Link(r.Attester, r.Attestee, r.Score)
			cm[r.Attestee] = teectx{r.Attester, r.Attestee, r.Score}
		}
	}

	var rst []teescore
	var teectxslice []teectx

	graph.Rank(0.85, 0.0001, func(attestee string, score float64) {
		tee := teescore{attestee, score}
		rst = append(rst, tee)
	})
	sort.Sort(teescoreslice(rst))
	if len(rst) < 1 {
		return nil, nil, nil
	}

	endIdx := int64(len(rst))
	if endIdx > numRank {
		endIdx = numRank
	}

	rst = rst[0:endIdx]
	for _, r := range rst {
		teectxslice = append(teectxslice, cm[r.Attestee])
	}

	return rst, teectxslice, nil
}
func PrintHCGraph(uri string, period string) error {
	data := "{\"command\":\"getBlocksInPeriodStatement\",\"period\":" + period + "}"
	r, err := doPost(uri, []byte(data))
	if err != nil {
		return err
	}
	var result Response
	err = json.Unmarshal(r, &result)
	if err != nil {
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
	return nil
}

func doPost(uri string, d []byte) ([]byte, error) {
	if uri == "" {
		uri = url
	}
	req, err := http.NewRequest("POST", uri, bytes.NewBuffer(d))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-IOTA-API-Version", "1")

	client := &http.Client{}
	res, err := client.Do(req)
	if err != nil {
		return nil, err
	}

	defer res.Body.Close()
	r, err := ioutil.ReadAll(res.Body)
	if err != nil {
		return nil, err
	}
	return r, nil
}



func (r teescoreslice) Len() int {
	return len(r)
}

func (r teescoreslice) Swap(i, j int) {
	r[i], r[j] = r[j], r[i]
}

func (r teescoreslice) Less(i, j int) bool {
	return r[j].Score < r[i].Score
}
