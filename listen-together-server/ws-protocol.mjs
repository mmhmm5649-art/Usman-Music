import crypto from 'node:crypto';
import { EventEmitter } from 'node:events';

const WS_GUID = '258EAFA5-E914-47DA-95CA-C5AB0DC85B11';
const MAX_FRAME_PAYLOAD_BYTES = 64 * 1024;
const MAX_BACKPRESSURE_BYTES = 512 * 1024;

export const WsCloseCode = {
  NORMAL: 1000,
  GOING_AWAY: 1001,
  PROTOCOL_ERROR: 1002,
  UNSUPPORTED_DATA: 1003,
  POLICY_VIOLATION: 1008,
  MESSAGE_TOO_BIG: 1009,
  INTERNAL_ERROR: 1011,
};

/**
 * RFC 6455 compliant WebSocket connection built purely on Node.js built-ins.
 */
export class WsConnection extends EventEmitter {
  constructor(socket, request, { maxPayloadBytes = MAX_FRAME_PAYLOAD_BYTES } = {}) {
    super();
    this.socket = socket;
    this.request = request;
    this.maxPayloadBytes = maxPayloadBytes;
    this.readyState = 'OPEN';
    this.isAlive = true;
    this.buffer = Buffer.alloc(0);
    this.fragmentOpcode = null;
    this.fragmentChunks = [];
    this.fragmentTotalBytes = 0;

    socket.setNoDelay(true);
    socket.on('data', (chunk) => this._onData(chunk));
    socket.on('close', () => this._handleClose(WsCloseCode.GOING_AWAY, 'Socket closed'));
    socket.on('error', (err) => {
      this.emit('error', err);
      this._handleClose(WsCloseCode.GOING_AWAY, 'Socket error');
    });
  }

  sendText(text) {
    if (this.readyState !== 'OPEN' || this.socket.destroyed) return false;
    if (this.socket.writableLength > MAX_BACKPRESSURE_BYTES) {
      this.close(WsCloseCode.POLICY_VIOLATION, 'Slow consumer');
      return false;
    }
    const payload = Buffer.from(String(text), 'utf8');
    return this._writeFrame(0x1, payload);
  }

  sendJson(obj) {
    return this.sendText(JSON.stringify(obj));
  }

  ping(payload = Buffer.alloc(0)) {
    if (this.readyState !== 'OPEN' || this.socket.destroyed) return false;
    return this._writeFrame(0x9, payload);
  }

  pong(payload = Buffer.alloc(0)) {
    if (this.readyState !== 'OPEN' || this.socket.destroyed) return false;
    return this._writeFrame(0xA, payload);
  }

  close(code = WsCloseCode.NORMAL, reason = '') {
    if (this.readyState === 'CLOSED') return;
    const wasOpen = this.readyState === 'OPEN';
    this.readyState = 'CLOSING';
    if (wasOpen && !this.socket.destroyed) {
      const reasonBuf = Buffer.from(String(reason).slice(0, 120), 'utf8');
      const payload = Buffer.allocUnsafe(2 + reasonBuf.length);
      payload.writeUInt16BE(code, 0);
      reasonBuf.copy(payload, 2);
      try {
        this._writeFrame(0x8, payload);
      } catch {
        // Ignore write error while closing
      }
      this.socket.end();
    }
    this._handleClose(code, reason);
  }

  terminate() {
    this.readyState = 'CLOSED';
    if (!this.socket.destroyed) {
      this.socket.destroy();
    }
    this.emit('close', WsCloseCode.GOING_AWAY, 'Terminated');
  }

  _writeFrame(opcode, payload) {
    const length = payload.length;
    let header;
    if (length < 126) {
      header = Buffer.allocUnsafe(2);
      header[0] = 0x80 | opcode;
      header[1] = length;
    } else if (length < 65536) {
      header = Buffer.allocUnsafe(4);
      header[0] = 0x80 | opcode;
      header[1] = 126;
      header.writeUInt16BE(length, 2);
    } else {
      header = Buffer.allocUnsafe(10);
      header[0] = 0x80 | opcode;
      header[1] = 127;
      header.writeBigUInt64BE(BigInt(length), 2);
    }
    try {
      this.socket.write(Buffer.concat([header, payload]));
      return true;
    } catch {
      return false;
    }
  }

  _onData(chunk) {
    if (this.readyState !== 'OPEN') return;
    this.buffer = this.buffer.length === 0 ? chunk : Buffer.concat([this.buffer, chunk]);

    while (this.buffer.length >= 2 && this.readyState === 'OPEN') {
      const firstByte = this.buffer[0];
      const secondByte = this.buffer[1];

      const fin = (firstByte & 0x80) !== 0;
      const rsv = firstByte & 0x70;
      const opcode = firstByte & 0x0f;
      const masked = (secondByte & 0x80) !== 0;
      let payloadLength = secondByte & 0x7f;

      if (rsv !== 0) {
        this.close(WsCloseCode.PROTOCOL_ERROR, 'Unsupported RSV bits');
        return;
      }

      // RFC 6455 §5.1: Client-to-server frames MUST be masked
      if (!masked) {
        this.close(WsCloseCode.PROTOCOL_ERROR, 'Client frame must be masked');
        return;
      }

      let offset = 2;
      if (payloadLength === 126) {
        if (this.buffer.length < 4) return;
        payloadLength = this.buffer.readUInt16BE(2);
        offset = 4;
      } else if (payloadLength === 127) {
        if (this.buffer.length < 10) return;
        const bigLen = this.buffer.readBigUInt64BE(2);
        if (bigLen > BigInt(this.maxPayloadBytes)) {
          this.close(WsCloseCode.MESSAGE_TOO_BIG, 'Payload too large');
          return;
        }
        payloadLength = Number(bigLen);
        offset = 10;
      }

      if (payloadLength > this.maxPayloadBytes) {
        this.close(WsCloseCode.MESSAGE_TOO_BIG, 'Payload exceeds limit');
        return;
      }

      const frameSize = offset + 4 + payloadLength;
      if (this.buffer.length < frameSize) return;

      const maskKey = this.buffer.subarray(offset, offset + 4);
      const maskedPayload = this.buffer.subarray(offset + 4, frameSize);
      const payload = Buffer.allocUnsafe(payloadLength);
      for (let i = 0; i < payloadLength; i++) {
        payload[i] = maskedPayload[i] ^ maskKey[i & 3];
      }

      this.buffer = this.buffer.subarray(frameSize);
      this._handleFrame(fin, opcode, payload);
    }
  }

  _handleFrame(fin, opcode, payload) {
    // Control frames (0x8..0xA)
    if (opcode >= 0x8) {
      if (!fin || payload.length > 125) {
        this.close(WsCloseCode.PROTOCOL_ERROR, 'Invalid control frame');
        return;
      }
      if (opcode === 0x8) {
        const code = payload.length >= 2 ? payload.readUInt16BE(0) : WsCloseCode.NORMAL;
        const reason = payload.length > 2 ? payload.subarray(2).toString('utf8') : '';
        this.close(code, reason);
        return;
      }
      if (opcode === 0x9) {
        this.isAlive = true;
        this.pong(payload);
        return;
      }
      if (opcode === 0xA) {
        this.isAlive = true;
        this.emit('pong', payload);
        return;
      }
      this.close(WsCloseCode.PROTOCOL_ERROR, 'Unknown control opcode');
      return;
    }

    // Data frames
    if (opcode === 0x0) {
      if (this.fragmentOpcode === null) {
        this.close(WsCloseCode.PROTOCOL_ERROR, 'Unexpected continuation frame');
        return;
      }
      this.fragmentTotalBytes += payload.length;
      if (this.fragmentTotalBytes > this.maxPayloadBytes) {
        this.close(WsCloseCode.MESSAGE_TOO_BIG, 'Fragmented message too large');
        return;
      }
      this.fragmentChunks.push(payload);
      if (fin) {
        const fullPayload = Buffer.concat(this.fragmentChunks);
        const fullOpcode = this.fragmentOpcode;
        this.fragmentOpcode = null;
        this.fragmentChunks = [];
        this.fragmentTotalBytes = 0;
        this._dispatchData(fullOpcode, fullPayload);
      }
      return;
    }

    if (opcode === 0x1 || opcode === 0x2) {
      if (this.fragmentOpcode !== null) {
        this.close(WsCloseCode.PROTOCOL_ERROR, 'Previous fragmented message incomplete');
        return;
      }
      if (!fin) {
        this.fragmentOpcode = opcode;
        this.fragmentChunks = [payload];
        this.fragmentTotalBytes = payload.length;
      } else {
        this._dispatchData(opcode, payload);
      }
      return;
    }

    this.close(WsCloseCode.PROTOCOL_ERROR, 'Unsupported opcode');
  }

  _dispatchData(opcode, payload) {
    this.isAlive = true;
    if (opcode === 0x1) {
      this.emit('message', payload.toString('utf8'));
    } else {
      this.close(WsCloseCode.UNSUPPORTED_DATA, 'Only UTF-8 JSON text frames are accepted');
    }
  }

  _handleClose(code, reason) {
    if (this.readyState === 'CLOSED') return;
    this.readyState = 'CLOSED';
    this.emit('close', code, reason);
  }
}

/**
 * Performs an RFC 6455 HTTP -> WebSocket upgrade handshake.
 */
export function upgradeWebSocket(req, socket, head, options = {}) {
  const upgradeHeader = (req.headers.upgrade || '').toLowerCase();
  const wsKey = req.headers['sec-websocket-key'];
  const wsVersion = req.headers['sec-websocket-version'];

  if (upgradeHeader !== 'websocket' || !wsKey || wsVersion !== '13') {
    socket.write('HTTP/1.1 400 Bad Request\r\nConnection: close\r\n\r\n');
    socket.destroy();
    return null;
  }

  const acceptKey = crypto
    .createHash('sha1')
    .update(wsKey + WS_GUID, 'binary')
    .digest('base64');

  const responseHeaders = [
    'HTTP/1.1 101 Switching Protocols',
    'Upgrade: websocket',
    'Connection: Upgrade',
    `Sec-WebSocket-Accept: ${acceptKey}`,
    '\r\n',
  ].join('\r\n');

  socket.write(responseHeaders);
  const ws = new WsConnection(socket, req, options);
  if (head && head.length > 0) {
    ws._onData(head);
  }
  return ws;
}
