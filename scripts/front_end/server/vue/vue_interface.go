package vue

import (
	"encoding/json"
	nr "github.com/wunder3605/noderank"
)
type Message struct {
	Code int64
	Message string
	Data DataTee
}
type DataTee struct {
	DataScore interface{}
	DataCtx interface{}
}

type OCli struct {
}

type AddAtInfo interface {
	AddAttestationInfoFunction(_data []byte)Message
	GetRankFunction(_data []byte) Message
}

func (o *OCli)AddAttestationInfoFunction(_data []byte )Message{
	mess:=Message{}
	m:=make(map[string]string)
	err := json.Unmarshal(_data, &m)

	if err!=nil{
		mess=Message{Code:0,Message:"Type conversion exception"}
		return mess
	}
	info:=make([]string,3)
	info[0]=m["Attester"]
	info[1]=m["Attestee"]
	info[2]=m["Score"]
	err1:=nr.AddAttestationInfo("","",info)
	if err1!=nil{
		mess=Message{Code:0,Message:"Failed to add node"}
		return mess
	}
	mess=Message{Code:1,Message:"Node added successfully"}
	return mess
}

type parameter struct {
	Period int64 `json:"period"`
	NumRank int64 `json:"numRank"`
}

func (o *OCli)GetRankFunction(_data []byte)Message{
	mess:=Message{}
	var para parameter
	err:=json.Unmarshal(_data,&para)
	if err!=nil{
		mess=Message{Code:0,Message:"Type conversion exception"}
		return mess
	}

	teescore,teectx,err1:=nr.GetRank("",para.Period,para.NumRank)
	if teectx==nil||err1!=nil||teescore==nil{
		mess=Message{Code:0,Message:"Failed to query node data"}
		return mess
	}
	data:=DataTee{teescore,teectx}
	mess=Message{Code:1,Message:"Query node data successfully",Data:data}
	return mess
}