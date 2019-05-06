package vue

import (
	"fmt"
	nr "github.com/triasteam/noderank"
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

type AddNodeRequest struct {
	Attester string `json:"attester,omitempty"`
	Attestee string `json:"attestee,omitempty"`
	Score    string `json:"score,omitempty"`
	Address  string `json:"address,omitempty"`
	Url      string `json:"url,omitempty"`
}

type QueryNodesRequest struct {
	Period  int64  `json:"period"`
	NumRank int64  `json:"numRank"`
	Url     string `json:"url,omitempty"`
}

type NodeDetailRequest struct {
	RequestUrl    string `json:"requestUrl,omitempty"`
	RequestData   string `json:"requestData,omitempty"`
	RequestMethod string `json:"requestMethod,omitempty"`
}

type OCli struct {
}

type AddAtInfo interface {
	AddAttestationInfoFunction(_data []byte) Message
	GetRankFunction(_data []byte) Message
}

func (o *OCli) AddAttestationInfoFunction(request *AddNodeRequest) Message {
	mess := Message{}

	info := make([]string, 3)
	info[0] = request.Attester
	info[1] = request.Attestee
	info[2] = request.Score
	err1 := nr.AddAttestationInfo("", request.Url, info)
	if err1 != nil {
		mess = Message{Code: 0, Message: "Failed to add node"}
		return mess
	}
	mess = Message{Code: 1, Message: "Node added successfully"}
	return mess
}

func (o *OCli) GetRankFunction(request *QueryNodesRequest) Message {
	mess := Message{}
	teescore, teectx, err1 := nr.GetRank("", request.Period, request.NumRank)
	if teectx == nil || err1 != nil || teescore == nil {
		mess = Message{Code: 0, Message: "Failed to query node data"}
		return mess
	}
	data := DataTee{teescore, teectx}
	mess = Message{Code: 1, Message: "Query node data successfully", Data: data}
	return mess
}

func (o *OCli) QueryNodeDetail(request *NodeDetailRequest) Message {
	if request.RequestUrl == "" {
		return Message{Code: 0, Message: "RequestUrl is empty"}
	}
	result, err := httpSend(request.RequestUrl, request.RequestData, request.RequestMethod)
	if err == nil {
		return Message{Code: 1, Message: "Success!", Data: result}
	} else {
		fmt.Println(err)
		return Message{Code: 0, Message: "Query node's details failed!"}
	}
}

func httpSend(url string, param string, method string) (string, error) {
	payload := strings.NewReader(param)

	req, err := http.NewRequest(method, url, payload)
	if err != nil {
		return "", err
	}

	req.Header.Add("Content-Type", "application/json")

	res, err := http.DefaultClient.Do(req)
	defer res.Body.Close()
	if err != nil {
		return "", err
	}
	body, _ := ioutil.ReadAll(res.Body)

	return string(body), nil
}
