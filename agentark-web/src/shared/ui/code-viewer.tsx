/** 代码查看器属性。 */
export interface CodeViewerProps {
  /** 用户可见的代码或 JSON 文本。 */
  value: string;
  /** 内容语言标签。 */
  language?: string;
  /** 代码区域可访问名称。 */
  ariaLabel: string;
}

/**
 * 渲染只读、可横向滚动的代码区域；不会执行内容。
 *
 * @param props 文本、语言和标签。
 */
export function CodeViewer({ value, language = "text", ariaLabel }: CodeViewerProps) {
  return (
    <pre className="code-viewer" tabIndex={0} aria-label={ariaLabel} data-language={language}>
      <code>{value}</code>
    </pre>
  );
}

/** JSON 查看器属性。 */
export interface JsonViewerProps {
  /** 待格式化的未知值。 */
  value: unknown;
  /** JSON 区域可访问名称。 */
  ariaLabel: string;
}

/**
 * 将未知值稳定格式化为只读 JSON；不包含折叠或执行逻辑。
 *
 * @param props 待展示值和标签。
 */
export function JsonViewer({ value, ariaLabel }: JsonViewerProps) {
  return (
    <CodeViewer value={JSON.stringify(value, null, 2)} language="json" ariaLabel={ariaLabel} />
  );
}
