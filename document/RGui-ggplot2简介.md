# 1  ggplot2简介
## 1.1 ggplot2组成
- 数据(data)
- 映射(mapping)
- 几何对象(geom)
- 统计变换(status)
- 标度(scale)
- 坐标系(coord)
- 分面(facet)
## 1.2 ggplot2绘图函数
- qplot(): 顾名思义，快速绘图；
- ggplot()：此函数才是ggplot2的精髓，远比qplot()强大，可以一步步绘制十分复杂的图形。
## 1.3 ggplot2基本开发步骤
- 开始-->初始化-->绘制图层-->调整数据相关图形元素-->调整数据无关图形元素-->绘制完毕

# 2  安装及其下载
- 这里提供三种安装方式供选择：
    - 1.直接安装tidyverse，一劳永逸（推荐,数据分析大礼包）
        - `install.packages("tidyverse")`
    - 2.直接安装ggplot2
         - `install.packages("ggplot2")`
    - 3.从Github上安装最新的版本，先安装devtools(如果没安装的话)
        - `devtools::install_github("tidyverse/ggplot2")`
- 加载
    - `library(ggplot2)`
    
# 3 choose
- How to Start Plotting from Zero
    - ggplot2的每一个绘图必须包含的两部分是数据(ggplot(data)) + 图层(layer: geom_*() or stat_*())。
    - 可选部分则有风格(theme()) + 坐标(coord_*()) + 映射方法scale_*() + 分面facet_*() + 标签labs() 。
    - 作者封装了复杂的底层绘图包来实现许多精美的绘图函数，而我们需要做的就是为这些函数的aes参数分配（mapping，使用aes*()系列函数实现）合适的数据（一个data.frame的某一列），最后再根据需要调制风格。并且，通过加号的重载，ggplot2的绘图过程十分有趣，就像在画布上使用+不断添加图层&滤镜。
- Choose your figure type
    - 画图的第一步是确定你想要的图的类型，折线图、散点图、热图、柱状图、饼图等等。持有的数据与希望观察的现象共同决定这一点，不同的图的类型有不同的表现力，能够展现数据的不同侧面，因此我们需要明确自己的目的。
- Choose the corresponding function
    - 在大体决定了要画什么图之后，我们需要根据变量是离散还是连续等等来选择对应的绘图函数。这一步需要你熟悉常见的以geom与stat开头的系列函数，也是初学十分困难的地方。官方提供的cheat sheet是一个很好的帮助。它根据变量类型对绘图函数进行分类，可以有效地帮助选择。

# 4 图形类型
- 根据数据集，ggplot2提供不同的方法绘制图形，主要是为下面几类数据类型提供绘图方法：
    - 一个变量x: 连续或离散
    - 两个变量x&y：连续和(或)离散
    - 连续双变量分布x&y: 都是连续
    - 误差棒
    - 地图
    - 三变量
    
## 4.1 一个变量x: 连续或离散
- 使用数据集mtcars
- 构建函数集1：
    - load the data set:
        - `data(mtcars)`
        - `df <- mtcars[, c("mpg","cyl","wt")]`
    - 将cyl转为因子型factor:
        - `df$cyl <- as.factor(df$cyl)`
        - `head(df)`
- 绘图：
    - 1.散点图：
        - `qplot(x=mpg, y=wt, data=df, geom = "point")`
        - 添加平滑曲线：`qplot(x=mpg, y=wt, data = df, geom = c("point", "smooth"))`
        - 将变量cyl映射给颜色和形状：
`qplot(x=mpg, y=wt, data = df, colour=cyl, shape=cyl)`
- 构造函数集2：
    - `set.seed(1234)`
    - `wdata <- data.frame(
  sex=factor(rep(c("F", "M"), each=200)),
  weight=c(rnorm(200, 55), rnorm(200, 58))`
)
    - `head(wdata)`
- 绘图：
    - 2.线箱图：
        - `plot(sex, weight, data = wdata, geom = "boxplot", fill=sex)`
    - 3.小提琴图：
        - `qplot(sex, weight, data = wdata, geom = "violin")`
    - 4.点图
        - `qplot(sex, weight, data = wdata, geom = "dotplot", stackdir="center", binaxis="y", dotsize=0.5, color=sex)
`
    - 5.直方图
        - `qplot(weight, data = wdata, geom = "histogram", fill=sex)`
    - 6.密度图
        - `qplot(weight, data = wdata, geom = "density", color=sex, linetype=sex)` 
    - 7.ggplot()实现
        - `ggplot(data=df, aes(x=mpg, y=wt))+
  geom_point()`

## 4.2 两个变量x&y：连续和(或)离散
- 使用数据集wdata
- 构建函数集：
    - `library(plyr)`
    - `mu <- ddply(wdata, "sex", summarise, grp.mean=mean(weight))`
- 先绘制一个图层a:
    - `a <- ggplot(wdata, aes(x=weight))`
- 可能添加的图层有:
    - 对于一个连续变量：:
        - 面积图geom_area()
        - 密度图geom_density()
        - 点图geom_dotplot()
        - 频率多边图geom_freqpoly()
        - 直方图geom_histogram()
        - 经验累积密度图stat_ecdf()
        - QQ图stat_qq()
    - 对于一个离散变量：
        - 条形图geom_bar()
    
### 4.2.1 两个变量：x离散，y连续
- 使用数据集ToothGrowth,其中的变量len(Tooth length)是连续变量，dose是离散变量
    - `ToothGrowth$dose <- as.factor(ToothGrowth$dose)`
    - `head(ToothGrowth)`
- 创建图层
    - `e <- ggplot(data = ToothGrowth, aes(x=dose, y=len))`
- 可添加的图层有：
    - geom_boxplot(): 箱线图
    - geom_violin()：小提琴图
    - geom_dotplot()：点图
    - geom_jitter(): 带状图
    - geom_line(): 线图
    - geom_bar(): 条形图
    
### 4.2.2 两个变量：x、y皆离散
- 使用数据集diamonds中的两个离散变量color以及cut
    - `ggplot(diamonds, aes(cut, color)) +
  geom_jitter(aes(color = cut), size = 0.5)`
- 两个变量：绘制误差图
    - `df <- ToothGrowth`
    - `df$dose <- as.factor(df$dose)
head(df)`
- 创建图层:
    - f <- ggplot(df2, aes(x = dose, y = len, 
                     ymin = len-sd, ymax = len+sd))
- 可添加的图层有：
    - geom_crossbar(): 空心柱，上中下三线分别代表ymax、mean、ymin
    - geom_errorbar(): 误差棒
    - geom_errorbarh(): 水平误差棒
    - geom_linerange()：竖直误差线
    - geom_pointrange()：中间为一点的误差线
    
### 4.2.3 连续双变量分布x&y: 都是连续
- 使用数据集mtcars
- 创建一个ggplot图层:
    - `b <- ggplot(data = mtcars, aes(x=wt, y=mpg))`
- 可能添加的图层有：
    - geom_point():散点图
    - geom_smooth():平滑线
    - geom_quantile():分位线
    - geom_rug():边际地毯线
    - geom_jitter():避免重叠
    - geom_text():添加文本注释
### 4.2.4 两个变量：连续二元分布
- 使用数据集diamonds
    - `head(diamonds[, c("carat", "price")])`
- 创建ggplot图层,后面再逐步添加图层:
    - `c <- ggplot(data=diamonds, aes(carat, price))`
- 可添加的图层有：
    - geom_bin2d(): 二维封箱热图
    - geom_hex(): 六边形封箱图
    - geom_density_2d(): 二维等高线密度图

### 4.2.5 两个变量：连续函数
- 主要是如何通过线来连接两个变量，使用数据集economics
    - head(economics)   
- 先创建一个ggplot图层，后面逐步添加图层
    - `d <- ggplot(data = economics, aes(x=date, y=unemploy))`
- 可添加的图层有：
    - geom_area():面积图
    - geom_line()：折线图
    - geom_step(): 阶梯图
    
### 4.2.6 两个变量：地图绘制
- ggplot2提供了绘制地图的函数geom_map()，依赖于包maps提供地理信息。
- 安装map
    - `install.paclages("maps")`
- 下面将绘制美国地图，数据集采用USArrests:
    - `library(maps)
head(USArrests)`
    - 对数据进行整理一下,添加一列state:
        - `crimes <- data.frame(state=tolower(rownames(USArrests)), USArrests)
head(crimes)`
        - `数据重铸:
library(reshape2)
crimesm <- melt(crimes, id=1)
head(crimesm)`
    - `map_data <- map_data("state")`
    - 绘制地图，使用Murder进行着色
        - `ggplot(crimes, aes(map_id=state))+
  geom_map(aes(fill=Murder), map=map_data)+
  expand_limits(x=map_data$long, y=map_data$lat)`

## 4.3 三个变量
- 使用数据集mtcars，首先绘制一个相关性图
    - `df <- mtcars[, c(1,3,4,5,6,7)]
head(df)`
    - `cormat <- round(cor(df), 2)
cormat_melt <- melt(cormat)
head(cormat)`
- 创建图层：
    - `g <- ggplot(cormat_melt, aes(x=Var1, y=Var2))`
- 在此基础上可添加的图层有：
    - geom_tile(): 瓦片图
    - geom_raster(): 光栅图，瓦片图的一种，只不过所有的tiles都是一样的大小
- 添加图形元件，将用到函数有：
    - geom_polygon()：添加多边形
    - geom_path(): 路径
    - geom_ribbon(): 带状
    - geom_segment(): 射线、线段
    - geom_curve(): 曲线
    - geom_rect(): 二维矩形