package com.verticordia.autoshtdwn;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.ConnectionHandshakeEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerPing.Players;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;

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

	/** Map to track the last successful ping time for each server */
	private final Map<String, Long> lastSuccessfulPing = new ConcurrentHashMap<>();

	/** Maximum time in seconds a server can be unreachable before shutdown */
	private static final long SERVER_UNREACHABLE_THRESHOLD_SECONDS = 30;

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

		logger.info("AutoShutdown: threshold={}s, backend‐poll={}s", IDLE_THRESHOLD_SECONDS,
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
					String serverName = srv.getServerInfo().getName();
					logger.warn("Failed to ping backend {}: {}", serverName, ex.getMessage());

					long lastPingTime = lastSuccessfulPing.getOrDefault(serverName, 0L);
					long now = System.currentTimeMillis();
					long unreachableTimeSec = (now - lastPingTime) / 1_000;

					if (unreachableTimeSec >= SERVER_UNREACHABLE_THRESHOLD_SECONDS) {
						logger.info("Server {} unreachable for {}s → shutting down proxy",
								serverName, unreachableTimeSec);
						kickAllPlayers(serverName);
						proxy.shutdown();
						return;
					}
				}

				Optional<Players> players = ping.getPlayers();
				if (players.isEmpty())
					return;

				int count = players.get().getOnline();
				if (count > 0) {
					String serverName = srv.getServerInfo().getName();
					lastActive = System.currentTimeMillis();
					lastSuccessfulPing.put(serverName, lastActive);
					logger.debug(
							"Backend '{}' has {} players → resetting idle timer", serverName, count);
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

	private void kickAllPlayers(String serverName) {
		TextComponent msg = Component.text("Um dos nossos servidores")
				.color(NamedTextColor.RED)
				.append(Component.text(" (o servidor “", NamedTextColor.DARK_RED))
				.append(Component.text(serverName, NamedTextColor.GOLD))
				.append(Component.text("”) ", NamedTextColor.DARK_RED))
				.append(Component.text(
						"não pôde ser alcançado. Provavelmente crashou!\n", NamedTextColor.RED))
				.append(Component.text(
						"A rede inteira será reiniciada, automaticamente. Para isso, temos que "
								+ "temporariamente desligar todos os servidores.\n",
						NamedTextColor.AQUA))
				.append(Component.text(
						"Tente entrar novamente em dois minutos. Sinto muito pela inconveniência, "
								+ "mas às vezes meu PC simplesmente não tanka... ",
						NamedTextColor.AQUA))
				.append(Component.text(" ☹\n", NamedTextColor.WHITE))
				.append(Component.text(
						"(Se o problema persistir, procure o operador.)", NamedTextColor.DARK_AQUA));
		proxy.getAllPlayers().forEach(player -> {
			player.disconnect(msg);
		});
	}
}
