import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

/**
 * 合并条件类名并消除 Tailwind 工具类冲突。
 *
 * @param inputs 条件类名、数组或对象。
 */
export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs));
}
