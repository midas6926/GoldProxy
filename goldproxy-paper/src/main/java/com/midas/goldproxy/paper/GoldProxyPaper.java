package com.midas.goldproxy.paper;

import org.bukkit.plugin.java.JavaPlugin;
import org.slf4j.Logger;

public class GoldProxyPaper extends JavaPlugin {

    private Logger logger;

    @Override
    public void onEnable() {
        this.logger = getSLF4JLogger();
        logger.info("GoldProxyPaper enabled");
        // TODO: setup Redis client, register listeners
        getServer().getPluginManager().registerEvents(new PlayerReportListener(this), this);
    }

    @Override
    public void onDisable() {
        logger.info("GoldProxyPaper disabled");
    }

    public Logger getLoggerSLF4J() { return logger; }
}
