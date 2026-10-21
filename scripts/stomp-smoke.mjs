// Smoke test do STOMP/WebSocket atrás do nginx. Sem dependências (usa o WebSocket global do Node 22+).
// Uso: BASE_URL=http://localhost ACCESS_TOKEN=... REFRESH_TOKEN=... ROOM_ID=... node scripts/stomp-smoke.mjs
const base = process.env.BASE_URL ?? 'http://localhost';
const accessToken = process.env.ACCESS_TOKEN;
const refreshToken = process.env.REFRESH_TOKEN;
const roomId = process.env.ROOM_ID;
if (!accessToken || !refreshToken || !roomId) {
  console.error('Defina ACCESS_TOKEN, REFRESH_TOKEN e ROOM_ID');
  process.exit(2);
}
const wsUrl = base.replace(/^http/, 'ws') + '/ws/websocket';
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

function frame(command, headers = {}, body = '') {
  const lines = Object.entries(headers).map(([k, v]) => `${k}:${v}`).join('\n');
  return `${command}\n${lines}${lines ? '\n' : ''}\n${body}\0`;
}

function parseFrames(data) {
  return data
    .split('\0')
    .map((raw) => raw.replace(/^\n+/, ''))
    .filter(Boolean)
    .map((raw) => {
      const [head, ...rest] = raw.split('\n\n');
      const [command, ...headerLines] = head.split('\n');
      const headers = Object.fromEntries(headerLines.map((l) => [l.slice(0, l.indexOf(':')), l.slice(l.indexOf(':') + 1)]));
      return { command, headers, body: rest.join('\n\n') };
    });
}

class Client {
  frames = [];
  closed = false;

  async open() {
    this.ws = new WebSocket(wsUrl, ['v12.stomp']);
    this.ws.addEventListener('message', (e) => this.frames.push(...parseFrames(String(e.data))));
    this.ws.addEventListener('close', () => { this.closed = true; });
    await new Promise((resolve, reject) => {
      this.ws.addEventListener('open', resolve);
      this.ws.addEventListener('error', () => reject(new Error('falha ao abrir o WebSocket')));
    });
    return this;
  }

  send(command, headers, body) {
    if (this.ws.readyState === WebSocket.OPEN) this.ws.send(frame(command, headers, body));
  }

  async waitFor(predicate, ms = 3000) {
    const deadline = Date.now() + ms;
    while (Date.now() < deadline) {
      const found = this.frames.find(predicate);
      if (found) return found;
      await sleep(50);
    }
    return null;
  }

  has(predicate) {
    return this.frames.some(predicate);
  }

  close() {
    this.ws.close();
  }
}

async function connect(token, command = 'CONNECT', extra = {}) {
  const client = await new Client().open();
  const headers = { 'accept-version': '1.2', host: 'whisp', ...extra };
  if (token) headers.Authorization = `Bearer ${token}`;
  client.send(command, headers);
  const reply = await client.waitFor((f) => f.command === 'CONNECTED' || f.command === 'ERROR');
  return { client, connected: reply?.command === 'CONNECTED', reply };
}

const results = [];
function check(name, ok) {
  results.push(ok);
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}`);
}

const topic = `/topic/chat/${roomId}`;
const isMessage = (text) => (f) => f.command === 'MESSAGE' && f.body.includes(text);
const isError = (f) => f.command === 'ERROR';
const stamp = Date.now();

// receptor legítimo, inscrito na sala
const listener = await connect(accessToken);
check('CONNECT com access token é aceito', listener.connected);
listener.client.send('SUBSCRIBE', { id: 'sub-0', destination: topic });
await sleep(300);

// o servidor negocia heartbeat quando o cliente pede (o ALB derruba conexões ociosas em 60 s)
const beating = await connect(accessToken, 'CONNECT', { 'heart-beat': '10000,10000' });
check('servidor negocia heartbeat no CONNECTED', beating.connected && /^[1-9]\d*,[1-9]\d*$/.test(beating.reply.headers['heart-beat'] ?? ''));
beating.client.close();

// 1. fluxo normal
const sender = await connect(accessToken);
sender.client.send('SEND', { destination: `/app/chat/${roomId}`, 'content-type': 'application/json' },
  JSON.stringify({ roomId, content: `legit-${stamp}` }));
const delivered = await listener.client.waitFor(isMessage(`legit-${stamp}`));
check('mensagem legítima chega ao inscrito com o remetente do token', !!delivered && delivered.body.includes('"senderUsername"'));
sender.client.close();

// 2. cliente autenticado não publica direto em /topic
const direct = await connect(accessToken);
direct.client.send('SEND', { destination: topic, 'content-type': 'application/json' },
  JSON.stringify({ senderUsername: 'admin', content: `forged-send-${stamp}` }));
check('SEND direto em /topic recebe ERROR', !!(await direct.client.waitFor(isError)));

// 3. comando MESSAGE vindo do cliente também é barrado
const asMessage = await connect(accessToken);
asMessage.client.send('MESSAGE', { destination: topic, 'message-id': 'x', subscription: 'sub-0' }, `forged-message-${stamp}`);
check('frame MESSAGE do cliente recebe ERROR', !!(await asMessage.client.waitFor(isError)));

// 4. inscrição com curinga
const wildcard = await connect(accessToken);
wildcard.client.send('SUBSCRIBE', { id: 'w', destination: '/topic/chat/**' });
check('SUBSCRIBE com curinga recebe ERROR', !!(await wildcard.client.waitFor(isError)));

// 5. sem CONNECT
const anonymous = await new Client().open();
anonymous.send('SUBSCRIBE', { id: 'a', destination: topic });
check('SUBSCRIBE sem CONNECT recebe ERROR', !!(await anonymous.waitFor(isError)));

// 6 a 8. credenciais inválidas no CONNECT
const noToken = await connect(null);
check('CONNECT sem token é recusado', !noToken.connected);
const stompNoToken = await connect(null, 'STOMP');
check('comando STOMP sem token é recusado', !stompNoToken.connected);
const refreshAsBearer = await connect(refreshToken);
check('refresh token no CONNECT é recusado', !refreshAsBearer.connected);

// 9. corpo apontando para outra sala que não a do destino
const mismatch = await connect(accessToken);
mismatch.client.send('SEND', { destination: `/app/chat/${crypto.randomUUID()}`, 'content-type': 'application/json' },
  JSON.stringify({ roomId, content: `mismatch-${stamp}` }));

await sleep(1500);
check('nenhuma mensagem forjada chegou ao inscrito',
  !listener.client.has(isMessage(`forged-send-${stamp}`))
  && !listener.client.has(isMessage(`forged-message-${stamp}`))
  && !listener.client.has(isMessage(`mismatch-${stamp}`)));
check('sessão sem CONNECT não recebe mensagens', !anonymous.has((f) => f.command === 'MESSAGE'));

for (const c of [listener.client, direct.client, asMessage.client, wildcard.client, anonymous, mismatch.client]) c.close();
const failed = results.filter((ok) => !ok).length;
console.log(`\n${results.length - failed}/${results.length} verificações passaram`);
process.exit(failed ? 1 : 0);
