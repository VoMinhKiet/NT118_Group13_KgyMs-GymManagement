/*
 * KgyMs – bộ mã hoá QR + HMAC-SHA256 tối giản, không phụ thuộc thư viện ngoài, chạy offline.
 * - QR: chế độ byte, mức sửa lỗi M, phiên bản 1–6 (đủ cho chuỗi điểm danh ~31 ký tự).
 * - HMAC-SHA256 viết tay để trang chạy được cả khi mở bằng file:// (không cần WebCrypto).
 * Phải khớp với app: data/qr/CheckInCode.kt.
 */
(function (root) {
  "use strict";

  /* ============================ SHA-256 / HMAC ============================ */

  var K = [
    0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
    0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
    0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
    0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
    0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
    0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
    0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
  ];

  function utf8(text) {
    return Array.prototype.slice.call(new TextEncoder().encode(text));
  }

  function sha256(bytes) {
    var h = [0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19];
    var msg = bytes.slice();
    var bitLen = bytes.length * 8;
    msg.push(0x80);
    while (msg.length % 64 !== 56) msg.push(0);
    var hi = Math.floor(bitLen / 0x100000000), lo = bitLen >>> 0;
    msg.push((hi >>> 24) & 255, (hi >>> 16) & 255, (hi >>> 8) & 255, hi & 255,
             (lo >>> 24) & 255, (lo >>> 16) & 255, (lo >>> 8) & 255, lo & 255);
    var w = new Array(64);
    for (var off = 0; off < msg.length; off += 64) {
      for (var i = 0; i < 16; i++) {
        w[i] = (msg[off + 4 * i] << 24) | (msg[off + 4 * i + 1] << 16) | (msg[off + 4 * i + 2] << 8) | msg[off + 4 * i + 3];
      }
      for (i = 16; i < 64; i++) {
        var s0 = rotr(w[i - 15], 7) ^ rotr(w[i - 15], 18) ^ (w[i - 15] >>> 3);
        var s1 = rotr(w[i - 2], 17) ^ rotr(w[i - 2], 19) ^ (w[i - 2] >>> 10);
        w[i] = (w[i - 16] + s0 + w[i - 7] + s1) | 0;
      }
      var a = h[0], b = h[1], c = h[2], d = h[3], e = h[4], f = h[5], g = h[6], hh = h[7];
      for (i = 0; i < 64; i++) {
        var S1 = rotr(e, 6) ^ rotr(e, 11) ^ rotr(e, 25);
        var ch = (e & f) ^ (~e & g);
        var t1 = (hh + S1 + ch + K[i] + w[i]) | 0;
        var S0 = rotr(a, 2) ^ rotr(a, 13) ^ rotr(a, 22);
        var maj = (a & b) ^ (a & c) ^ (b & c);
        var t2 = (S0 + maj) | 0;
        hh = g; g = f; f = e; e = (d + t1) | 0; d = c; c = b; b = a; a = (t1 + t2) | 0;
      }
      h[0] = (h[0] + a) | 0; h[1] = (h[1] + b) | 0; h[2] = (h[2] + c) | 0; h[3] = (h[3] + d) | 0;
      h[4] = (h[4] + e) | 0; h[5] = (h[5] + f) | 0; h[6] = (h[6] + g) | 0; h[7] = (h[7] + hh) | 0;
    }
    var out = [];
    for (i = 0; i < 8; i++) out.push((h[i] >>> 24) & 255, (h[i] >>> 16) & 255, (h[i] >>> 8) & 255, h[i] & 255);
    return out;
  }

  function rotr(x, n) { return (x >>> n) | (x << (32 - n)); }

  function hmacSha256Hex(keyText, messageText) {
    var key = utf8(keyText);
    if (key.length > 64) key = sha256(key);
    while (key.length < 64) key.push(0);
    var inner = key.map(function (k) { return k ^ 0x36; }).concat(utf8(messageText));
    var outer = key.map(function (k) { return k ^ 0x5c; }).concat(sha256(inner));
    return sha256(outer).map(function (b) { return (b < 16 ? "0" : "") + b.toString(16); }).join("");
  }

  /* ============================ QR (byte, mức M) ============================ */

  // version: [số codeword sửa lỗi mỗi khối, số khối, tổng codeword]
  var EC_M = { 1: [10, 1, 26], 2: [16, 1, 44], 3: [26, 1, 70], 4: [18, 2, 100], 5: [24, 2, 134], 6: [16, 4, 172] };
  var ALIGN = { 1: [], 2: [6, 18], 3: [6, 22], 4: [6, 26], 5: [6, 30], 6: [6, 34] };

  function gfMul(x, y) {
    var z = 0;
    for (var i = 7; i >= 0; i--) {
      z = (z << 1) ^ ((z >>> 7) * 0x11d);
      z ^= ((y >>> i) & 1) * x;
    }
    return z & 255;
  }

  function rsDivisor(degree) {
    var result = [];
    for (var i = 0; i < degree - 1; i++) result.push(0);
    result.push(1);
    var root = 1;
    for (i = 0; i < degree; i++) {
      for (var j = 0; j < result.length; j++) {
        result[j] = gfMul(result[j], root);
        if (j + 1 < result.length) result[j] ^= result[j + 1];
      }
      root = gfMul(root, 0x02);
    }
    return result;
  }

  function rsRemainder(data, divisor) {
    var result = divisor.map(function () { return 0; });
    data.forEach(function (b) {
      var factor = b ^ result.shift();
      result.push(0);
      divisor.forEach(function (coef, i) { result[i] ^= gfMul(coef, factor); });
    });
    return result;
  }

  function buildCodewords(bytes, version) {
    var spec = EC_M[version], ecLen = spec[0], blocks = spec[1], total = spec[2];
    var dataLen = total - ecLen * blocks;
    var bits = [];
    function put(value, len) { for (var i = len - 1; i >= 0; i--) bits.push((value >>> i) & 1); }
    put(0x4, 4);
    put(bytes.length, 8);
    bytes.forEach(function (b) { put(b, 8); });
    var capacity = dataLen * 8;
    put(0, Math.min(4, capacity - bits.length));
    while (bits.length % 8 !== 0) bits.push(0);
    var data = [];
    for (var i = 0; i < bits.length; i += 8) {
      var v = 0;
      for (var j = 0; j < 8; j++) v = (v << 1) | bits[i + j];
      data.push(v);
    }
    for (var pad = 0xec; data.length < dataLen; pad ^= 0xec ^ 0x11) data.push(pad);

    var blockLen = dataLen / blocks, divisor = rsDivisor(ecLen), dataBlocks = [], ecBlocks = [];
    for (i = 0; i < blocks; i++) {
      var block = data.slice(i * blockLen, (i + 1) * blockLen);
      dataBlocks.push(block);
      ecBlocks.push(rsRemainder(block, divisor));
    }
    var out = [];
    for (i = 0; i < blockLen; i++) dataBlocks.forEach(function (b) { out.push(b[i]); });
    for (i = 0; i < ecLen; i++) ecBlocks.forEach(function (b) { out.push(b[i]); });
    return out;
  }

  function maskBit(mask, x, y) {
    switch (mask) {
      case 0: return (x + y) % 2 === 0;
      case 1: return y % 2 === 0;
      case 2: return x % 3 === 0;
      case 3: return (x + y) % 3 === 0;
      case 4: return (Math.floor(x / 3) + Math.floor(y / 2)) % 2 === 0;
      case 5: return (x * y) % 2 + (x * y) % 3 === 0;
      case 6: return ((x * y) % 2 + (x * y) % 3) % 2 === 0;
      default: return ((x + y) % 2 + (x * y) % 3) % 2 === 0;
    }
  }

  function makeMatrix(version, codewords, mask) {
    var size = version * 4 + 17, m = [], fn = [], dx, dy, x;
    for (var y = 0; y < size; y++) {
      m.push([]); fn.push([]);
      for (var x = 0; x < size; x++) { m[y].push(false); fn[y].push(false); }
    }
    function set(x, y, dark) { m[y][x] = dark; fn[y][x] = true; }

    for (var i = 0; i < size; i++) { set(6, i, i % 2 === 0); set(i, 6, i % 2 === 0); }
    [[3, 3], [size - 4, 3], [3, size - 4]].forEach(function (c) {
      for (var dy = -4; dy <= 4; dy++) for (var dx = -4; dx <= 4; dx++) {
        var xx = c[0] + dx, yy = c[1] + dy, dist = Math.max(Math.abs(dx), Math.abs(dy));
        if (xx >= 0 && xx < size && yy >= 0 && yy < size) set(xx, yy, dist !== 2 && dist !== 4);
      }
    });
    var pos = ALIGN[version], n = pos.length;
    for (i = 0; i < n; i++) for (var j = 0; j < n; j++) {
      if ((i === 0 && j === 0) || (i === 0 && j === n - 1) || (i === n - 1 && j === 0)) continue;
      for (dy = -2; dy <= 2; dy++) for (dx = -2; dx <= 2; dx++) {
        set(pos[i] + dx, pos[j] + dy, Math.max(Math.abs(dx), Math.abs(dy)) !== 1);
      }
    }

    // Định dạng: mức M = 00, BCH(15,5) rồi XOR 0x5412.
    var data = (0 << 3) | mask, rem = data;
    for (i = 0; i < 10; i++) rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
    var bits = ((data << 10) | rem) ^ 0x5412;
    function bit(k) { return ((bits >>> k) & 1) !== 0; }
    for (i = 0; i <= 5; i++) set(8, i, bit(i));
    set(8, 7, bit(6)); set(8, 8, bit(7)); set(7, 8, bit(8));
    for (i = 9; i < 15; i++) set(14 - i, 8, bit(i));
    for (i = 0; i < 8; i++) set(size - 1 - i, 8, bit(i));
    for (i = 8; i < 15; i++) set(8, size - 15 + i, bit(i));
    set(8, size - 8, true);

    var k = 0;
    for (var right = size - 1; right >= 1; right -= 2) {
      if (right === 6) right = 5;
      for (var vert = 0; vert < size; vert++) {
        for (j = 0; j < 2; j++) {
          x = right - j;
          var upward = ((right + 1) & 2) === 0;
          y = upward ? size - 1 - vert : vert;
          if (!fn[y][x] && k < codewords.length * 8) {
            m[y][x] = ((codewords[k >>> 3] >>> (7 - (k & 7))) & 1) !== 0;
            k++;
          }
        }
      }
    }
    for (y = 0; y < size; y++) for (x = 0; x < size; x++) {
      if (!fn[y][x] && maskBit(mask, x, y)) m[y][x] = !m[y][x];
    }
    return m;
  }

  function penalty(m) {
    var size = m.length, score = 0, dark = 0, x, y;
    function line(get) {
      var s = 0;
      for (var a = 0; a < size; a++) {
        var run = 1, cells = [get(a, 0)];
        for (var b = 1; b < size; b++) {
          var v = get(a, b);
          cells.push(v);
          if (v === get(a, b - 1)) { run++; } else { if (run >= 5) s += run - 2; run = 1; }
        }
        if (run >= 5) s += run - 2;
        var str = cells.map(function (c) { return c ? "1" : "0"; }).join("");
        var idx = -1;
        while ((idx = str.indexOf("10111010000", idx + 1)) !== -1) s += 40;
        idx = -1;
        while ((idx = str.indexOf("00001011101", idx + 1)) !== -1) s += 40;
      }
      return s;
    }
    score += line(function (a, b) { return m[a][b]; });
    score += line(function (a, b) { return m[b][a]; });
    for (y = 0; y < size - 1; y++) for (x = 0; x < size - 1; x++) {
      var c = m[y][x];
      if (c === m[y][x + 1] && c === m[y + 1][x] && c === m[y + 1][x + 1]) score += 3;
    }
    for (y = 0; y < size; y++) for (x = 0; x < size; x++) if (m[y][x]) dark++;
    score += 10 * Math.floor(Math.abs(dark * 100 / (size * size) - 50) / 5);
    return score;
  }

  /** Mã hoá [text] thành ma trận QR (true = ô đen). [forceMask] chỉ dùng để kiểm thử. */
  function encode(text, forceMask) {
    var bytes = utf8(text), version = 0;
    for (var v = 1; v <= 6; v++) {
      var spec = EC_M[v];
      if (4 + 8 + bytes.length * 8 <= (spec[2] - spec[0] * spec[1]) * 8) { version = v; break; }
    }
    if (!version) throw new Error("Nội dung quá dài cho QR phiên bản 1–6.");
    var codewords = buildCodewords(bytes, version);
    if (forceMask !== undefined) return makeMatrix(version, codewords, forceMask);
    var best = null, bestScore = Infinity;
    for (var mask = 0; mask < 8; mask++) {
      var m = makeMatrix(version, codewords, mask), s = penalty(m);
      if (s < bestScore) { best = m; bestScore = s; }
    }
    return best;
  }

  var api = { encode: encode, hmacSha256Hex: hmacSha256Hex };
  if (typeof module !== "undefined" && module.exports) module.exports = api;
  else root.KgQr = api;
})(this);
