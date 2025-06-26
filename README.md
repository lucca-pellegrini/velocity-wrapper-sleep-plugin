# AutoShutdown

A Velocity proxy plugin that automatically shuts down the proxy after a period
of inactivity (no client pings, no logins, and no players on any backend).
Ideal for use with a systemd-socket–activated server wrapper such as
[my wonderful hack here](https://github.com/lucca-pellegrini/minecraft-servers-tmux-wrapper),
to spin up your Minecraft servers on demand and clean them up when everyone’s
gone.

---

## Features

- Tracks proxy‐side activity:
  - Connection handshakes
  - MOTD pings
  - Login events
- Polls each registered backend every 10 s for its player count
- Idle threshold of 90 s (no proxy players, no backend players)
- Calls `proxy.shutdown()` to trigger your systemd wrapper’s cleanup
- Zero configuration: sensible defaults, fork to adjust timing

---

## Requirements

- Velocity proxy (tested on Velocity 3.4.0)
- Java 17+ (build and runtime)
- (Optional) A socket-activated systemd-unit wrapper to start the server again
(e.g. [mine :)](https://github.com/lucca-pellegrini/minecraft-servers-tmux-wrapper)) to manage server processes

---

## Installation

1. Clone & build
   ```bash
   git clone https://github.com/lucca-pellegrini/velocity-wrapper-sleep-plugin.git
   cd autoshtdwn
   mvn clean package
   ```
2. Copy the resulting JAR into your Velocity `plugins/` directory:
   ```
   cp target/autoshtdwn-<version>.jar /path/to/velocity/plugins/
   ```
3. Restart or reload your Velocity proxy.
   The plugin will log its idle threshold and backend-poll interval on startup.

---

## How It Works

1. **Event Listeners**
   - `ConnectionHandshakeEvent`
   - `ProxyPingEvent`
   - `LoginEvent`
   Each of these “bumps” the internal `lastActive` timestamp.

2. **Backend Polling**
   Every 10 s, the plugin pings all registered servers:
   - If any server reports >0 players, `lastActive` is bumped.

3. **Idle Check**
   Every 1 s, compares `now − lastActive` to the 90 s threshold:
   - If the proxy has 0 players and idle ≥ 90 s, logs and calls
     `proxy.shutdown()`.
   - That shutdown triggers your systemd wrapper’s cleanup logic (assuming you
     have [it](https://github.com/lucca-pellegrini/minecraft-servers-tmux-wrapper)).

---

## Configuration

All timing and behavior constants are hardcoded:

- `IDLE_THRESHOLD_SECONDS = 90`
- `BACKEND_POLL_INTERVAL_SECONDS = 10`

To adjust, fork or patch the source and recompile.

---

## Integration

Use alongside [my systemd socket–activated tmux
wrapper](https://github.com/lucca-pellegrini/minecraft-servers-tmux-wrapper).

That wrapper listens on your Minecraft port via systemd, launches one tmux
window per server, proxies traffic into Velocity, and cleans up servers when
Velocity exits.

---

## Copyright

This project is licensed under the Apache License, Version 2.0. See the
[LICENSE](LICENSE) file for more details.

---

## See also

- [minecraft-servers-tmux-wrapper](https://github.com/lucca-pellegrini/minecraft-servers-tmux-wrapper)
- [Velocity Plugin API](https://jd.papermc.io/velocity/3.4.0/index.html)
- [systemd.unit(5)](https://man.archlinux.org/man/systemd.unit.5)
- [systemd.service(5)](https://man.archlinux.org/man/systemd.service.5)
- [systemd.socket(5)](https://man.archlinux.org/man/systemd.socket.5)
- [lazymc](https://github.com/timvisee/lazymc)
- [Velocity proxy](https://papermc.io/software/velocity/)
