package vue

import (
	"encoding/json"
	"fmt"
	nr "github.com/wunder3605/noderank"
	"io/ioutil"
	"net/http"
	"strings"
)

type Message struct {
	Code    int64
	Message string
	Data    interface{}
}

type DataTee struct {
	DataScore interface{}
	DataCtx   interface{}
}

type NodeDetailRequest struct {
	RequestUrl  string `json:"requestUrl,omitempty"`
	RequestData string `json:"requestData,omitempty"`
}

type OCli struct {
}

type AddAtInfo interface {
	AddAttestationInfoFunction(_data []byte) Message
	GetRankFunction(_data []byte) Message
}

func (o *OCli) AddAttestationInfoFunction(_data []byte) Message {
	mess := Message{}
	m := make(map[string]string)
	err := json.Unmarshal(_data, &m)

	if err != nil {
		mess = Message{Code: 0, Message: "Type conversion exception"}
		return mess
	}
	info := make([]string, 3)
	info[0] = m["Attester"]
	info[1] = m["Attestee"]
	info[2] = m["Score"]
	err1 := nr.AddAttestationInfo("", "", info)
	if err1 != nil {
		mess = Message{Code: 0, Message: "Failed to add node"}
		return mess
	}
	mess = Message{Code: 1, Message: "Node added successfully"}
	return mess
}

type parameter struct {
	Period  int64 `json:"period"`
	NumRank int64 `json:"numRank"`
}

func (o *OCli) GetRankFunction(_data []byte) Message {
	mess := Message{}
	var para parameter
	err := json.Unmarshal(_data, &para)
	if err != nil {
		mess = Message{Code: 0, Message: "Type conversion exception"}
		return mess
	}

	teescore, teectx, err1 := nr.GetRank("", para.Period, para.NumRank)
	if teectx == nil || err1 != nil || teescore == nil {
		mess = Message{Code: 0, Message: "Failed to query node data"}
		return mess
	}
	data := DataTee{teescore, teectx}
	mess = Message{Code: 1, Message: "Query node data successfully", Data: data}
	return mess
}

func (o *OCli) QueryNodeDetail(request *NodeDetailRequest) Message {
	if (request.RequestUrl == "") {
		return Message{Code: 0, Message: "RequestUrl is empty"}
	}
	result,err := httpGet(request.RequestUrl,request.RequestData);
	if err == nil {
		return Message{Code: 1, Message: "Success!", Data: result}
	}else {
		fmt.Println(err)
		return Message{Code: 0, Message: "Query node's details failed!"}
	}
}

func httpGet(url string, param string) (string, error) {
	payload := strings.NewReader(param)

	req, err := http.NewRequest("GET", url, payload)
	if err != nil {
		return "",err
	}

	req.Header.Add("Content-Type", "application/json")

	res, err := http.DefaultClient.Do(req)
	defer res.Body.Close()
	if err != nil{
		return "",err
	}
	body, _ := ioutil.ReadAll(res.Body)

	return string(body), nil;
}
