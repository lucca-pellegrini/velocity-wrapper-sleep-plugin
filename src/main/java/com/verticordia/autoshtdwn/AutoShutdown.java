package com.verticordia.autoshtdwn;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.ConnectionHandshakeEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.util.concurrent.TimeUnit;

@Plugin(id = "autoshtdwn", name = "AutoShutdown", version = "0.0.0-SNAPSHOT", description = "Shuts down the proxy if NOTHING (no pings, no handshakes, no logins) happens for 90 seconds.", authors = {
		"Lucca Pellegrini" })
public class AutoShutdown {
	private final ProxyServer proxy;
	private final Logger logger;

	/** last time we saw *any* connection activity (handshake, ping, login, etc.) */
	private volatile long lastActive = System.currentTimeMillis();

	/** seconds of total silence before we exit */
	private static final long IDLE_THRESHOLD_SECONDS = 90;

	@Inject
	public AutoShutdown(ProxyServer proxy, Logger logger) {
		this.proxy = proxy;
		this.logger = logger;
	}

	@Subscribe
	public void onProxyInit(ProxyInitializeEvent ev) {
		// every second check how long we've been silent
		proxy.getScheduler()
				.buildTask(this, this::checkIdle)
				.repeat(1, TimeUnit.SECONDS)
				.schedule();

		logger.info("AutoShutdown scheduled (threshold {}s of zero activity)", IDLE_THRESHOLD_SECONDS);
	}

	@Subscribe
	public void onHandshake(ConnectionHandshakeEvent ev) {
		// catches both real login attempts and status handshakes
		resetIdleTimer("handshake");
	}

	@Subscribe
	public void onPing(ProxyPingEvent ev) {
		// status / MOTD ping
		resetIdleTimer("ping");
	}

	@Subscribe
	public void onLogin(LoginEvent ev) {
		resetIdleTimer("login");
	}

	private void resetIdleTimer(String what) {
		lastActive = System.currentTimeMillis();
		logger.debug("Activity detected ({}), resetting idle timer", what);
	}

	private void checkIdle() {
		long now = System.currentTimeMillis();
		long idleSec = (now - lastActive) / 1_000;

		if (idleSec >= IDLE_THRESHOLD_SECONDS) {
			logger.info("No activity for {}s → shutting down proxy", idleSec);
			proxy.shutdown();
		}
	}
}
