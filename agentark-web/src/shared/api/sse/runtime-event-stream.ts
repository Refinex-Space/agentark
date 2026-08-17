import type { CredentialProvider } from "../http";
import { isTerminalRuntimeEvent, runtimeEventSchema, type RuntimeEvent } from "./runtime-event";

/** SSE 连接当前状态。 */
export type RuntimeEventConnectionStatus =
  "idle" | "connecting" | "open" | "paused" | "reconnecting" | "closed" | "error";

/** 页面可见性最小抽象，便于测试隐藏/恢复行为。 */
export interface VisibilitySource {
  /** 当前页面是否隐藏。 */
  readonly hidden: boolean;
  /** 监听页面可见性变化。 */
  addEventListener(type: "visibilitychange", listener: () => void): void;
  /** 移除页面可见性监听。 */
  removeEventListener(type: "visibilitychange", listener: () => void): void;
}

/** Runtime SSE Client 的构造选项。 */
export interface RuntimeEventStreamOptions {
  /** Run UUIDv7。 */
  runId: string;
  /** 仅驻留内存的认证凭据来源。 */
  credentialProvider: CredentialProvider;
  /** 本地最多保留的脱敏事件数量。 */
  capacity?: number;
  /** 初始断点事件 ID。 */
  lastEventId?: string;
  /** 注入 Fetch 实现用于测试。 */
  fetcher?: typeof fetch;
  /** 注入页面可见性来源用于测试。 */
  visibility?: VisibilitySource;
  /** 接收到有效事件时触发。 */
  onEvent?: (event: RuntimeEvent) => void;
  /** 状态变化时触发。 */
  onStatus?: (status: RuntimeEventConnectionStatus) => void;
  /** 单条事件无效时触发；无效事件不会中断整个流。 */
  onInvalidEvent?: (reason: Error) => void;
  /** 重连基础退避，单位毫秒。 */
  retryBaseMs?: number;
  /** 重连最大退避，单位毫秒。 */
  retryMaxMs?: number;
}

interface ParsedSseMessage {
  /** SSE id 字段。 */
  id?: string;
  /** 合并多行后的 data 字段。 */
  data: string;
  /** 服务端建议的重连毫秒数。 */
  retry?: number;
}

/**
 * 将任意分块的 UTF-8 SSE 字节流解析为消息。
 *
 * @param stream Fetch Response 的字节流。
 */
async function* parseSse(stream: ReadableStream<Uint8Array>): AsyncGenerator<ParsedSseMessage> {
  const reader = stream.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  let id: string | undefined;
  let retry: number | undefined;
  let data: string[] = [];

  const dispatch = (): ParsedSseMessage | undefined => {
    if (data.length === 0) {
      id = undefined;
      retry = undefined;
      return undefined;
    }
    const message: ParsedSseMessage = { data: data.join("\n") };
    if (id !== undefined) {
      message.id = id;
    }
    if (retry !== undefined) {
      message.retry = retry;
    }
    id = undefined;
    retry = undefined;
    data = [];
    return message;
  };

  try {
    while (true) {
      const result = await reader.read();
      buffer += result.value ? decoder.decode(result.value, { stream: !result.done }) : "";
      const lines = buffer.split(/\r?\n/);
      buffer = lines.pop() ?? "";
      for (const line of lines) {
        if (line === "") {
          const message = dispatch();
          if (message) {
            yield message;
          }
          continue;
        }
        if (line.startsWith(":")) {
          continue;
        }
        const separator = line.indexOf(":");
        const field = separator < 0 ? line : line.slice(0, separator);
        const rawValue = separator < 0 ? "" : line.slice(separator + 1);
        const value = rawValue.startsWith(" ") ? rawValue.slice(1) : rawValue;
        if (field === "data") {
          data.push(value);
        } else if (field === "id" && !value.includes("\0")) {
          id = value;
        } else if (field === "retry" && /^\d+$/.test(value)) {
          retry = Number(value);
        }
      }
      if (result.done) {
        const message = dispatch();
        if (message) {
          yield message;
        }
        return;
      }
    }
  } finally {
    reader.releaseLock();
  }
}

/**
 * 提供 Last-Event-ID、去重、退避、页面隐藏恢复和有界内存的 Runtime SSE Client。
 */
export class RuntimeEventStreamClient {
  /** 构造选项。 */
  private readonly options: Required<
    Pick<RuntimeEventStreamOptions, "capacity" | "retryBaseMs" | "retryMaxMs">
  > &
    RuntimeEventStreamOptions;
  /** 当前有界事件缓冲。 */
  private readonly eventBuffer: RuntimeEvent[] = [];
  /** 与有界事件缓冲同步的事件 ID 集合。 */
  private readonly eventIds = new Set<string>();
  /** 当前请求中止器。 */
  private requestController?: AbortController;
  /** 当前是否应继续连接。 */
  private running = false;
  /** 当前连续失败次数。 */
  private attempt = 0;
  /** 当前断点事件 ID。 */
  private lastEventId: string | undefined;
  /** 服务端建议的重连等待时间。 */
  private serverRetryMs?: number;
  /** 当前公开状态。 */
  private currentStatus: RuntimeEventConnectionStatus = "idle";

  /**
   * 创建 Runtime SSE Client；构造过程不会建立网络连接。
   *
   * @param options Run、凭据、容量和回调配置。
   */
  constructor(options: RuntimeEventStreamOptions) {
    this.options = {
      capacity: 500,
      retryBaseMs: 500,
      retryMaxMs: 15_000,
      ...options,
    };
    if (this.options.capacity < 1) {
      throw new Error("Runtime Event Store 容量必须大于 0");
    }
    this.lastEventId = options.lastEventId;
  }

  /** 当前连接状态。 */
  get status(): RuntimeEventConnectionStatus {
    return this.currentStatus;
  }

  /** 当前断点事件 ID。 */
  get resumeEventId(): string | undefined {
    return this.lastEventId;
  }

  /** 返回事件缓冲的只读快照，不暴露内部可变数组。 */
  get events(): readonly RuntimeEvent[] {
    return [...this.eventBuffer];
  }

  /** 开始连接；重复调用保持幂等。 */
  start(): void {
    if (this.running) {
      return;
    }
    this.running = true;
    this.options.visibility?.addEventListener("visibilitychange", this.handleVisibilityChange);
    void this.run();
  }

  /** 主动关闭连接；关闭 SSE 不向服务端发送 Run Cancel。 */
  stop(): void {
    this.running = false;
    this.requestController?.abort();
    this.options.visibility?.removeEventListener("visibilitychange", this.handleVisibilityChange);
    this.updateStatus("closed");
  }

  /** 页面隐藏时暂停网络读取，恢复后依靠 Last-Event-ID 追平。 */
  private readonly handleVisibilityChange = (): void => {
    if (this.options.visibility?.hidden) {
      this.requestController?.abort();
      this.updateStatus("paused");
    }
  };

  /** 持续连接直到主动停止或收到 Run 终态。 */
  private async run(): Promise<void> {
    while (this.running) {
      if (this.options.visibility?.hidden) {
        this.updateStatus("paused");
        await new Promise((resolve) => setTimeout(resolve, 250));
        continue;
      }
      try {
        await this.connectOnce();
        if (!this.running) {
          return;
        }
        this.attempt += 1;
      } catch (error) {
        if (!this.running) {
          return;
        }
        if (this.options.visibility?.hidden) {
          continue;
        }
        this.attempt += 1;
        this.updateStatus("error");
        this.options.onInvalidEvent?.(
          error instanceof Error ? error : new Error("Runtime SSE 连接发生未知错误"),
        );
      }
      this.updateStatus("reconnecting");
      await this.waitForRetry();
    }
  }

  /** 建立一次 Fetch SSE 连接并消费到断开或终态。 */
  private async connectOnce(): Promise<void> {
    this.updateStatus(this.lastEventId ? "reconnecting" : "connecting");
    const headers = this.options.credentialProvider.getHeaders();
    headers.set("Accept", "text/event-stream");
    headers.set("Cache-Control", "no-cache");
    if (this.lastEventId) {
      headers.set("Last-Event-ID", this.lastEventId);
    }
    this.requestController = new AbortController();
    const fetcher = this.options.fetcher ?? fetch;
    const response = await fetcher(
      `/api/v1/runtime/runs/${encodeURIComponent(this.options.runId)}/events:stream`,
      {
        method: "GET",
        headers,
        credentials: "same-origin",
        signal: this.requestController.signal,
      },
    );
    if (response.status === 204) {
      this.running = false;
      this.updateStatus("closed");
      return;
    }
    if (!response.ok || !response.body) {
      throw new Error(`Runtime SSE 响应无效：HTTP ${String(response.status)}`);
    }
    this.attempt = 0;
    this.updateStatus("open");
    for await (const message of parseSse(response.body)) {
      if (message.retry !== undefined) {
        this.serverRetryMs = Math.min(message.retry, this.options.retryMaxMs);
      }
      let raw: unknown;
      try {
        raw = JSON.parse(message.data);
      } catch {
        this.options.onInvalidEvent?.(new Error("Runtime SSE data 不是合法 JSON"));
        continue;
      }
      const parsed = runtimeEventSchema.safeParse(raw);
      if (!parsed.success) {
        this.options.onInvalidEvent?.(
          new Error(`Runtime Event v1 校验失败：${parsed.error.message}`),
        );
        continue;
      }
      const event = parsed.data;
      const eventId = message.id || event.eventId;
      this.lastEventId = eventId;
      if (this.eventIds.has(event.eventId)) {
        continue;
      }
      this.appendEvent(event);
      this.options.onEvent?.(event);
      if (isTerminalRuntimeEvent(event)) {
        this.running = false;
        this.updateStatus("closed");
        this.requestController.abort();
        return;
      }
    }
  }

  /** 将事件加入有界缓冲，并同步淘汰去重集合。 */
  private appendEvent(event: RuntimeEvent): void {
    this.eventBuffer.push(event);
    this.eventIds.add(event.eventId);
    while (this.eventBuffer.length > this.options.capacity) {
      const removed = this.eventBuffer.shift();
      if (removed) {
        this.eventIds.delete(removed.eventId);
      }
    }
  }

  /** 使用服务端建议或指数退避加抖动等待下一次连接。 */
  private async waitForRetry(): Promise<void> {
    const exponential = Math.min(
      this.options.retryBaseMs * 2 ** Math.max(0, this.attempt - 1),
      this.options.retryMaxMs,
    );
    const base = this.serverRetryMs ?? exponential;
    const jittered = Math.max(0, Math.round(base * (0.8 + Math.random() * 0.4)));
    await new Promise((resolve) => setTimeout(resolve, jittered));
  }

  /** 发布连接状态，仅在实际变化时通知。 */
  private updateStatus(status: RuntimeEventConnectionStatus): void {
    if (this.currentStatus !== status) {
      this.currentStatus = status;
      this.options.onStatus?.(status);
    }
  }
}
