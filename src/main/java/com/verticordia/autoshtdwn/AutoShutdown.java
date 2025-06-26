package com.verticordia.autoshtdwn;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.util.concurrent.TimeUnit;

@Plugin(id = "autoshtdwn", name = "AutoShutdown", version = "0.0.0-SNAPSHOT", description = "Shuts down the proxy if no players connect for 90 seconds.", authors = {
		"Lucca Pellegrini" })
public class AutoShutdown {

	private final ProxyServer proxy;
	private final Logger logger;

	/** last time we saw ≥1 players */
	private volatile long lastActive = System.currentTimeMillis();

	/** how many seconds idle before shutdown */
	private static final long IDLE_THRESHOLD_SECONDS = 90;

	@Inject
	public AutoShutdown(ProxyServer proxy, Logger logger) {
		this.proxy = proxy;
		this.logger = logger;
	}

	@Subscribe
	public void onProxyInit(ProxyInitializeEvent ev) {
		proxy.getScheduler()
				.buildTask(this, this::checkIdle)
				.repeat(1, TimeUnit.SECONDS)
				.schedule();
		logger.info("AutoShutdown: scheduled idle checker (threshold {}s)", IDLE_THRESHOLD_SECONDS);
	}

	private void checkIdle() {
		int online = proxy.getPlayerCount();
		long now = System.currentTimeMillis();

		if (online > 0) {
			lastActive = now;
			return;
		}

		long idleSec = (now - lastActive) / 1_000;
		if (idleSec >= IDLE_THRESHOLD_SECONDS) {
			logger.info("No players for {}s → shutting down.", idleSec);
			proxy.shutdown();
		}
	}
}
