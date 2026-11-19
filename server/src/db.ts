/**
 * Lớp truy cập dữ liệu tối thiểu mà server cần. Bản thật dùng firebase-admin (firebase.ts);
 * test dùng bản trong bộ nhớ (test/memoryDb.ts).
 */
export interface TransactionResult {
  committed: boolean;
  value: unknown;
}

export interface Db {
  /** Giá trị tại [path], null nếu không có. */
  get(path: string): Promise<unknown>;
  /** Các con của [path] có trường [child] == [value] (orderByChild().equalTo()). */
  queryByChild(path: string, child: string, value: string): Promise<Record<string, unknown>>;
  /** Multi-path update: thành công hết hoặc không ghi gì. Giá trị null = xoá. */
  update(updates: Record<string, unknown>): Promise<void>;
  /**
   * Transaction trên [path]. [apply] trả undefined để huỷ.
   * Lưu ý: firebase-admin có thể gọi [apply] lần đầu với null (chưa có cache), nên [apply] phải
   * xử lý đúng trường hợp null rồi để Firebase chạy lại với giá trị thật.
   */
  transaction(path: string, apply: (current: unknown) => unknown): Promise<TransactionResult>;
  remove(path: string): Promise<void>;
  /** Sinh key kiểu push() cho [path] (không ghi gì). */
  pushKey(path: string): string;
}

export interface AuthVerifier {
  /** Kiểm tra Firebase ID token, trả uid; ném lỗi nếu không hợp lệ. */
  verifyIdToken(token: string): Promise<{ uid: string }>;
}
