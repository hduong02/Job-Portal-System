// SSH forwarding through Node's sockets and the existing Windows SSH agent.
// Pin the server keys already trusted by the user's OpenSSH known_hosts file.
const fs = require('node:fs');
const path = require('node:path');
const net = require('node:net');
const { execFileSync } = require('node:child_process');
const { monitorEventLoopDelay } = require('node:perf_hooks');
const { Client } = require(path.resolve('.loadtest/node_modules/ssh2'));
const config = JSON.parse(fs.readFileSync('.loadtest/ramping-vus-clean-mailhog-20261009/transport-config.json', 'utf8'));
const knownHosts = path.join(process.env.USERPROFILE, '.ssh', 'known_hosts');
const matched = execFileSync('ssh-keygen', ['-F', '201.18.216.184', '-f', knownHosts], { encoding: 'utf8', windowsHide: true });
const trusted = matched.split(/\r?\n/).filter(line => line && !line.startsWith('#'))
  .map(line => Buffer.from(line.trim().split(/\s+/)[2], 'base64'));
if (!trusted.length) throw new Error('No previously trusted VPS host keys');
const clients = [];
const servers = [];
let active = 0;
let peak = 0;
let errors = 0;
const delay = monitorEventLoopDelay({ resolution: 20 });
delay.enable();

async function connect() {
  const client = new Client();
  clients.push(client);
  await new Promise((resolve, reject) => {
    client.once('ready', resolve).once('error', reject);
    client.connect({ host: '201.18.216.184', port: 22, username: 'root',
      agent: '\\\\.\\pipe\\openssh-ssh-agent', readyTimeout: 15000, keepaliveInterval: 15000,
      hostVerifier: key => trusted.some(expected => expected.equals(key)) });
  });
  client.on('error', error => { errors++; console.error(error.message); });
  return client;
}

async function main() {
  if (process.argv.includes('--probe')) {
    const client = await connect();
    console.log('SSH agent authentication and pinned host-key verification passed');
    client.end();
    return;
  }
  for (let i = 0; i < config.tunnelCount; i++) {
    const client = await connect();
    const server = net.createServer(socket => {
      socket.pause();
      socket.on('error', () => {});
      const opened = (error, channel) => {
        if (error) { errors++; console.error(error.message); socket.destroy(); return; }
        if (socket.destroyed) { channel.close(); return; }
        active++;
        peak = Math.max(peak, active);
        channel.on('error', () => { errors++; socket.destroy(); });
        channel.on('close', () => { active--; socket.destroy(); });
        socket.once('close', () => channel.close());
        socket.once('end', () => channel.close());
        channel.once('end', () => socket.end());
        socket.pipe(channel).pipe(socket);
        socket.resume();
      };
      try {
        client.forwardOut('127.0.0.1', socket.remotePort, '127.0.0.1', 5000, opened);
      } catch (error) {
        errors++;
        console.error(error.message);
        socket.destroy();
      }
    });
    servers.push(server);
    await new Promise((resolve, reject) => {
      server.once('error', reject).listen(config.basePort + i, '127.0.0.1', resolve);
    });
  }
  console.log(`READY ${servers.length} SSH forwarding connections`);
  let previousCpu = process.cpuUsage();
  let previousTime = Date.now();
  setInterval(() => {
    const now = Date.now();
    const cpu = process.cpuUsage();
    const cpuPercent = ((cpu.user-previousCpu.user)+(cpu.system-previousCpu.system))/(now-previousTime)/10;
    const snapshot = { at: new Date().toISOString(), active, peak, errors,
      rss: process.memoryUsage().rss, cpuPercent, eventLoopP95Ms: delay.percentile(95)/1e6 };
    fs.writeFileSync('.loadtest/ramping-vus-clean-mailhog-20261009/transport.json', JSON.stringify(snapshot));
    fs.appendFileSync('.loadtest/ramping-vus-clean-mailhog-20261009/transport.jsonl', JSON.stringify(snapshot)+'\n');
    previousCpu = cpu;
    previousTime = now;
    delay.reset();
  }, 2000);
}

function shutdown() {
  for (const server of servers) server.close();
  for (const client of clients) client.end();
  setTimeout(() => process.exit(0), 1000).unref();
}
process.on('SIGTERM', shutdown);
process.on('SIGINT', shutdown);
main().catch(error => { console.error(error.message); shutdown(); process.exitCode = 1; });
