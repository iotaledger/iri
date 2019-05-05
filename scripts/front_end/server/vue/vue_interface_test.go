package vue

import (
    "encoding/json"
    "fmt"
    "io"
    "io/ioutil"
    "log"
    "net"
    "net/http"
    "net/http/httptest"
    "regexp"
    "strings"
    "testing"
)

const MyURL = "127.0.0.1:14700"
var requests []AddNodeRequest
var o OCli
func TestAddAttestationInfoFunction(t *testing.T) {
    l, err := net.Listen("tcp", MyURL)
    if err != nil {
        log.Fatal(err)
    }
    ts := httptest.NewUnstartedServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
        w.WriteHeader(http.StatusOK)
        if r.Method != "POST" {
            t.Errorf("Except 'Get' got '%s'", r.Method)
        }

        if r.URL.EscapedPath() != "/" {
            t.Errorf("Except to path '/person',got '%s'", r.URL.EscapedPath())
        }

        bodyBytes, _ := ioutil.ReadAll(r.Body)
        handleData(bodyBytes)

    }))

    _ = ts.Listener.Close()
    ts.Listener = l
    ts.Start()
    defer ts.Close()

    bytes := []byte("{\"Attester\":\"192.168.130.101\",\"Attestee\":\"192.168.130.110\",\"Score\":\"1\"}")
    var addNodeRequest *AddNodeRequest
    json.Unmarshal(bytes,&addNodeRequest)
    resp:=o.AddAttestationInfoFunction(addNodeRequest)
    if resp.Code != 1 {
        t.Errorf("failed to call AddAttestationInfoFunction: %s\n", resp.Message)
    }

    bytes1 := []byte("{\"Attester\":\"192.168.130.102\",\"Attestee\":\"192.168.130.120\",\"Score\":\"2\"}")
    var addNodeRequest1 *AddNodeRequest
    json.Unmarshal(bytes1,&addNodeRequest1)
    resp1:=o.AddAttestationInfoFunction(addNodeRequest1)
    if resp1.Code != 1 {
        t.Errorf("failed to call AddAttestationInfoFunction: %s\n", resp1.Message)
    }

}

func TestGetRankFunction(t *testing.T) {
    l, err := net.Listen("tcp", MyURL)
    if err != nil {
        log.Fatal(err)
    }
    ts := httptest.NewUnstartedServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
        if r.Method != "POST" {
            t.Errorf("Except 'Get' got '%s'", r.Method)
        }

        if r.URL.EscapedPath() != "/" {
            t.Errorf("Except to path '/person',got '%s'", r.URL.EscapedPath())
        }

        w.WriteHeader(http.StatusOK)
        w.Header().Set("Content-Type", "application/json")

        var str string
        data := make([]string,1)
        for i := 0;i < len(requests);i ++ {
            data = append(data,`%7B%22attester%22%3A%22` + requests[i].Attester +
                `%22%2C%22attestee%22%3A%22`+requests[i].Attestee+`%22%2C%22score%22%3A`+requests[i].Score+`%7D`)
        }

        str = strings.Trim(strings.Join(data,","),",")
        str = `{"blocks":"[\"%7B%22tee_num%22%3A1%2C%22tee_content%22%3A%5B`+str+`%5D%7D\"]","duration":5}`
        _, _ = io.WriteString(w, str)
    }))

    _ = ts.Listener.Close()
    ts.Listener = l
    ts.Start()
    defer ts.Close()
    bytes := []byte("{\"period\":1,\"numRank\":100}")
    var queryNodesRequest *QueryNodesRequest
    json.Unmarshal(bytes,&queryNodesRequest)
    resp := o.GetRankFunction(queryNodesRequest)
    if resp.Code != 1 {
        t.Errorf("failed to call GetRankFunction: %s\n", resp.Message)
    }
    result := checkData(resp.Data)
    if result == 1{
        fmt.Println("Data detection correct")
    }else {
        t.Error("Data detection failure")
    }
}

func handleData(bodyBytes []byte){
    data1 := strings.Split(string(bodyBytes),",")
    data2 := strings.Split(string(data1[2]),"tee_content%22%3A%5B")
    data3 := strings.Split(data2[1],"%5D")
    data4 := data3[0]
    data5 := strings.Split(data4,"score")
    r := regexp.MustCompile(`%[\d][0-9A-Z]`)
    arg := strings.Split(r.ReplaceAllString(data5[1],"*"),"*")[2]
    str := strings.Replace(strings.Replace(strings.Replace(strings.Replace(strings.Replace(data3[0],"%22",
        "\"",-1), "%7B","{",-1),"%3A",":",-1),"%2C",",",-1),"%7D","}",-1)
    var req AddNodeRequest
    json.Unmarshal([]byte(str),&req)
    req.Score = arg
    requests = append(requests,req)

}

func checkData(a interface{}) int{
    str := fmt.Sprintf("%v", a)
    reg := regexp.MustCompile(`[{\[\]}]`)
    ss := reg.ReplaceAllString(str, "")
    s1 := strings.Split(ss," ")[4*len(requests):]
    var tee teectx
    var tees []teectx
    for k := range s1{
        if s1[k] == "0" {
            continue
        }else {
            if k % 3 == 0 {
                tee.Attester = s1[k]
            }else if k % 3 == 1 {
                tee.Attestee = s1[k]
            }else if k % 3 == 2{
                tee.Score = s1[k]
                tees = append(tees,tee)
            }
        }
    }
    if len(tees) != len(requests){
        return 0
    }

    for k1 := range tees{
        k2 := 0
        for k2 = range requests {
            if tees[k1].Attester == requests[k2].Attester&&
                tees[k1].Attestee == requests[k2].Attestee&&
                tees[k1].Score == requests[k2].Score{
                break
            }
        }
        if k2 == len(requests){
            return 0
        }
    }
    return 1
}

type teectx struct {
    Attester string `json:"attester,omitempty"`
    Attestee string `json:"attestee,omitempty"`
    Score    string `json:"score,omitempty"`
}



