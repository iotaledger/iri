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
		mess=Message{Code:0,Message:"类型转换异常"}
		return mess
	}
	info:=make([]string,3)
	info[0]=m["Attester"]
	info[1]=m["Attestee"]
	info[2]=m["Score"]
	err1:=nr.AddAttestationInfo(info)
	if err1!=nil{
		mess=Message{Code:0,Message:"节点添加失败"}
		return mess
	}
	mess=Message{Code:1,Message:"节点添加成功"}
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
		mess=Message{Code:0,Message:"类型转换异常"}
		return mess
	}

	teescore,teectx,err1:=nr.GetRank(para.Period,para.NumRank)  //返回值[]teectx
	if teectx==nil||err1!=nil||teescore==nil{
		mess=Message{Code:0,Message:"查询失败"}
		return mess
	}
	data:=DataTee{teescore,teectx}
	mess=Message{Code:1,Message:"查询成功",Data:data}
	return mess
}
