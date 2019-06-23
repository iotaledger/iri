#!/usr/bin/env Rscript
args = commandArgs(trailingOnly=TRUE)

library(ggplot2)
dat<- read.csv(file=args[1],head=TRUE,sep=",")
# ggplot() +
# geom_point(data=df, aes(x = x, y = y, colour = data) ) +
# geom_smooth() +
# theme(legend.position="bottom", legend.text=element_text(size=15))

ggplot(dat, aes(x=num_txn, y=TPS, linetype=cluster_size, color=cluster_size)) +
    geom_line() + theme_bw()

ggsave(file=args[2])
