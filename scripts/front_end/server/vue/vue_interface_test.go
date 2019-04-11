package vue

import (
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

var o OCli
const MyURL = "127.0.0.1:14700"
var nodesCache = make([]string,3)
var index = 0
var number = 1

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
    resp := o.AddAttestationInfoFunction(bytes)
    if resp.Code != 1 {
        t.Errorf("failed to call AddAttestationInfoFunction: %s\n", resp.Message)
    }

    bytes1 := []byte("{\"Attester\":\"192.168.130.102\",\"Attestee\":\"192.168.130.120\",\"Score\":\"2\"}")
    resp1 := o.AddAttestationInfoFunction(bytes1)
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
        for i := 0;i < len(nodesCache);i += 3 {
            data = append(data,`%7B%22attester%22%3A%22` + nodesCache[i+0] +
            `%22%2C%22attestee%22%3A%22` + nodesCache[i+1] + `%22%2C%22score%22%3A` + nodesCache[i+2] +`%7D`)
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
    resp := o.GetRankFunction(bytes)
    if resp.Code != 1 {
        t.Errorf("failed to call GetRankFunction: %s\n", resp.Message)
    }

    result:=checkData(resp.Data.DataCtx)
    if result == 1{
        fmt.Println("Data detection correct")
    }else {
        t.Error("Data detection failure")
    }

}

func handleData(bodyBytes []byte){
    nodes := make([]string,15)
    data1 := strings.Split(string(bodyBytes),",")
    nodes = strings.Split(data1[2],"%22")
    reg := regexp.MustCompile(`[%ACD]`)
    data2 := reg.ReplaceAllString(nodes[14], "0")
    nodes[14] = strings.Split(data2,"0")[2]

    if number == 1 {
        for k := range nodes {
            if k == 7 || k == 11 || k == 14 {
                nodesCache[index] = nodes[k]
                index++
            }
        }
        number ++
    }else{
        for k := range nodes {
            if k == 7 || k == 11 || k == 14 {
                nodesCache=append(nodesCache,nodes[k])
            }
        }
    }

}

func checkData(a interface{}) int{
    str := fmt.Sprintf("%v", a)
    reg := regexp.MustCompile(`[{\[\]}]`)
    ss := reg.ReplaceAllString(str, " ")
    var tee teectx
    var tees []teectx
    s1 := strings.Fields(ss)
    for k := range s1{
        if s1[k] == "0" {
            continue
        }else {
            if k % 3 == 0 {
                tee.Attester = s1[k]
            } else if k % 3 == 1 {
                tee.Attestee = s1[k]
            } else if k % 3 == 2{
                tee.Score = s1[k]
                tees = append(tees,tee)
            }
        }
    }
    var s []string
    for k := range tees{
        s = append(s,tees[k].Attester)
        s = append(s,tees[k].Attestee)
        s = append(s,tees[k].Score)
    }
    if len(s) != len(nodesCache){
        return 0
    }
    var j int
    for i:= 0;i < len(s);i ++{
        for j = 0;j<len(nodesCache);j ++{
            if s[i] == nodesCache[j]{
                break
            }
        }
        if j == len(nodesCache){
            return 0
        }
    }

    return 1
}

type teectx struct {
    Attester string
    Attestee string
    Score    string
}


