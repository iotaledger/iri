暴露出去的接口，供客户端调用，监听的端口是0.0.0.0：8000，实现添加结点和查询数据
（1）添加结点（AddAttestationInfo）：url是AddNode，参数需要字符串切片返回Message结构体（此方法不管成功失败，data都没有数据，通过code （0/1）字段来验证成功与否）
（2）查询信息（GetRank）：url是QueryData，参数需要两个int64;  参数一：查询的阶段，参数二：截取到的下标数，
       返回Message结构体（成功后data中封装的是两个结构体数据）