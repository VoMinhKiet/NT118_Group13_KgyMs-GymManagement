/** Lỗi trả về cho client dạng { "error": "<mã>", "message": "<tiếng Việt>" }. */
export class HttpError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string
  ) {
    super(message);
  }
}

export const badRequest = (code: string, message: string) => new HttpError(400, code, message);
