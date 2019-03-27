#!/usr/bin/env Rscript
args = commandArgs(trailingOnly=TRUE)

library(ggplot2)
dat<- read.csv(file=args[1],head=TRUE,sep=",")
# ggplot() +
# geom_point(data=df, aes(x = x, y = y, colour = data) ) +
# geom_smooth() +
# theme(legend.position="bottom", legend.text=element_text(size=15))

pc1 <- ggplot(dat, aes(x = num_txn, y = TPS, color = cluster_size))
pc1 + geom_point()

pc2 <- pc1 +
  geom_smooth(mapping = aes(linetype = "r2"),
              method = "lm",
              formula = y ~ x + log(x), se = FALSE,
              color = "red")
pc2 + geom_point()

ggsave(file=args[2])
