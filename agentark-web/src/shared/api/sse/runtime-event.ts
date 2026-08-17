import { z } from "zod";

const uuidV7 = z
  .string()
  .regex(/^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);

const objectRef = z.object({
  uri: z.string().url(),
  checksum: z.string().regex(/^sha256:[0-9a-f]{64}$/),
  size: z.number().int().nonnegative(),
  mediaType: z.string().min(1).max(255),
});

/** Runtime Event v1 的运行时校验器，与 JSON Schema 的稳定信封字段一致。 */
export const runtimeEventSchema = z
  .object({
    schemaVersion: z.literal(1),
    eventId: uuidV7,
    sessionSequence: z.number().int().positive(),
    sequence: z.number().int().positive(),
    eventType: z
      .string()
      .min(3)
      .max(128)
      .regex(/^[a-z][a-z0-9]*(?:[.][a-z][a-z0-9]*)+$/),
    occurredAt: z.iso.datetime({ offset: true }),
    organizationId: uuidV7,
    projectId: uuidV7,
    sessionId: uuidV7,
    turnId: uuidV7,
    runId: uuidV7,
    traceId: z.string().regex(/^[0-9a-f]{32}$/),
    fencingToken: z.number().int().nonnegative(),
    payload: z.record(z.string(), z.unknown()).optional(),
    payloadRef: objectRef.optional(),
  })
  .strict()
  .refine((event) => Number(Boolean(event.payload)) + Number(Boolean(event.payloadRef)) === 1, {
    message: "payload 与 payloadRef 必须且只能存在一个",
  });

/** 通过 Runtime Event v1 Schema 校验后的稳定事件。 */
export type RuntimeEvent = z.infer<typeof runtimeEventSchema>;

/** Runtime Run 的明确终态事件。 */
const terminalEventTypes = new Set([
  "run.succeeded",
  "run.failed",
  "run.cancelled",
  "run.timed_out",
  "run.abandoned",
]);

/**
 * 判断事件是否表示 Run 已进入不可继续的终态。
 *
 * @param event 已校验的 Runtime Event。
 */
export function isTerminalRuntimeEvent(event: RuntimeEvent): boolean {
  return terminalEventTypes.has(event.eventType);
}
