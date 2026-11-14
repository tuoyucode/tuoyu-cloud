import type { PageQuery, PageResult } from '../types';

import { exportExcel, requestClient } from '../request';

export namespace BizItemApi {
  /** 【请填写功能名称】 */
  export interface BizItem {
    /** 物品ID */
    itemId?: number | string;
    /** item_name */
    itemName?: string;
    /** 封面 */
    coverImage?: number;
    /** 单价 */
    price?: number;
    /** 重量 */
    weight?: number;
    /** 类型 */
    itemType?: string;
    /** 启用 */
    enabled?: boolean;
    /** 开始日期 */
    startDate?: string;
    /** 删除标志 */
    delFlag?: string;
    /** 版本 */
    version?: number;
    /** 编码 */
    itemCodeX?: string;
  }

  /** 查询条件 */
  export interface Query extends PageQuery {
    itemName?: string;
    price?: number;
    weight?: number;
    itemType?: string;
    enabled?: boolean;
    itemCodeX?: string;
    /** 范围查询条件，例如 params[beginCreateTime] */
    params?: Record<string, unknown>;
  }

  /** 新增、修改的参数 */
  export type Form = Partial<BizItem>;
}

const BASE = '/system/item';

export function listBizItem(query: BizItemApi.Query) {
  return requestClient.get<PageResult<BizItemApi.BizItem>>(`${BASE}/list`, {
    params: query,
  });
}

export function getBizItem(itemId: number | string) {
  return requestClient.get<BizItemApi.BizItem>(`${BASE}/${itemId}`);
}

export function addBizItem(data: BizItemApi.Form) {
  return requestClient.post<null>(BASE, data);
}

export function updateBizItem(data: BizItemApi.Form) {
  return requestClient.put<null>(BASE, data);
}

export function removeBizItem(itemIds: (number | string)[]) {
  return requestClient.delete<null>(`${BASE}/${itemIds.join(',')}`);
}

export function exportBizItem(query: BizItemApi.Query, fileName: string) {
  return exportExcel(`${BASE}/export`, { ...query }, fileName);
}
