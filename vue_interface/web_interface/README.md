
#Dag Web Service Interface


###暴露出去的接口，供客户端调用，实现添加结点和查询数据
* 添加结点（AddAttestationInfo）：url是AddNode，调用AddAttestationInfo方法，参数需要一个字符串切片，该字符串切片
   对应的是teectx结构体中的字段（Attester string  ，Attestee string  ，Score    float64 ），返回前端的是Message结构体
    （此方法不管成功失败，data都没有数据，通过返回的结构体中的字段code（0/1）来验证结点添加是否成功）
* 查询信息（GetRank）：url是QueryData，调用GetRank方法，参数需要两个int64类型; 
    * 参数一：明确查询的阶段
    * 参数二：在该阶段截取到数据的下标数 [0,参数二），如果截取的下标数大于该阶段中的数据总数，
       则默认将数据总数值赋给截取的下标总数，返回前端的是Message结构体（成功后data中封装的是两个结构体数据）
    

###监听的端口号
* 0.0.0.0：8000