import type { ReactNode } from "react";

/** 表格列描述。 */
export interface TableColumn<Row> {
  /** 稳定列键。 */
  key: string;
  /** 表头文本。 */
  header: string;
  /** 将行渲染为当前单元格内容。 */
  render: (row: Row) => ReactNode;
}

/** 通用数据表属性。 */
export interface DataTableProps<Row> {
  /** 表格说明，提供给屏幕阅读器。 */
  caption: string;
  /** 列描述集合。 */
  columns: TableColumn<Row>[];
  /** 数据行集合。 */
  rows: Row[];
  /** 返回每行稳定键。 */
  getRowKey: (row: Row) => string;
  /** 无数据时显示的文本。 */
  emptyText?: string;
}

/**
 * 渲染语义化表格和可读空状态，不内置业务分页或排序。
 *
 * @param props 表格说明、列、数据和行键函数。
 */
export function DataTable<Row>({
  caption,
  columns,
  rows,
  getRowKey,
  emptyText = "暂无数据",
}: DataTableProps<Row>) {
  return (
    <div className="table-scroll" tabIndex={0} aria-label={`${caption}，可横向滚动`}>
      <table className="data-table">
        <caption className="sr-only">{caption}</caption>
        <thead>
          <tr>
            {columns.map((column) => (
              <th key={column.key} scope="col">
                {column.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.length === 0 ? (
            <tr>
              <td colSpan={columns.length} className="table-empty">
                {emptyText}
              </td>
            </tr>
          ) : (
            rows.map((row) => (
              <tr key={getRowKey(row)}>
                {columns.map((column) => (
                  <td key={column.key}>{column.render(row)}</td>
                ))}
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  );
}
