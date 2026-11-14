<script lang="ts" setup>
import type { PostDemoApi } from '@tuoyu/shared/api/system/postDemo';

import { computed, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import {
  addPostDemo,
  getPostDemo,
  updatePostDemo,
} from '@tuoyu/shared/api/system/postDemo';
import { message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import { $t } from '#/locales';

import { loadFormOptions, multipleFields, useFormSchema } from './data';

const emit = defineEmits<{ success: [] }>();

/** 编辑时的主键；新增时为空 */
const postId = ref<number | string>();

const [Form, formApi] = useVbenForm({
  layout: 'vertical',
  schema: useFormSchema(),
  showDefaultActions: false,
  wrapperClass: 'grid-cols-1 md:grid-cols-2 gap-x-4',
});

/** 多选字段：表单中为数组，后端以逗号分隔的字符串保存 */
function toForm(record: PostDemoApi.PostDemo) {
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
  return data as PostDemoApi.Form;
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
      await (postId.value === undefined
        ? addPostDemo(data)
        : updatePostDemo({ ...data, postId: postId.value }));
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
      | { postId?: number | string; values?: PostDemoApi.Form };
    postId.value = data?.postId;
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
      if (postId.value === undefined) {
        // 新增时的预填值，例如树表“新增下级”的上级
        if (data?.values) {
          await formApi.setValues(data.values);
        }
      } else {
        await formApi.setValues(toForm(await getPostDemo(postId.value)));
      }
    } finally {
      drawerApi.unlock();
    }
  },
});

const title = computed(() =>
  postId.value === undefined ? $t('system.postDemo.create') : $t('system.postDemo.edit'),
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
