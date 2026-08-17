import { Link } from "react-router-dom";

import { Button, EmptyState } from "@/shared/ui";

/** 未匹配路由的稳定空状态页面。 */
export default function NotFoundPage() {
  return (
    <div className="not-found-page">
      <EmptyState
        title="页面不存在"
        description="该路由尚未实现，或已被移动到新的产品区域。"
        action={
          <Button asChild>
            <Link to="/">返回总览</Link>
          </Button>
        }
      />
    </div>
  );
}
