package com.verticordia.autoshtdwn;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.ConnectionHandshakeEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerPing.Players;

import org.slf4j.Logger;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Plugin(id = "autoshtdwn", name = "AutoShutdown", version = "0.0.0-SNAPSHOT", description = "Shuts down if NOTHING (no client pings, no logins, NO backend activity) for 90s.", authors = {
		"Lucca Pellegrini" })
public class AutoShutdown {
	private final ProxyServer proxy;
	private final Logger logger;

	/**
	 * When we last saw any activity (handshake, ping, login, or backend players)
	 */
	private volatile long lastActive = System.currentTimeMillis();

	/** Seconds of silence before we exit */
	private static final long IDLE_THRESHOLD_SECONDS = 90;

	/** How often to poll backends for their own player‐counts */
	private static final long BACKEND_POLL_INTERVAL_SECONDS = 10;

	@Inject
	public AutoShutdown(ProxyServer proxy, Logger logger) {
		this.proxy = proxy;
		this.logger = logger;
	}

	@Subscribe
	public void onProxyInit(ProxyInitializeEvent ev) {
		// 1) Every second, check if we've exceeded IDLE_THRESHOLD
		proxy.getScheduler()
				.buildTask(this, this::checkIdle)
				.repeat(1, TimeUnit.SECONDS)
				.schedule();

		// 2) Every BACKEND_POLL_INTERVAL_SECONDS, ping all registered servers
		proxy.getScheduler()
				.buildTask(this, this::pollBackends)
				.repeat(BACKEND_POLL_INTERVAL_SECONDS, TimeUnit.SECONDS)
				.schedule();

		logger.info("AutoShutdown: threshold={}s, backend‐poll={}s",
				IDLE_THRESHOLD_SECONDS,
				BACKEND_POLL_INTERVAL_SECONDS);
	}

	@Subscribe
	public void onHandshake(ConnectionHandshakeEvent ev) {
		bump("handshake");
	}

	@Subscribe
	public void onPing(ProxyPingEvent ev) {
		bump("MOTD ping");
	}

	@Subscribe
	public void onLogin(LoginEvent ev) {
		bump("login");
	}

	private void bump(String why) {
		lastActive = System.currentTimeMillis();
		logger.debug("Activity [{}], resetting idle timer", why);
	}

	private void pollBackends() {
		Collection<RegisteredServer> servers = proxy.getAllServers();
		for (RegisteredServer srv : servers) {
			srv.ping().whenComplete((ping, ex) -> {
				if (ex != null) {
					logger.warn("Failed to ping backend {}", srv.getServerInfo().getName(), ex);
					return;
				}

				Optional<Players> players = ping.getPlayers();
				if (players.isEmpty())
					return;

				int count = players.get().getOnline();
				if (count > 0) {
					lastActive = System.currentTimeMillis();
					logger.debug("Backend '{}' has {} players → resetting idle timer",
							srv.getServerInfo().getName(), count);
				}
			});
		}
	}

	private void checkIdle() {
		long now = System.currentTimeMillis();
		int online = proxy.getPlayerCount();
		if (online > 0) {
			// physical players on proxy → reset immediately
			lastActive = now;
			return;
		}

		long idleSec = (now - lastActive) / 1_000;
		if (idleSec >= IDLE_THRESHOLD_SECONDS) {
			logger.info("No activity for {}s → shutting down proxy", idleSec);
			proxy.shutdown();
		}
	}
}
