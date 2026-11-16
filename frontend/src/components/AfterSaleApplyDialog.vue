<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  applyAfterSale, reapplyAfterSale, uploadEvidence, REASON_LABELS, type AfterSaleType,
} from '@/api/afterSales'
import { mediaUrl } from '@/api/media'

const props = defineProps<{
  modelValue: boolean
  mode: 'apply' | 'reapply'
  orderNo?: string
  subOrderNo?: string
  asNo?: string
  customOnly?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  submitted: []
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

const form = ref({ type: 'REFUND_ONLY' as AfterSaleType, reasonCode: 'QUALITY', description: '' })
const evidence = ref<string[]>([])
const uploading = ref(false)
const saving = ref(false)

const reasons = computed(() => Object.entries(REASON_LABELS)
  .filter(([code]) => !props.customOnly || code === 'QUALITY' || code === 'NOT_AS_DESCRIBED'))

watch(() => props.modelValue, (open) => {
  if (open) {
    form.value = { type: 'REFUND_ONLY', reasonCode: 'QUALITY', description: '' }
    evidence.value = []
  }
})

async function onFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (evidence.value.length >= 3) {
    ElMessage.warning('最多上传 3 张凭证')
    return
  }
  uploading.value = true
  try {
    const key = await uploadEvidence(file)
    evidence.value.push(key)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '上传失败')
  } finally {
    uploading.value = false
    input.value = ''
  }
}

function removeEvidence(index: number) {
  evidence.value.splice(index, 1)
}

async function submit() {
  if (!form.value.reasonCode) {
    ElMessage.warning('请选择原因')
    return
  }
  saving.value = true
  try {
    const payload = {
      type: form.value.type,
      reasonCode: form.value.reasonCode,
      description: form.value.description || undefined,
      evidence: evidence.value,
    }
    if (props.mode === 'apply') {
      if (!props.orderNo || !props.subOrderNo) return
      await applyAfterSale(props.orderNo, props.subOrderNo, payload)
      ElMessage.success('售后申请已提交，等待商家处理')
    } else {
      if (!props.asNo) return
      await reapplyAfterSale(props.asNo, payload)
      ElMessage.success('已补充证据并重新提交')
    }
    visible.value = false
    emit('submitted')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '提交失败')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog v-model="visible" :title="mode === 'apply' ? '申请售后' : '补充证据重新申请'" width="520px">
    <div class="form">
      <label>售后类型
        <select v-model="form.type">
          <option value="REFUND_ONLY">仅退款</option>
          <option value="RETURN_REFUND">退货退款</option>
        </select>
      </label>
      <label>原因
        <select v-model="form.reasonCode">
          <option v-for="[code, label] in reasons" :key="code" :value="code">{{ label }}</option>
        </select>
      </label>
      <label>问题描述
        <textarea v-model="form.description" rows="3" maxlength="500"
          placeholder="说明问题、期望的处理方式（选填，最多 500 字）" />
      </label>
      <div>
        <p class="label">凭证（最多 3 张）</p>
        <div class="evidence">
          <div v-for="(key, index) in evidence" :key="key" class="shot">
            <img :src="mediaUrl(key)" alt="凭证" />
            <button type="button" @click="removeEvidence(index)">移除</button>
          </div>
          <label v-if="evidence.length < 3" class="picker">
            <input type="file" accept="image/jpeg,image/png,image/webp" @change="onFile" />
            {{ uploading ? '上传中…' : '＋ 上传' }}
          </label>
        </div>
      </div>
      <p v-if="customOnly" class="hint">定制商品仅支持「质量问题」与「与描述不符」两类原因。</p>
    </div>
    <template #footer>
      <button class="btn btn--ghost" @click="visible = false">取消</button>
      <button class="btn btn--solid" :disabled="saving || uploading" @click="submit">
        {{ saving ? '提交中…' : '提交' }}
      </button>
    </template>
  </el-dialog>
</template>

<style scoped>
.form { display: grid; gap: 14px; }
label { display: grid; gap: 6px; font-size: 13px; color: var(--muted); }
select, textarea {
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: 10px;
  font-size: 14px;
  font-family: inherit;
  resize: vertical;
}
.label { font-size: 13px; color: var(--muted); margin: 0 0 6px; }
.evidence { display: flex; gap: 10px; flex-wrap: wrap; }
.shot { position: relative; width: 84px; height: 84px; border-radius: 10px; overflow: hidden; }
.shot img { width: 100%; height: 100%; object-fit: cover; }
.shot button {
  position: absolute; inset: auto 0 0 0;
  border: 0; background: rgba(0, 0, 0, .55); color: #fff; font-size: 11px; padding: 3px 0; cursor: pointer;
}
.picker {
  display: grid; place-items: center;
  width: 84px; height: 84px;
  border: 1px dashed var(--line); border-radius: 10px;
  cursor: pointer; font-size: 12px; color: var(--muted);
}
.picker input { display: none; }
.hint { font-size: 12px; color: var(--muted); margin: 0; }
</style>
