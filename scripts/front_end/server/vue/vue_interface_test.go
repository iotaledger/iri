package vue

import (
	"fmt"
	"io"
	"io/ioutil"
	"log"
	"net"
	"net/http"
	"net/http/httptest"
	"os"
	"testing"
)

var o OCli
const MyURL = "127.0.0.1:14700"
var nodesCache = make([]string, 3)

func TestAddAttestationInfoFunction(t *testing.T) {
	l, err := net.Listen("tcp", MyURL)
	if err != nil {
		log.Fatal(err)
	}
	ts := httptest.NewUnstartedServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		//w.Write(weatherRespBytes)
		if r.Method != "POST" {
			t.Errorf("Except 'Get' got '%s'", r.Method)
		}

		if r.URL.EscapedPath() != "/" {
			t.Errorf("Except to path '/person',got '%s'", r.URL.EscapedPath())
		}

		bodyBytes, _ := ioutil.ReadAll(r.Body)
		nodesCache = append(nodesCache, string(bodyBytes))
	}))

	_ = ts.Listener.Close()
	ts.Listener = l
	ts.Start()
	defer ts.Close()

	bytes := []byte("{\"Attester\":\"192.168.130.102\",\"Attestee\":\"192.168.130.129\",\"Score\":\"1\"}")
	resp := o.AddAttestationInfoFunction(bytes)
	if resp.Code != 1 {
		fmt.Printf("failed to call AddAttestationInfoFunction: %s\n", resp.Message)
		os.Exit(-1)
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
		// TODO: replace these hard coded data with data in 'nodes_cache'
		str := `{"blocks":"[\"%7B%22tee_num%22%3A1%2C%22tee_content%22%3A%5B%7B%22attester%22%3A%22192.168.130.102%22`+
			   `%2C%22attestee%22%3A%22192.168.130.129%22%2C%22score%22%3A1%7D%5D%7D\"]","duration":5}`
		_, _ = io.WriteString(w, str)
	}))

	_ = ts.Listener.Close()
	ts.Listener = l
	ts.Start()
	defer ts.Close()

	bytes := []byte("{\"period\":1,\"numRank\":100}")
	resp := o.GetRankFunction(bytes)
	if resp.Code != 1 {
		fmt.Printf("failed to call GetRankFunction: %s\n", resp.Message)
		os.Exit(-1)
	}
}