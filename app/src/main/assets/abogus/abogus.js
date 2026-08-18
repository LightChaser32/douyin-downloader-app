(function (global) {
/**
 * A-Bogus 依赖函数库
 * 包含 SM3 哈希、RC4 加密、Base64 编码等核心算法
 */

// ============================================================
// 辅助函数（用于类定义和类型检查）
// ============================================================

/**
 * 获取值的类型
 * @param {*} t - 要检查的值
 * @returns {string} 类型字符串
 */
function getType(t) {
  return "function" == typeof Symbol && "symbol" == typeof Symbol.iterator
    ? function (t) { return typeof t; }
    : function (t) {
        return t && "function" == typeof Symbol && t.constructor === Symbol && t !== Symbol.prototype
          ? "symbol"
          : typeof t;
      }(t);
}

/**
 * 转换为属性键名
 * @param {*} t - 要转换的值
 * @returns {string|symbol} 属性键
 */
function toPropertyKey(t) {
  var r = function (t, r) {
    if ("object" != getType(t) || !t) return t;
    var e = t[Symbol.toPrimitive];
    if (void 0 !== e) {
      var n = e.call(t, r || "default");
      if ("object" != getType(n)) return n;
      throw new TypeError("@@toPrimitive must return a primitive value.");
    }
    return ("string" === r ? String : Number)(t);
  }(t, "string");
  return "symbol" == getType(r) ? r : r + "";
}

/**
 * 定义类的属性（用于 ES5 类模拟）
 * @param {Object} target - 目标对象
 * @param {Array} descriptors - 属性描述符数组
 */
function defineClassProperties(target, descriptors) {
  for (var i = 0; i < descriptors.length; i++) {
    var desc = descriptors[i];
    desc.enumerable = desc.enumerable || false;
    desc.configurable = true;
    "value" in desc && (desc.writable = true);
    Object.defineProperty(target, toPropertyKey(desc.key), desc);
  }
}

// ============================================================
// SM3 哈希算法
// 中国国家密码标准（GB/T 32905-2016）
// ============================================================

/**
 * 循环左移
 * @param {number} value - 要移位的值
 * @param {number} bits - 移位位数
 * @returns {number} 移位后的值
 */
function rotateLeft(value, bits) {
  return (value << (bits %= 32) | value >>> 32 - bits) >>> 0;
}

/**
 * SM3 常量 Tj
 * @param {number} j - 轮次 (0-63)
 * @returns {number} 常量值
 */
function getConstantTj(j) {
  return 0 <= j && j < 16 ? 2043430169 : 16 <= j && j < 64 ? 2055708042 : void console.error("invalid j for constant Tj");
}

/**
 * SM3 布尔函数 FFj
 * @param {number} j - 轮次
 * @param {number} a, b, c - 输入值
 * @returns {number} 计算结果
 */
function boolFunctionFF(j, a, b, c) {
  return 0 <= j && j < 16 ? (a ^ b ^ c) >>> 0 : 16 <= j && j < 64 ? (a & b | a & c | b & c) >>> 0 : (console.error("invalid j for bool function FF"), 0);
}

/**
 * SM3 布尔函数 GGj
 * @param {number} j - 轮次
 * @param {number} a, b, c - 输入值
 * @returns {number} 计算结果
 */
function boolFunctionGG(j, a, b, c) {
  return 0 <= j && j < 16 ? (a ^ b ^ c) >>> 0 : 16 <= j && j < 64 ? (a & b | ~a & c) >>> 0 : (console.error("invalid j for bool function GG"), 0);
}

/**
 * SM3 哈希算法类
 * 用于计算消息的 SM3 哈希值
 */
const SM3 = function () {
  function SM3Hash() {
    if (!(this instanceof SM3Hash)) return new SM3Hash();
    this.reg = new Array(8);   // 8 个 32 位寄存器
    this.chunk = [];            // 数据块缓冲区
    this.size = 0;              // 已处理数据大小
    this.reset();
  }

  defineClassProperties(SM3Hash.prototype, [{
    key: "reset",
    value: function () {
      // 初始向量 IV
      this.reg[0] = 0x7380166f;  // 1937774191
      this.reg[1] = 0x4914b2b9;  // 1226093241
      this.reg[2] = 0x172442d7;  // 388252375
      this.reg[3] = 0xda8a0600;  // 3666478592
      this.reg[4] = 0xa96f30bc;  // 2842636476
      this.reg[5] = 0x163138aa;  // 372324522
      this.reg[6] = 0xe38dee4d;  // 3817729613
      this.reg[7] = 0xb0fb0e4e;  // 2969243214
      this.chunk = [];
      this.size = 0;
    }
  }, {
    key: "write",
    value: function (data) {
      // 字符串转字节数组
      var bytes = "string" == typeof data ? function (str) {
        var encoded = encodeURIComponent(str).replace(/%([0-9A-F]{2})/g, function (match, hex) {
          return String.fromCharCode("0x" + hex);
        });
        var arr = new Array(encoded.length);
        Array.prototype.forEach.call(encoded, function (char, i) {
          arr[i] = char.charCodeAt(0);
        });
        return arr;
      }(data) : data;

      this.size += bytes.length;
      var remaining = 64 - this.chunk.length;

      if (bytes.length < remaining) {
        this.chunk = this.chunk.concat(bytes);
      } else {
        for (this.chunk = this.chunk.concat(bytes.slice(0, remaining)); this.chunk.length >= 64;) {
          this._compress(this.chunk);
          if (remaining < bytes.length) {
            this.chunk = bytes.slice(remaining, Math.min(remaining + 64, bytes.length));
          } else {
            this.chunk = [];
          }
          remaining += 64;
        }
      }
    }
  }, {
    key: "sum",
    value: function (data, outputFormat) {
      if (data) {
        this.reset();
        this.write(data);
      }
      this._fill();

      for (var i = 0; i < this.chunk.length; i += 64) {
        this._compress(this.chunk.slice(i, i + 64));
      }

      var result = null;
      if ("hex" == outputFormat) {
        // 十六进制输出
        result = "";
        for (i = 0; i < 8; i++) {
          var hex = this.reg[i].toString(16);
          result += hex.length >= 8 ? hex : "0".repeat(8 - hex.length) + hex;
        }
      } else {
        // 字节数组输出
        result = new Array(32);
        for (i = 0; i < 8; i++) {
          var val = this.reg[i];
          result[4 * i + 3] = (255 & val) >>> 0;
          val >>>= 8;
          result[4 * i + 2] = (255 & val) >>> 0;
          val >>>= 8;
          result[4 * i + 1] = (255 & val) >>> 0;
          val >>>= 8;
          result[4 * i] = (255 & val) >>> 0;
        }
      }
      this.reset();
      return result;
    }
  }, {
    key: "_compress",
    value: function (block) {
      if (block < 64) {
        console.error("compress error: not enough data");
        return;
      }

      // 扩展消息
      var extended = function (block) {
        var w = new Array(132);

        // W0-W15: 从块中加载
        for (var i = 0; i < 16; i++) {
          w[i] = block[4 * i] << 24;
          w[i] |= block[4 * i + 1] << 16;
          w[i] |= block[4 * i + 2] << 8;
          w[i] |= block[4 * i + 3];
          w[i] >>>= 0;
        }

        // W16-W67: 消息扩展
        for (var j = 16; j < 68; j++) {
          var tmp = w[j - 16] ^ w[j - 9] ^ rotateLeft(w[j - 3], 15);
          tmp = tmp ^ rotateLeft(tmp, 15) ^ rotateLeft(tmp, 23);
          w[j] = (tmp ^ rotateLeft(w[j - 13], 7) ^ w[j - 6]) >>> 0;
        }

        // W'0-W'63
        for (j = 0; j < 64; j++) {
          w[j + 68] = (w[j] ^ w[j + 4]) >>> 0;
        }
        return w;
      }(block);

      // 压缩函数
      var regs = this.reg.slice(0);
      for (var n = 0; n < 64; n++) {
        var ss1 = rotateLeft(regs[0], 12) + regs[4] + rotateLeft(getConstantTj(n), n);
        ss1 = rotateLeft((4294967295 & ss1) >>> 0, 7);
        var tt1 = ((ss1 ^ rotateLeft(regs[0], 12)) >>> 0);
        var ff = boolFunctionFF(n, regs[0], regs[1], regs[2]);
        ff = (4294967295 & (ff + regs[3] + tt1 + extended[n + 68])) >>> 0;
        var gg = boolFunctionGG(n, regs[4], regs[5], regs[6]);
        gg = (4294967295 & (gg + regs[7] + ss1 + extended[n])) >>> 0;

        regs[3] = regs[2];
        regs[2] = rotateLeft(regs[1], 9);
        regs[1] = regs[0];
        regs[0] = ff;
        regs[7] = regs[6];
        regs[6] = rotateLeft(regs[5], 19);
        regs[5] = regs[4];
        regs[4] = (gg ^ rotateLeft(gg, 9) ^ rotateLeft(gg, 17)) >>> 0;
      }

      // 更新寄存器
      for (var c = 0; c < 8; c++) {
        this.reg[c] = (this.reg[c] ^ regs[c]) >>> 0;
      }
    }
  }, {
    key: "_fill",
    value: function () {
      var bitLength = 8 * this.size;
      var padPos = this.chunk.push(128) % 64;

      // 填充到 56 字节（留 8 字节给长度）
      if (64 - padPos < 8) padPos -= 64;
      for (; padPos < 56; padPos++) {
        this.chunk.push(0);
      }

      // 追加 64 位长度值
      for (var i = 0; i < 4; i++) {
        var highBits = Math.floor(bitLength / 4294967296);
        this.chunk.push(highBits >>> 8 * (3 - i) & 255);
      }
      for (i = 0; i < 4; i++) {
        this.chunk.push(bitLength >>> 8 * (3 - i) & 255);
      }
    }
  }]);

  return SM3Hash;
}();

// ============================================================
// RC4 加密算法
// ============================================================

/**
 * RC4 流加密算法
 * @param {string} key - 加密密钥
 * @param {string} data - 要加密的数据
 * @returns {string} 加密后的字符串
 */
function rc4Encrypt(key, data) {
  // 初始化 S 盒
  var sbox = [];
  for (var i = 0; i < 256; i++) {
    sbox[255 - i] = i;
  }

  // 密钥调度算法 (KSA)
  var j = 0;
  for (var i = 0; i < 256; i++) {
    j = (j * sbox[i] + j + key.charCodeAt(i % key.length)) % 256;
    // 交换 S[i] 和 S[j]
    var tmp = sbox[i];
    sbox[i] = sbox[j];
    sbox[j] = tmp;
  }

  // 伪随机生成算法 (PRGA)
  var i = 0, j2 = 0;
  var result = '';
  for (var k = 0; k < data.length; k++) {
    i = (i + 1) % 256;
    j2 = (j2 + sbox[i]) % 256;
    // 交换 S[i] 和 S[j]
    var tmp = sbox[i];
    sbox[i] = sbox[j2];
    sbox[j2] = tmp;
    // XOR 加密
    result += String.fromCharCode(data.charCodeAt(k) ^ sbox[(sbox[i] + sbox[j2]) % 256]);
  }
  return result;
}

// 别名（兼容旧代码）
const Ht = rc4Encrypt;
const RC4Like = rc4Encrypt;

// ============================================================
// 自定义 Base64 编码
// ============================================================

/**
 * Base64 字符表映射
 * s0: 标准 Base64
 * s1-s4: 抖音 a_bogus 专用字符表
 */
const BASE64_ALPHABETS = {
  s0: 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=',  // 标准
  s1: 'Dkdpgh4ZKsQB80/Mfvw36XI1R25+WUAlEi7NLboqYTOPuzmFjJnryx9HVGcaStCe=',  // abogus v1
  s2: 'Dkdpgh4ZKsQB80/Mfvw36XI1R25-WUAlEi7NLboqYTOPuzmFjJnryx9HVGcaStCe=',  // abogus v2
  s3: 'ckdp1h4ZKsUB80/Mfvw36XIgR25+WQAlEi7NLboqYTOPuzmFjJnryx9HVGDaStCe',   // abogus v3 (无填充)
  s4: 'Dkdpgh2ZmsQB80/MfvV36XI1R45-WUAlEixNLwoqYTOPuzKFjJnry79HbGcaStCe'    // abogus v4
};

/**
 * 自定义 Base64 编码
 * @param {string} data - 要编码的数据
 * @param {string} alphabetKey - 字符表键名 (s0-s4)
 * @returns {string} Base64 编码结果
 */
function base64Encode(data, alphabetKey) {
  var alphabet = BASE64_ALPHABETS[alphabetKey] || BASE64_ALPHABETS.s0;
  var result = '';

  // 每 3 字节编码为 4 个字符
  for (var i = 0; i + 3 <= data.length; i += 3) {
    var b0 = data.charCodeAt(i) & 255;
    var b1 = data.charCodeAt(i + 1) & 255;
    var b2 = data.charCodeAt(i + 2) & 255;
    var triplet = (b0 << 16) | (b1 << 8) | b2;

    result += alphabet.charAt((triplet & 16515072) >> 18);  // 前 6 位
    result += alphabet.charAt((triplet & 258048) >> 12);    // 中 6 位
    result += alphabet.charAt((triplet & 4032) >> 6);       // 后 6 位
    result += alphabet.charAt(triplet & 63);                // 最后 6 位
  }

  // 处理剩余字节
  if (data.length - i > 0) {
    var b0 = data.charCodeAt(i) & 255;
    var triplet = (b0 << 16) | (i + 1 < data.length ? (data.charCodeAt(i + 1) & 255) << 8 : 0);

    result += alphabet.charAt((triplet & 16515072) >> 18);
    result += alphabet.charAt((triplet & 258048) >> 12);
    result += (i + 1 < data.length) ? alphabet.charAt((triplet & 4032) >> 6) : '=';
    result += '=';
  }

  return result;
}

// 别名（兼容旧代码）
const qt = base64Encode;
const Base64Like = base64Encode;

// ============================================================
// User-Agent 加密
// ============================================================

/**
 * RC4 加密 User-Agent 字符串
 * @param {number} seed1 - 种子值1（通常为 1）
 * @param {number} seed2 - 种子值2（通常为 14）
 * @param {string} userAgent - User-Agent 字符串
 * @returns {string} 加密后的字符串
 */
function encryptUserAgent(seed1, seed2, userAgent) {
  // 构建密钥数组
  var keyBytes = new Array(3);
  keyBytes[0] = seed1 / 256;      // 高位字节
  keyBytes[1] = seed1 % 256;      // 低位字节
  keyBytes[2] = seed2 % 256;      // 种子2

  // RC4 加密
  return rc4Encrypt(String.fromCharCode.apply(null, keyBytes), userAgent.trim());
}

// 别名（兼容旧代码）
const m_728 = encryptUserAgent;

// ============================================================
// 随机标志位生成
// ============================================================

/**
 * 获取随机标志位数组
 * 用于签名计算中的随机性参数
 * @returns {number[]} 5 字节的标志位数组
 */
function getRandomFlags() {
  // [flag0, flag1, flag2, flag3, flag4]
  // flag4 包含配置标志 (129 = 0b10000001)
  return [0, 0, 0, 0, 129];
}

// 别名（兼容旧代码）
const nr = getRandomFlags;

// ============================================================
// 对象序列化
// ============================================================

/**
 * 将对象转换为管道分隔的字符串
 * @param {Object} obj - 要序列化的对象
 * @returns {string} 序列化后的字符串
 * @example
 * objectToString({ a: 1, b: 2 }) => "1|2"
 */
function objectToString(obj) {
  var result = '';
  var isFirst = true;

  Object.keys(obj).forEach(function (key) {
    if (isFirst) {
      result += obj[key];
      isFirst = false;
    } else {
      result += '|' + obj[key];
    }
  });

  return result;
}

// 别名（兼容旧代码）
const m_731 = objectToString;

// ============================================================
// 字符串转字节数组
// ============================================================

/**
 * 将字符串转换为字节数组
 * 处理 UTF-8 多字节字符
 * @param {string} str - 输入字符串
 * @returns {number[]} 字节数组
 */
function stringToByteArray(str) {
  var bytes = [];

  for (var i = 0; i < str.length; i++) {
    var charCode = str.charCodeAt(i);

    if (charCode & 65280) {  // 多字节字符 (> 255)
      bytes.push(charCode >> 8);      // 高字节
      bytes.push(charCode & 255);     // 低字节
    } else {
      bytes.push(charCode);
    }
  }

  return bytes;
}

// 别名（兼容旧代码）
const m_732 = stringToByteArray;

// ============================================================
// 随机数生成器
// ============================================================

/**
 * 生成随机字节值（用于前缀）
 * 根据标志位生成不同范围的随机数
 * @returns {number} 随机字节值 (0-255)
 */
function generateRandomByte() {
  var flags = getRandomFlags();

  if (flags[4] & 64) {
    // 模式1: 110-328 范围
    var r = Math.random() * 109 >> 0;
    return r + 110 + r % 2;
  } else {
    // 模式2: 0-240 范围
    var r = Math.random() * 240 >> 0;
    if (r > 109) {
      return r + r % 2 + 1;
    }
    return r;
  }
}

// 别名（兼容旧代码）
const m_716 = generateRandomByte;

/**
 * 获取 Mock 时间戳值
 * @returns {number} 固定时间戳值
 */
function getMockTimestamp() {
  return 179;
}

// 别名（兼容旧代码）
const m_717 = getMockTimestamp;

/**
 * 获取 Mock 浏览器名称
 * @returns {string} 浏览器名称
 */
function getMockBrowserName() {
  return "Chrome";
}

// 别名（兼容旧代码）
const m_715 = getMockBrowserName;

/**
 * 生成随机前缀字节
 * 用于 Base64 编码前的混淆
 * @param {number[]} seedBytes - 2 字节的种子数组
 * @param {number} mode - 生成模式 (0, 1, 2)
 * @returns {number[]} 4 字节的前缀数组
 */
function generateRandomPrefix(seedBytes, mode) {
  mode = mode || 0;
  var randomVal = Math.random() * 65535;
  var b0, b1;

  if (mode === 2) {
    // 模式2: 使用随机字节生成器
    b0 = generateRandomByte();
    b1 = getMockTimestamp();
  } else {
    // 模式0/1: 使用随机值
    b0 = randomVal & 255;
    b1 = mode === 1 ? getMockBrowserName() : (randomVal >> 8) & 255;
  }

  // 混合种子和随机值
  return [
    b0 & 170 | seedBytes[0] & 85,
    b0 & 85 | seedBytes[0] & 170,
    b1 & 170 | seedBytes[1] & 85,
    b1 & 85 | seedBytes[1] & 170
  ];
}

// 别名（兼容旧代码）
const m_718 = generateRandomPrefix;

// ============================================================
// 版本号处理
// ============================================================

/**
 * 处理版本号字符串为字节数组
 * 将版本号各部分转换为混淆后的字节
 * @param {string} versionStr - 版本号字符串 (如 "1.0.1.19-fix.01")
 * @returns {number[]} 8 字节的版本号数组
 */
function processVersionBytes(versionStr) {
  var parts = versionStr.split('.').map(function (p) { return ~~p; });  // 取整

  // 前两部分的随机前缀
  var prefix1 = generateRandomPrefix([parts[0], parts[1]]);
  // 后两部分的随机前缀（模式2）
  var prefix2 = generateRandomPrefix([parts[2], parts[3]], 2);

  return [
    prefix1[0], prefix1[1], prefix1[2], prefix1[3],
    prefix2[0], prefix2[1], prefix2[2], prefix2[3]
  ];
}

// 别名（兼容旧代码）
const m_733 = processVersionBytes;

// ============================================================
// 数组转换工具
// ============================================================

/**
 * 将输入转换为数组
 * 支持数组、可迭代对象、类数组对象
 * @param {*} input - 输入值
 * @returns {Array} 数组
 */
function toArray(input) {
  // 尝试作为可迭代对象
  var result = tryGetIterator(input);
  if (result) return result;

  // 尝试作为数组
  result = tryGetArray(input);
  if (result) return result;

  // 尝试作为类数组
  result = tryGetArrayLike(input);
  if (result) return result;

  // 失败则抛出错误
  throw new TypeError('Invalid attempt to spread non-iterable instance.\nIn order to be iterable, non-array objects must have a [Symbol.iterator]() method.');
}

/**
 * 尝试从可迭代对象获取数组
 */
function tryGetIterator(input) {
  var hasIterator = 'undefined' != typeof Symbol && null != input[Symbol.iterator] || null != input['@@iterator'];
  if (hasIterator) return Array.from(input);
}

/**
 * 尝试从数组获取副本
 */
function tryGetArray(input) {
  if (Array.isArray(input)) return copyArray(input);
}

/**
 * 复制数组
 */
function copyArray(arr, len) {
  len = null == len || len > arr.length ? arr.length : len;
  var result = Array(len);
  for (var i = 0; i < len; i++) {
    result[i] = arr[i];
  }
  return result;
}

/**
 * 尝试从类数组对象获取数组
 */
function tryGetArrayLike(input) {
  if (!input) return;

  if ('string' == typeof input) return copyArray(input);

  var typeName = {}.toString.call(input).slice(8, -1);
  var constructorName = 'Object' === typeName && input.constructor ? input.constructor.name : typeName;

  if ('Map' === constructorName || 'Set' === constructorName) {
    return Array.from(input);
  }

  if ('Arguments' === constructorName || /^(?:Ui|I)nt(?:8|16|32)(?:Clamped)?Array$/.test(constructorName)) {
    return copyArray(input);
  }
}

// 别名（兼容旧代码）
const m_734 = toArray;
const m_706 = tryGetIterator;
const m_705 = tryGetArray;
const m_709 = copyArray;
const m_707 = tryGetArrayLike;
const m_708 = function () { throw new TypeError('Invalid attempt to spread non-iterable instance.'); };

// ============================================================
// 字节混淆
// ============================================================

/**
 * 字节混淆函数
 * 将每 3 字节扩展为 4 字节，增加随机性
 * @param {number[]} inputBytes - 输入字节数组
 * @returns {number[]} 混淆后的字节数组
 */
function obfuscateBytes(inputBytes) {
  var output = [];

  for (var i = 0; i < inputBytes.length; i += 3) {
    if (i + 2 < inputBytes.length) {
      // 每 3 字节扩展为 4 字节
      var randomByte = Math.random() * 1000 & 255;
      var b0 = inputBytes[i];
      var b1 = inputBytes[i + 1];
      var b2 = inputBytes[i + 2];

      // 混合原始字节和随机字节
      output.push(
        randomByte & 145 | b0 & 110,
        randomByte & 66 | b1 & 189,
        randomByte & 44 | b2 & 211,
        b0 & 145 | b1 & 66 | b2 & 44  // 校验字节
      );
    } else {
      // 剩余不足 3 字节，直接输出
      output.push(inputBytes[i]);
      if (inputBytes[i + 1] !== undefined) {
        output.push(inputBytes[i + 1]);
      }
    }
  }

  return output;
}

// 别名（兼容旧代码）
const m_735 = obfuscateBytes;


/**
 * A-Bogus 签名算法
 * 用于抖音系 API 的请求签名验证
 * 
 * 算法流程：
 * 1. 计算请求参数、请求体、User-Agent 的 SM3 哈希
 * 2. 组合时间戳、随机种子、页面ID、应用ID等参数
 * 3. 计算校验字节
 * 4. RC4 加密 + 自定义 Base64 编码
 */



// ============================================================
// 常量定义
// ============================================================

/**
 * SM3 哈希盐值
 * - "dhzx": 移动端/通用
 * - "cus": PC 端
 */
const HASH_SALT = "dhzx";

/**
 * 周期基准日期: 2024-07-25 00:00:00 UTC
 * 用于计算 14 天周期数
 */
const CYCLE_BASE_DATE = 1721836800000;

/**
 * 周期天数
 */
const CYCLE_DAYS = 14;

/**
 * 固定 XOR 值（用于校验计算）
 */
const FIXED_XOR_VALUE = 41;

/**
 * RC4 密钥字节
 */
const RC4_KEY_BYTE = 211;

// ============================================================
// 全局状态
// ============================================================

/**
 * 请求计数器
 * 用于确定签名版本类型
 */
var requestCounter = 0;

/**
 * 初始化时间戳
 */
var initTimestamp = Date.now();

// ============================================================
// 辅助函数
// ============================================================

/**
 * 根据请求次数确定签名版本类型
 * 返回值对应不同的签名算法版本
 * @returns {number} 版本类型 (3-6)
 */
function getVersionType() {
  if (requestCounter > 10745) return 3;
  if (requestCounter > 1283) return 4;
  if (requestCounter > 139) return 5;
  return 6;
}

/**
 * 取整函数
 * @param {number} n - 输入数值
 * @returns {number} 整数部分
 */
function floorInt(n) {
  return ~~n;
}

/**
 * 将整数拆分为字节数组
 * @param {number} value - 要拆分的值
 * @param {number} byteCount - 字节数 (1-6)
 * @returns {number[]} 字节数组
 */
function intToBytes(value, byteCount) {
  var bytes = [];
  for (var i = 0; i < byteCount; i++) {
    if (i < 4) {
      bytes.push((value >> (8 * i)) & 255);
    } else {
      // 超过 32 位，需要除法
      var divisor = Math.pow(256, i);
      bytes.push(Math.floor(value / divisor) & 255);
    }
  }
  return bytes;
}

// ============================================================
// BDMS 签名类
// ============================================================

/**
 * BDMS 签名计算器
 * 用于生成抖音 API 的 a_bogus 签名
 */
class BDMS {

  /**
   * @param {string} userAgent - User-Agent 字符串
   * @param {Object} fingerprint - 浏览器指纹参数
   * @param {number} fingerprint.innerWidth - 内部窗口宽度
   * @param {number} fingerprint.innerHeight - 内部窗口高度
   * @param {number} fingerprint.outerWidth - 外部窗口宽度
   * @param {number} fingerprint.outerHeight - 外部窗口高度
   * @param {number} fingerprint.availWidth - 可用屏幕宽度
   * @param {number} fingerprint.availHeight - 可用屏幕高度
   * @param {number} fingerprint.sizeWidth - 屏幕宽度
   * @param {number} fingerprint.sizeHeight - 屏幕高度
   * @param {string} fingerprint.platform - 平台标识 (如 "Linux armv81", "Win32")
   */
  constructor(userAgent, fingerprint = null) {
    this.userAgent = userAgent;
    // 默认指纹 (Android Chrome 真机参数)
    this.fingerprint = fingerprint || {
      innerWidth: 980,
      innerHeight: 1762,
      outerWidth: 400,
      outerHeight: 890,
      availWidth: 400,
      availHeight: 890,
      sizeWidth: 400,
      sizeHeight: 890,
      platform: "Linux armv81"
    };
  }

  /**
   * 计算 a_bogus 签名
   * @param {number} _arg0 - 固定值 1（未使用）
   * @param {number} _arg1 - 固定值 0（未使用）
   * @param {number} _arg2 - 固定值 8（未使用）
   * @param {string} queryString - 请求参数字符串
   * @param {string} requestBody - 请求体，默认为空字符串
   * @param {string} _userAgent - User-Agent（未使用，从构造函数获取）
   * @param {number} pageId - 页面ID
   *   - 9999: 移动端 H5 页面
   *   - 6241: PC 端页面
   * @param {number} appId - 应用ID
   *   - 1128: 抖音移动端
   *   - 6383: 抖音 PC 端
   * @param {string} version - bdms 版本号，如 "1.0.1.19-fix.01"
   * @returns {string} a_bogus 签名字符串
   */
  calculateABogus(_arg0, _arg1, _arg2, queryString, requestBody, _userAgent, pageId, appId, version) {
    // 更新请求计数器
    requestCounter = requestCounter + 1;

    // --------------------------------------------------------
    // 第一阶段：计算基础参数
    // --------------------------------------------------------

    // 当前时间戳（毫秒）
    const timestamp = Date.now();

    // Mock 版本类型（实际应从 getVersionType() 获取）
    const mockVersionType = 3;

    // SM3 哈希实例
    const sm3 = new SM3();

    // RC4 加密种子参数
    const rc4Seed1 = 1;
    const rc4Seed2 = 14;

    // --------------------------------------------------------
    // 第二阶段：计算各类哈希值
    // --------------------------------------------------------

    // 请求参数的 SM3 哈希（双重哈希 + 盐值）
    const paramsHash = sm3.sum(sm3.sum(queryString + HASH_SALT));

    // 请求体的 SM3 哈希
    const bodyHash = sm3.sum(sm3.sum((requestBody || '') + HASH_SALT));

    // User-Agent 的 SM3 哈希
    // 步骤：RC4加密 -> Base64编码(s3) -> SM3哈希
    const uaEncrypted = encryptUserAgent(rc4Seed1, rc4Seed2, this.userAgent);
    const uaBase64 = base64Encode(uaEncrypted, 's3');
    const uaHash = sm3.sum(uaBase64);

    // Mock 时间戳（用于混淆）
    const mockTimestamp = Date.now();

    // --------------------------------------------------------
    // 第三阶段：准备参数字节
    // --------------------------------------------------------

    // Base64 前缀随机种子
    const prefixSeed = [3, 82];

    // 当前版本类型
    const versionType = getVersionType();

    // 计算日期周期数（从基准日期起，每14天一个周期）
    const dateCycle = Math.floor((timestamp - CYCLE_BASE_DATE) / (1000 * 60 * 60 * 24 * CYCLE_DAYS));

    // 时间戳偏移量
    const timestampOffset = initTimestamp > 0 
      ? (timestamp - initTimestamp + 3) & 255 
      : 2;

    // 时间戳各字节（6 字节）
    const tsBytes = intToBytes(timestamp, 6);

    // RC4 种子1的字节表示
    const seed1Bytes = intToBytes(rc4Seed1, 2);

    // 获取随机指纹参数
    const randomFlags = getRandomFlags();

    // 指纹参数的字节表示
    const flagBytes = [
      randomFlags[4] & 255,        // flag byte 0
      (randomFlags[4] >> 8) & 255, // flag byte 1
      randomFlags[0],
      randomFlags[1],
      randomFlags[2],
      randomFlags[3]
    ];

    // RC4 种子2的字节表示（4 字节）
    const seed2Bytes = intToBytes(rc4Seed2, 4);

    // --------------------------------------------------------
    // 第四阶段：从哈希中提取索引
    // --------------------------------------------------------

    // 从请求参数哈希提取
    let paramsIndex = paramsHash[9];
    let paramsIndexAlt = paramsHash[18];
    let paramsSearchIdx = 3;
    let paramsSearchVal = paramsHash[3];

    // 跳过值为 11 的位置
    while (paramsSearchVal === 11) {
      paramsSearchIdx++;
      paramsSearchVal = paramsSearchIdx < paramsHash.length ? paramsHash[paramsSearchIdx] : 12;
    }

    // 根据标志位选择索引
    const paramsFinalIndex = (randomFlags[4] & 2) ? 11 : paramsSearchVal;

    // 从请求体哈希提取
    let bodyIndex = bodyHash[10];
    let bodyIndexAlt = bodyHash[19];
    let bodySearchIdx = 4;
    let bodySearchVal = bodyHash[4];

    // 跳过值为 8 的位置
    while (bodySearchVal === 8) {
      bodySearchIdx++;
      bodySearchVal = bodySearchIdx < bodyHash.length ? bodyHash[bodySearchIdx] : 9;
    }

    const bodyFinalIndex = (randomFlags[4] & 4) ? 8 : bodySearchVal;

    // 从 UA 哈希提取
    let uaIndex = uaHash[11];
    let uaIndexAlt = uaHash[21];
    let uaSearchIdx = 5;
    let uaSearchVal = uaHash[5];

    // 跳过值为 12 的位置
    while (uaSearchVal === 12) {
      uaSearchIdx++;
      uaSearchVal = uaSearchIdx < uaHash.length ? uaHash[uaSearchIdx] : 13;
    }

    const uaFinalIndex = (randomFlags[4] & 8) ? 12 : uaSearchVal;

    // --------------------------------------------------------
    // 第五阶段：准备 ID 和指纹字节
    // --------------------------------------------------------

    // Mock 时间戳字节
    const mockTsBytes = intToBytes(mockTimestamp, 6);

    // pageId 字节
    const pageIdBytes = intToBytes(pageId, 4);

    // appId 字节
    const appIdBytes = intToBytes(appId, 4);

    // 浏览器指纹转字节数组
    const fingerprintStr = objectToString(this.fingerprint);
    const fingerprintBytes = stringToByteArray(fingerprintStr);
    const fingerprintLen = fingerprintBytes.length;
    const fingerprintLenBytes = intToBytes(fingerprintLen, 2);

    // 时间数组
    const timeArrStr = ((timestamp + 3) & 255) + ',';
    const timeArrBytes = stringToByteArray(timeArrStr);
    const timeArrLen = timeArrBytes.length;
    const timeArrLenBytes = intToBytes(timeArrLen, 2);

    // --------------------------------------------------------
    // 第六阶段：计算校验字节
    // --------------------------------------------------------

    // 处理版本号
    const versionBytes = processVersionBytes(version);

    // 版本号异或值
    const versionXor = versionBytes[0] ^ versionBytes[1] ^ versionBytes[2] ^ versionBytes[3] 
                     ^ versionBytes[4] ^ versionBytes[5];

    // 计算最终校验字节
    let checksum = versionXor ^ versionBytes[6] ^ versionBytes[7] 
                  ^ FIXED_XOR_VALUE ^ dateCycle ^ versionType ^ timestampOffset;

    checksum = checksum ^ tsBytes[0] ^ tsBytes[1] ^ tsBytes[2] ^ tsBytes[3] ^ tsBytes[4] ^ tsBytes[5]
             ^ seed1Bytes[0] ^ seed1Bytes[1];

    checksum = checksum ^ flagBytes[0] ^ flagBytes[1] ^ flagBytes[2] ^ flagBytes[3] ^ flagBytes[4] ^ flagBytes[5]
             ^ seed2Bytes[0] ^ seed2Bytes[1];

    checksum = checksum ^ seed2Bytes[2] ^ seed2Bytes[3] ^ paramsIndex ^ paramsIndexAlt ^ paramsFinalIndex
             ^ bodyIndex ^ bodyIndexAlt ^ bodyFinalIndex;

    checksum = checksum ^ uaIndex ^ uaIndexAlt ^ uaFinalIndex 
             ^ mockTsBytes[0] ^ mockTsBytes[1] ^ mockTsBytes[2] ^ mockTsBytes[3] ^ mockTsBytes[4] ^ mockTsBytes[5];

    checksum = checksum ^ mockVersionType 
             ^ pageIdBytes[0] ^ pageIdBytes[1] ^ pageIdBytes[2] ^ pageIdBytes[3]
             ^ appIdBytes[0] ^ appIdBytes[1];

    // --------------------------------------------------------
    // 第七阶段：构建参数数组（50 字节）
    // --------------------------------------------------------

    const paramArray = new Array(50);
    paramArray[0] = tsBytes[5];              // 时间戳字节5
    paramArray[1] = seed2Bytes[0];           // 种子2字节0
    paramArray[2] = uaIndex;                 // UA哈希索引
    paramArray[3] = mockTsBytes[1];          // Mock时间戳字节1
    paramArray[4] = appIdBytes[2];           // appId字节2
    paramArray[5] = tsBytes[0];              // 时间戳字节0
    paramArray[6] = pageIdBytes[3];          // pageId字节3
    paramArray[7] = seed2Bytes[1];           // 种子2字节1
    paramArray[8] = seed1Bytes[0];           // 种子1字节0
    paramArray[9] = paramsIndexAlt;          // 参数哈希索引
    paramArray[10] = flagBytes[0];           // 标志字节0
    paramArray[11] = mockVersionType;        // Mock版本类型
    paramArray[12] = paramsFinalIndex;       // 参数最终索引
    paramArray[13] = pageIdBytes[1];         // pageId字节1
    paramArray[14] = timestampOffset;        // 时间戳偏移
    paramArray[15] = paramsIndex;            // 参数哈希索引
    paramArray[16] = mockTsBytes[4];         // Mock时间戳字节4
    paramArray[17] = seed2Bytes[3];          // 种子2字节3
    paramArray[18] = tsBytes[1];             // 时间戳字节1
    paramArray[19] = appIdBytes[0];          // appId字节0
    paramArray[20] = dateCycle;              // 日期周期数
    paramArray[21] = bodyFinalIndex;         // 请求体最终索引
    paramArray[22] = tsBytes[2];             // 时间戳字节2
    paramArray[23] = pageIdBytes[2];         // pageId字节2
    paramArray[24] = uaFinalIndex;           // UA最终索引
    paramArray[25] = flagBytes[2];           // 标志字节2
    paramArray[26] = mockTsBytes[2];         // Mock时间戳字节2
    paramArray[27] = mockTsBytes[3];         // Mock时间戳字节3
    paramArray[28] = versionType;            // 版本类型
    paramArray[29] = appIdBytes[1];          // appId字节1
    paramArray[30] = flagBytes[3];           // 标志字节3
    paramArray[31] = appIdBytes[3];          // appId字节3
    paramArray[32] = uaIndexAlt;             // UA哈希索引备选
    paramArray[33] = bodyIndex;              // 请求体哈希索引
    paramArray[34] = flagBytes[4];           // 标志字节4
    paramArray[35] = flagBytes[1];           // 标志字节1
    paramArray[36] = tsBytes[4];             // 时间戳字节4
    paramArray[37] = pageIdBytes[0];         // pageId字节0
    paramArray[38] = bodyIndexAlt;           // 请求体哈希索引备选
    paramArray[39] = flagBytes[5];           // 标志字节5
    paramArray[40] = mockTsBytes[5];         // Mock时间戳字节5
    paramArray[41] = seed2Bytes[2];          // 种子2字节2
    paramArray[42] = seed1Bytes[1];          // 种子1字节1
    paramArray[43] = FIXED_XOR_VALUE;        // 固定XOR值
    paramArray[44] = mockTsBytes[0];         // Mock时间戳字节0
    paramArray[45] = tsBytes[3];             // 时间戳字节3
    paramArray[46] = fingerprintLenBytes[0]; // 指纹长度字节0
    paramArray[47] = fingerprintLenBytes[1]; // 指纹长度字节1
    paramArray[48] = timeArrLenBytes[0];     // 时间数组长度字节0
    paramArray[49] = timeArrLenBytes[1];     // 时间数组长度字节1

    // --------------------------------------------------------
    // 第八阶段：最终加密和编码
    // --------------------------------------------------------

    // 最终校验字节数组
    const finalChecksum = new Array(1);
    finalChecksum[0] = checksum ^ appIdBytes[2] ^ appIdBytes[3] 
                      ^ fingerprintLenBytes[0] ^ fingerprintLenBytes[1] 
                      ^ timeArrLenBytes[0] ^ timeArrLenBytes[1];

    // 构建数据块
    const dataBlock = toArray(versionBytes)
      .concat(toArray(obfuscateBytes(paramArray.concat(
        toArray(fingerprintBytes),
        toArray(timeArrBytes),
        finalChecksum
      ))));

    // RC4 加密
    const rc4Key = String.fromCharCode(RC4_KEY_BYTE);
    const rc4Data = String.fromCharCode.apply(null, dataBlock);
    const encrypted = rc4Encrypt(rc4Key, rc4Data);

    // 生成前缀
    const prefixBytes = generateRandomPrefix(prefixSeed, 1);
    const prefixStr = String.fromCharCode.apply(String, toArray(prefixBytes));

    // Base64 编码（使用 s4 字符表）
    const finalBase64 = base64Encode(prefixStr + encrypted, 's4');

    return finalBase64;
  }
}


/**
 * 抖音 __ac_signature 签名生成模块
 * 
 * __ac_signature 是抖音 Cookie 中的一个签名字段，用于验证请求的合法性。
 * 格式示例: _02B4Z6wo00f01XXXXXXXXXXXX
 * 
 * 参考: sources/dynew/DouyinLiveWebFetcher-main/ac_signature.py
 * 
 * 使用方法:
 *   import { generateAcSignature } from './abogus/ac_signature.js';
 *   const signature = generateAcSignature('www.douyin.com', 'abc123', 'Mozilla/5.0...');
 */

/**
 * 计算字符串的哈希值 (方法1)
 * 使用 XOR 和乘法进行哈希计算
 * 
 * @param {string} str - 输入字符串
 * @param {number} iv - 初始向量
 * @returns {number} 32位无符号整数哈希值
 */
function hashString1(str, iv) {
  let k = iv >>> 0; // 确保是无符号32位整数
  for (let i = 0; i < str.length; i++) {
    const charCode = str.charCodeAt(i);
    // 模拟 JavaScript 的 >>> 0 (无符号右移0位，确保32位无符号)
    k = ((k ^ charCode) * 65599) >>> 0;
  }
  return k;
}

/**
 * 计算字符串的哈希值 (方法2)
 * 使用字符串长度和索引进行哈希计算
 * 
 * @param {string} str - 输入字符串
 * @param {number} iv - 初始向量
 * @returns {number} 32位无符号整数哈希值
 */
function hashString2(str, iv) {
  let k = iv >>> 0;
  const len = str.length;
  
  // 32次迭代计算
  for (let i = 0; i < 32; i++) {
    // 使用 k % len 作为索引确保在字符串范围内
    const charIndex = k % len;
    k = ((k * 65599) + str.charCodeAt(charIndex)) >>> 0;
  }
  return k;
}

/**
 * 计算字符串的哈希值 (方法3)
 * 使用纯乘法进行哈希计算
 * 
 * @param {string} str - 输入字符串
 * @param {number} iv - 初始向量
 * @returns {number} 32位无符号整数哈希值
 */
function hashString3(str, iv) {
  let k = iv >>> 0;
  for (let i = 0; i < str.length; i++) {
    k = ((k * 65599) + str.charCodeAt(i)) >>> 0;
  }
  return k;
}

/**
 * 将数字编码转换为字符
 * 
 * 编码规则:
 * - 0-25  -> A-Z (大写字母)
 * - 26-51 -> a-z (小写字母)
 * - 52-61 -> 0-9 (数字)
 * - 62-63 -> + / (特殊字符)
 * 
 * @param {number} code - 编码值 (0-63)
 * @returns {string} 对应的字符
 */
function encodeChar(code) {
  if (code < 26) {
    // A-Z (ASCII 65-90)
    return String.fromCharCode(code + 65);
  } else if (code < 52) {
    // a-z (ASCII 97-122)
    // 71 = 97 - 26
    return String.fromCharCode(code + 71);
  } else if (code < 62) {
    // 0-9 (ASCII 48-57)
    // -4 = 48 - 52
    return String.fromCharCode(code - 4);
  } else {
    // + / (ASCII 43, 47)
    // -17 = 43 - 60, -17 = 47 - 64
    return String.fromCharCode(code - 17);
  }
}

/**
 * 将32位整数编码为4字符字符串
 * 
 * 将32位整数分成4组，每组6位 (共24位)，然后编码为字符
 * 
 * @param {number} num - 32位无符号整数
 * @returns {string} 4字符编码字符串
 */
function encodeNumToStr(num) {
  let result = '';
  // 从高位到低位，每次取6位
  for (let i = 24; i >= 0; i -= 6) {
    // 提取6位数据 (& 0x3F = & 63)
    const bits = (num >>> i) & 0x3F;
    result += encodeChar(bits);
  }
  return result;
}

/**
 * 生成 __ac_signature 签名
 * 
 * @param {string} site - 网站域名 (如 'www.douyin.com')
 * @param {string} nonce - 随机字符串 (通常使用随机生成的字符串)
 * @param {string} userAgent - User-Agent 字符串
 * @param {number} [timestamp] - 时间戳 (可选，默认为当前时间)
 * @returns {string} __ac_signature 签名字符串
 * 
 * @example
 * const sig = generateAcSignature(
 *   'www.douyin.com',
 *   'abc123randomstring',
 *   'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/126.0.0.0'
 * );
 * // 输出类似: _02B4Z6wo00f01XXXXXXXXXXXX
 */
function generateAcSignature(site, nonce, userAgent, timestamp = Math.floor(Date.now() / 1000)) {
  // 签名固定头部
  const SIGN_HEAD = '_02B4Z6wo00f01';
  
  // 将时间戳转为字符串
  const timestampStr = String(timestamp);
  
  // ── 步骤1: 计算 a ─────────────────────────────────────────────────────
  // a = hash(time_str, 0) -> hash(site, result) % 65521
  // 65521 是最大的小于 65536 的质数
  const a = hashString1(site, hashString1(timestampStr, 0)) % 65521;
  
  // ── 步骤2: 计算 b ─────────────────────────────────────────────────────
  // 创建二进制字符串: "10000000110000" + 32位二进制字符串
  // 异或值: timestamp ^ (a * 65521)
  const xorValue = timestamp ^ (a * 65521);
  const binStr = xorValue.toString(2).padStart(32, '0');
  const b = parseInt('10000000110000' + binStr, 2);
  const bStr = String(b);
  
  // ── 步骤3: 计算 c ─────────────────────────────────────────────────────
  const c = hashString1(bStr, 0);
  
  // ── 步骤4: 计算 d, e, f, g, h, i ───────────────────────────────────────
  // d: 编码 b >> 2
  const d = encodeNumToStr(b >>> 2);
  
  // e: b 的高位部分 (模拟 64 位右移)
  // JavaScript 中 Number 最大安全整数是 2^53-1，这里直接计算
  const e = Math.floor(b / 4294967296) >>> 0;
  
  // f: 编码 (b << 28) | (e >> 4)
  // 注意: JavaScript 中 << 会先转为 32 位，需要特殊处理
  const f = encodeNumToStr(((b << 28) >>> 0) | (e >>> 4));
  
  // g: 异或值
  const g = 582085784 ^ b;
  
  // h: 编码 (e << 26) | (g >> 6)
  const h = encodeNumToStr(((e << 26) >>> 0) | (g >>> 6));
  
  // i: g 的低6位编码
  const i = encodeChar(g & 0x3F);
  
  // ── 步骤5: 计算 j, k, l, m ─────────────────────────────────────────────
  // j: 组合 UA 和 nonce 的哈希值
  const uaHash = hashString1(userAgent, c) % 65521;
  const nonceHash = hashString1(nonce, c) % 65521;
  const j = ((uaHash << 16) | nonceHash) >>> 0;
  
  // k: 编码 j >> 2
  const k = encodeNumToStr(j >>> 2);
  
  // l: 编码 (j << 28) | ((524576 ^ b) >> 4)
  const l = encodeNumToStr(((j << 28) >>> 0) | ((524576 ^ b) >>> 4));
  
  // m: 编码 a
  const m = encodeNumToStr(a);
  
  // ── 步骤6: 组合各部分 ─────────────────────────────────────────────────
  const n = SIGN_HEAD + d + f + h + i + k + l + m;
  
  // ── 步骤7: 计算校验位 ─────────────────────────────────────────────────
  // 计算最终签名的哈希值，取最后两位作为校验
  const finalHash = hashString3(n, 0);
  const hashHex = finalHash.toString(16);
  const o = hashHex.slice(-2).padStart(2, '0');
  
  // 最终签名
  return n + o;
}

/**
 * 生成随机 nonce 字符串
 * 用于 __ac_signature 签名
 * 
 * @param {number} length - 字符串长度 (默认 16)
 * @returns {string} 随机字符串
 */
function generateNonce(length = 16) {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
  let result = '';
  for (let i = 0; i < length; i++) {
    result += chars.charAt(Math.floor(Math.random() * chars.length));
  }
  return result;
}

/**
 * 生成完整的 __ac_signature Cookie 值
 * 
 * @param {string} userAgent - User-Agent 字符串
 * @param {string} [site] - 网站域名 (默认 'www.douyin.com')
 * @returns {string} __ac_signature Cookie 值
 */
function generateAcSignatureCookie(userAgent, site = 'www.douyin.com') {
  const nonce = generateNonce();
  return generateAcSignature(site, nonce, userAgent);
}


/**
 * 抖音 a_bogus 签名模块
 * 
 * 用于生成抖音 API 请求所需的 a_bogus 签名参数
 * 基于反编译的 bdms 1.0.1.19-fix.01 版本
 * 
 * 使用方法:
 *   import { generateABogus, BDMS } from '../abogus/index.js';
 *   const aBogus = generateABogus(queryString, userAgent);
 */



// 默认指纹配置 (Android Chrome 真机参数)
let fingerprint = {
  innerWidth: 980,
  innerHeight: 1762,
  outerWidth: 400,
  outerHeight: 890,
  availWidth: 400,
  availHeight: 890,
  sizeWidth: 400,
  sizeHeight: 890,
  platform: "Linux armv81"
};

// 常见屏幕分辨率（用于生成合理的指纹参数）
const SCREEN_PRESETS = {
  // 移动端分辨率
  mobile: [
    { width: 360, height: 640 },   // 常见 Android
    { width: 375, height: 667 },   // iPhone 6/7/8
    { width: 390, height: 844 },   // iPhone 12/13/14
    { width: 393, height: 851 },   // Pixel 7
    { width: 412, height: 915 },   // Samsung Galaxy
    { width: 414, height: 896 },   // iPhone 11/XR
    { width: 428, height: 926 },   // iPhone 12/13/14 Pro Max
  ],
  // PC 端分辨率
  desktop: [
    { width: 1366, height: 768 },  // 最常见笔记本
    { width: 1440, height: 900 },  // MacBook Air
    { width: 1536, height: 864 },  // 常见缩放
    { width: 1600, height: 900 },  // 常见显示器
    { width: 1920, height: 1080 }, // 全高清
    { width: 2560, height: 1440 }, // 2K 显示器
  ]
};

/**
 * 生成随机浏览器指纹
 * @param {string} platformType - 平台类型: "mobile" 或 "desktop"，默认自动检测
 * @param {string} userAgent - User-Agent 字符串，用于自动检测平台类型
 * @returns {Object} 指纹对象
 */
function generateFingerprint(platformType = null, userAgent = null) {
  // 自动检测平台类型
  let type = platformType;
  if (!type && userAgent) {
    type = /mobile|android|iphone|ipad/i.test(userAgent) ? 'mobile' : 'desktop';
  }
  if (!type) {
    type = 'mobile'; // 默认移动端
  }

  const presets = SCREEN_PRESETS[type];
  const screen = presets[Math.floor(Math.random() * presets.length)];

  // 生成合理的浏览器窗口参数
  if (type === 'mobile') {
    // 移动端：窗口通常等于或略小于屏幕
    const innerWidth = screen.width;
    const innerHeight = Math.floor(screen.height * (0.85 + Math.random() * 0.1)); // 85%-95% 高度
    const outerWidth = innerWidth;
    const outerHeight = screen.height;
    const availWidth = screen.width;
    const availHeight = screen.height - Math.floor(Math.random() * 80); // 减去状态栏/导航栏

    return {
      innerWidth,
      innerHeight,
      outerWidth,
      outerHeight,
      availWidth,
      availHeight,
      sizeWidth: screen.width,
      sizeHeight: screen.height,
      platform: "Linux armv81"
    };
  } else {
    // 桌面端：窗口通常小于屏幕，有一定随机性
    const innerWidth = Math.floor(screen.width * (0.6 + Math.random() * 0.35)); // 60%-95% 宽度
    const innerHeight = Math.floor(screen.height * (0.7 + Math.random() * 0.25)); // 70%-95% 高度
    const outerWidth = innerWidth + Math.floor(Math.random() * 20); // 边框
    const outerHeight = innerHeight + Math.floor(70 + Math.random() * 30); // 标题栏等
    const availWidth = screen.width;
    const availHeight = screen.height - Math.floor(30 + Math.random() * 50); // 任务栏

    return {
      innerWidth,
      innerHeight,
      outerWidth,
      outerHeight,
      availWidth,
      availHeight,
      sizeWidth: screen.width,
      sizeHeight: screen.height,
      platform: "Win32"
    };
  }
}

// 默认 UA (Android Edge Mobile)
const DEFAULT_UA = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Mobile Safari/537.36 EdgA/145.0.0.0";

// 默认配置
const DEFAULT_CONFIG = {
  // pageId: 页面ID
  //   - 9999: 移动端 H5 页面（slidesinfo 等接口）
  //   - 6241: PC 端页面
  pageId: 9999,
  
  // appId: 应用ID
  //   - 1128: 抖音移动端
  //   - 6383: 抖音 PC 端（aweme/detail 等接口）
  // 注意：不同接口可能需要不同的 appId
  appId: 1128,
  
  // bdms 版本号 (不同平台使用不同版本)
  //   - douyin (抖音): 1.0.1.19-fix
  //   - tuan (团长): 1.0.1.15
  //   - ju (巨量百应): 1.0.1.20
  //   - doudian (抖店): 1.0.1.1
  //   - qc (巨量千川): 1.0
  version: "1.0.1.19-fix.01"
};

/**
 * 设置浏览器指纹
 * @param {Object} fp - 指纹对象
 */
function setFingerprint(fp) {
  fingerprint = { ...fingerprint, ...fp };
  console.log('[ABOGUS] 指纹已更新:', fingerprint);
}

/**
 * 生成 a_bogus 签名
 * @param {string} queryString - URL 查询参数字符串 (不含 a_bogus)
 * @param {string} userAgent - User-Agent 字符串
 * @param {Object} config - 可选配置
 * @param {number} config.pageId - 页面ID (9999=移动端, 6241=PC端)
 * @param {number} config.appId - 应用ID (1128=移动端, 6383=PC端)
 * @param {string} config.version - bdms 版本号
 * @param {Object} config.fingerprint - 自定义指纹对象
 * @param {boolean} config.useRandomFingerprint - 是否使用随机指纹（优先级高于 fingerprint）
 * @param {string} config.platformType - 随机指纹平台类型: "mobile" 或 "desktop"
 * @returns {string} a_bogus 签名
 */
function generateABogus(queryString, userAgent = DEFAULT_UA, config = {}) {
  const { 
    pageId, 
    appId, 
    version, 
    fingerprint: customFingerprint,
    useRandomFingerprint = false,
    platformType = null
  } = { ...DEFAULT_CONFIG, ...config };
  
  // 确定使用的指纹：随机指纹 > 自定义指纹 > 默认指纹
  let fp;
  if (useRandomFingerprint) {
    fp = generateFingerprint(platformType, userAgent);
  } else {
    fp = customFingerprint || fingerprint;
  }
  
  const bdms = new BDMS(userAgent, fp);
  
  const aBogus = bdms.calculateABogus(
    1, 0, 8,
    queryString,
    "",
    userAgent,
    pageId,
    appId,
    version
  );
  
  return aBogus;
}

/**
 * 获取当前指纹配置
 */
function getFingerprint() {
  return { ...fingerprint };
}




global.__abogus = {
  generateABogus: function (queryString, userAgent) {
    return generateABogus(queryString, userAgent, { useRandomFingerprint: true, platformType: "desktop" });
  },
  generateABogusMobile: function (queryString, userAgent) {
    return generateABogus(queryString, userAgent, { useRandomFingerprint: true, platformType: "mobile" });
  }
};
})(typeof window !== "undefined" ? window : this);
