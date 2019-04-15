package main

import (
	v "./vue"
	"encoding/json"
	"fmt"
	"io/ioutil"
	"net/http"
	"log"
)

func main() {
	http.HandleFunc("/AddNode", AddNode)
	http.HandleFunc("/QueryNodes", QueryNodes)
	http.HandleFunc("/QueryNodeDetail", QueryNodeDetail)
	err := http.ListenAndServe("0.0.0.0:8000", nil)

	if err != nil {
		fmt.Println(err)
	}
}

func AddNode(writer http.ResponseWriter, request *http.Request) {
	var o v.OCli
	body, _ := ioutil.ReadAll(request.Body)
	response := o.AddAttestationInfoFunction(body)

	if err := json.NewEncoder(writer).Encode(response); err != nil {
		fmt.Println(err)
	}
}

func QueryNodes(writer http.ResponseWriter, request *http.Request) {
	var o v.OCli
	body, _ := ioutil.ReadAll(request.Body)
	response := o.GetRankFunction(body)

	if err := json.NewEncoder(writer).Encode(response); err != nil {
		fmt.Println(err)
	}
}

func QueryNodeDetail(writer http.ResponseWriter, request *http.Request) {
	var detailRequest *v.NodeDetailRequest
	if err := json.NewDecoder(request.Body).Decode(&detailRequest);err != nil {
		fmt.Println(err)
		log.Fatal(err)
		request.Body.Close()
	}

	var o v.OCli
	response := o.QueryNodeDetail(detailRequest)
	if err := json.NewEncoder(writer).Encode(response); err != nil {
		fmt.Println(err)
	}
}
