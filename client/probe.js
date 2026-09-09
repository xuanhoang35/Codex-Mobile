/**
 * Kiem tra ket noi WebSocket toi Codex Mobile App Server
 * Cach su dung: node probe.js <SERVER_URL> <TOKEN>
 */
const WebSocket = require('ws');

const url = process.argv[2] || process.env.CODEX_URL || 'wss://intsoft-codebank-vn.tailb240ce.ts.net:8443/';
const token = process.argv[3] || process.env.CODEX_TOKEN;

if (!token) {
  console.error("Cach dung: node probe.js <SERVER_URL> <BEARER_TOKEN>");
  process.exit(1);
}

console.log(`Dang ket noi toi: ${url}`);

const ws = new WebSocket(url, {
  headers: {
    Authorization: `Bearer ${token}`
  }
});

ws.on('open', () => {
  console.log('Ket noi thanh cong toi Codex App Server!');
  const initMsg = {
    method: 'initialize',
    id: 1,
    params: {
      clientInfo: {
        name: 'codex_mobile_probe',
        title: 'Codex Mobile Probe',
        version: '1.0.0'
      }
    }
  };
  ws.send(JSON.stringify(initMsg));
});

ws.on('message', (data) => {
  console.log('Phan hoi tu Codex Server:');
  console.log(data.toString());
  ws.close();
});

ws.on('error', (err) => {
  console.error('Loi ket noi:', err.message);
});

ws.on('close', (code, reason) => {
  console.log(`Dong ket noi (Code: ${code})`);
});
