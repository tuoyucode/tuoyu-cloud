<script lang="ts" setup>
import type { BizItemApi } from '@tuoyu/shared/api/system/item';

import { computed, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import {
  addBizItem,
  getBizItem,
  updateBizItem,
} from '@tuoyu/shared/api/system/item';
import { message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import { $t } from '#/locales';

import { loadFormOptions, multipleFields, useFormSchema } from './data';

const emit = defineEmits<{ success: [] }>();

/** 编辑时的主键；新增时为空 */
const itemId = ref<number | string>();

const [Form, formApi] = useVbenForm({
  layout: 'vertical',
  schema: useFormSchema(),
  showDefaultActions: false,
  wrapperClass: 'grid-cols-1 md:grid-cols-2 gap-x-4',
});

/** 多选字段：表单中为数组，后端以逗号分隔的字符串保存 */
function toForm(record: BizItemApi.BizItem) {
  const values: Record<string, unknown> = { ...record };
  for (const field of multipleFields) {
    const value = values[field];
    values[field] = typeof value === 'string' && value ? value.split(',') : [];
  }
  return values;
}

function fromForm(values: Record<string, unknown>) {
  const data: Record<string, unknown> = { ...values };
  for (const field of multipleFields) {
    const value = data[field];
    data[field] = Array.isArray(value) ? value.join(',') : value;
  }
  return data as BizItemApi.Form;
}

const [Drawer, drawerApi] = useVbenDrawer({
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) {
      return;
    }
    drawerApi.lock();
    try {
      const data = fromForm(await formApi.getValues());
      await (itemId.value === undefined
        ? addBizItem(data)
        : updateBizItem({ ...data, itemId: itemId.value }));
      message.success($t('crud.saved'));
      emit('success');
      drawerApi.close();
    } finally {
      drawerApi.unlock();
    }
  },
  async onOpenChange(isOpen) {
    if (!isOpen) {
      return;
    }
    const data = drawerApi.getData() as
      | undefined
      | { itemId?: number | string; values?: BizItemApi.Form };
    itemId.value = data?.itemId;
    await formApi.resetForm();
    drawerApi.lock();
    try {
      const options = await loadFormOptions();
      formApi.updateSchema(
        Object.entries(options).map(([fieldName, fieldOptions]) => ({
          componentProps: { options: fieldOptions },
          fieldName,
        })),
      );
      if (itemId.value === undefined) {
        // 新增时的预填值，例如树表“新增下级”的上级
        if (data?.values) {
          await formApi.setValues(data.values);
        }
      } else {
        await formApi.setValues(toForm(await getBizItem(itemId.value)));
      }
    } finally {
      drawerApi.unlock();
    }
  },
});

const title = computed(() =>
  itemId.value === undefined ? $t('system.item.create') : $t('system.item.edit'),
);
</script>

<template>
  <Drawer
    :confirm-text="$t('crud.save')"
    :title="title"
    class="w-full max-w-[560px]"
  >
    <Form />
  </Drawer>
</template>
