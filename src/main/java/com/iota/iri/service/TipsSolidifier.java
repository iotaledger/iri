package com.iota.iri.service;

import com.iota.iri.TransactionValidator;
import com.iota.iri.conf.SolidificationConfig;
import com.iota.iri.controllers.TipsViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.storage.Tangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TipsSolidifier {

    private static final int RESCAN_TX_TO_REQUEST_INTERVAL = 750;
    private static final long LOG_DELAY = 10000l;
    
    private final Logger log = LoggerFactory.getLogger(TipsSolidifier.class);
    private final Tangle tangle;
    private final TipsViewModel tipsViewModel;
    private final TransactionValidator transactionValidator;
    private final SolidificationConfig config;
    
    private boolean shuttingDown = false;
    private Thread solidityRescanHandle;
    

    public TipsSolidifier(final Tangle tangle,
                          final TransactionValidator transactionValidator,
                          final TipsViewModel tipsViewModel,
                          final SolidificationConfig config) {
        this.tangle = tangle;
        this.transactionValidator = transactionValidator;
        this.tipsViewModel = tipsViewModel;
        this.config = config;
    }

    public void init() {
        if (!enabled()) {
            return;
        }
        
        solidityRescanHandle = new Thread(() -> {

            long lastTime = 0;
            while (!shuttingDown) {
                try {
                    scanTipsForSolidity();
                    if (log.isDebugEnabled()) {
                        long now = System.currentTimeMillis();
                        if ((now - lastTime) > LOG_DELAY) {
                            lastTime = now;
                            log.debug("#Solid/NonSolid: {}/{}", tipsViewModel.solidSize(), tipsViewModel.nonSolidSize());
                        }
                    }
                } catch (Exception e) {
                    log.error("Error during solidity scan : {}", e);
                }
                try {
                    Thread.sleep(RESCAN_TX_TO_REQUEST_INTERVAL);
                } catch (InterruptedException e) {
                    log.error("Solidity rescan interrupted.");
                }
            }
        }, "Tip Solidity Rescan");
        solidityRescanHandle.start();
    }

    private void scanTipsForSolidity() throws Exception {
        int size = tipsViewModel.nonSolidSize();
        if (size != 0) {
            Hash hash = tipsViewModel.getRandomNonSolidTipHash();
            boolean isTip = true;
            if (hash != null && TransactionViewModel.fromHash(tangle, hash).getApprovers(tangle).size() != 0) {
                tipsViewModel.removeTipHash(hash);
                isTip = false;
            }
            if (hash != null && isTip && transactionValidator.checkSolidity(hash, false)) {
                //if(hash != null && TransactionViewModel.fromHash(hash).isSolid() && isTip) {
                tipsViewModel.setSolid(hash);
            }
        }
    }

    public void shutdown() {
        if (!enabled()) {
            return;
        }
        
        shuttingDown = true;
        try {
            if (solidityRescanHandle != null && solidityRescanHandle.isAlive()) {
                solidityRescanHandle.join();
            }
        } catch (Exception e) {
            log.error("Error in shutdown", e);
        }

    }
    
    private boolean enabled() {
        return config.isTipSolidifierEnabled();
    }
}
