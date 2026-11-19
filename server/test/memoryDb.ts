/** Firebase Realtime Database giả trong bộ nhớ, đủ cho test (kể cả kiểm tra multi-path chồng nhau). */
import type { Db, TransactionResult } from '../src/db.js';

type Tree = Record<string, unknown>;

const split = (path: string) => path.split('/').filter(Boolean);
const clone = <T>(value: T): T => (value === undefined ? (null as T) : structuredClone(value));

export class MemoryDb implements Db {
  data: Tree;
  private keyCounter = 0;
  /** Trả true để làm lần update() tương ứng ném lỗi (giả lập lỗi mạng). */
  failUpdate: ((updates: Record<string, unknown>) => boolean) | null = null;
  updateCalls: Record<string, unknown>[] = [];

  constructor(initial: Tree = {}) {
    this.data = structuredClone(initial);
  }

  read(path: string): unknown {
    let node: unknown = this.data;
    for (const part of split(path)) {
      if (!node || typeof node !== 'object') return null;
      node = (node as Tree)[part];
    }
    return node === undefined ? null : node;
  }

  private write(path: string, value: unknown) {
    const parts = split(path);
    if (parts.length === 0) {
      this.data = (value as Tree) ?? {};
      return;
    }
    let node = this.data;
    for (const part of parts.slice(0, -1)) {
      if (!node[part] || typeof node[part] !== 'object') node[part] = {};
      node = node[part] as Tree;
    }
    const last = parts[parts.length - 1];
    if (value === null || value === undefined) delete node[last];
    else node[last] = clone(value);
  }

  async get(path: string) {
    return clone(this.read(path));
  }

  async queryByChild(path: string, child: string, value: string) {
    const node = this.read(path);
    const out: Record<string, unknown> = {};
    if (node && typeof node === 'object') {
      for (const [key, item] of Object.entries(node as Tree)) {
        if (item && typeof item === 'object' && (item as Tree)[child] === value) out[key] = clone(item);
      }
    }
    return out;
  }

  async update(updates: Record<string, unknown>) {
    const paths = Object.keys(updates);
    for (const a of paths) {
      for (const b of paths) {
        if (a !== b && b.startsWith(`${a}/`)) throw new Error(`Multi-path chồng nhau: ${a} và ${b}`);
      }
    }
    if (this.failUpdate?.(updates)) throw new Error('Giả lập lỗi ghi Firebase');
    this.updateCalls.push(structuredClone(updates));
    for (const [path, value] of Object.entries(updates)) this.write(path, value);
  }

  async transaction(path: string, apply: (current: unknown) => unknown): Promise<TransactionResult> {
    const next = apply(clone(this.read(path)));
    if (next === undefined) return { committed: false, value: clone(this.read(path)) };
    this.write(path, next);
    return { committed: true, value: clone(this.read(path)) };
  }

  async remove(path: string) {
    this.write(path, null);
  }

  pushKey(path: string) {
    this.keyCounter += 1;
    return `-K${String(this.keyCounter).padStart(4, '0')}`;
  }
}
