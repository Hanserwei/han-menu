<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import {
  Button,
  Form,
  FormItem,
  Input,
  TextArea,
  Select,
  Switch,
  InputNumber,
  Alert,
  Skeleton,
  Upload,
  Tag,
} from 'antdv-next'
import { catalogApi, type Kind, type Product } from '../api/catalog'
import { productDraft, detailsFor } from '../model/product-draft'
import ProductImage from '../ui/ProductImage.vue'
import DishPicker from '../ui/DishPicker.vue'
import { queryClient } from '@/shared/api/query-client'
import { revision } from '@/shared/api/result'
import { sessionBridge } from '@/shared/api/session-bridge'
import { ApiProblem } from '@/shared/api/problem'
import { useCommand } from '@/shared/model/use-command'
import { useEditorGuard } from '@/shared/model/use-editor-guard'
import ProblemAlert from '@/shared/ui/ProblemAlert.vue'
import WriteFeedback from '@/shared/ui/WriteFeedback.vue'
const props = defineProps<{ kind: Kind }>(),
  route = useRoute(),
  router = useRouter()
const current = ref<Product>(),
  loading = ref(false),
  loadError = ref<unknown>(null),
  baseline = ref(JSON.stringify(productDraft())),
  picker = ref(false),
  uploading = ref(false),
  uploadError = ref<unknown>(null)
const draft = reactive(productDraft()),
  dishes = reactive<Record<string, Product>>({})
const command = useCommand(),
  { pending, error, needsReload } = command
const busy = computed(() => pending.value || uploading.value),
  dirty = computed(() => JSON.stringify(draft) !== baseline.value)
const { confirmDiscard } = useEditorGuard(dirty, busy)
const path = computed(() => (props.kind === 'DISH' ? '/catalog/dishes' : '/catalog/meals')),
  title = computed(() => (props.kind === 'DISH' ? '菜品' : '套餐'))
const categories = useQuery({
  queryKey: ['catalog-categories'],
  queryFn: ({ signal }) => catalogApi.categories(signal),
})
const options = computed(() =>
  (categories.data.value || [])
    .filter((c) => c.kind === props.kind)
    .map((c) => ({ label: `${c.name}${c.enabled ? '' : '（停用）'}`, value: c.id })),
)
let generation = 0
/** 编辑时一次读取聚合及最多50个组成快照；草稿不绑定持续刷新的查询结果。 */
async function load() {
  const epoch = ++generation
  loading.value = true
  loadError.value = null
  error.value = null
  try {
    const id = String(route.params.id || '')
    const value = id ? await catalogApi.product(id) : undefined
    if (epoch !== generation) return
    if (value && value.kind !== props.kind) throw new ApiProblem(404, '商品种类与页面不匹配')
    current.value = value
    Object.assign(draft, productDraft(value))
    baseline.value = JSON.stringify(draft)
    const snapshots = await Promise.all(draft.components.map((c) => catalogApi.product(c.dishId)))
    if (epoch === generation) {
      Object.keys(dishes).forEach((key) => delete dishes[key])
      for (const dish of snapshots) if (dish.id) dishes[dish.id] = dish
    }
  } catch (failure) {
    if (epoch === generation) loadError.value = failure
  } finally {
    if (epoch === generation) loading.value = false
  }
}
async function reload() {
  if (await confirmDiscard()) await load()
}
async function cancel() {
  if (await confirmDiscard()) {
    baseline.value = JSON.stringify(draft)
    await router.push(path.value)
  }
}
async function save() {
  if (busy.value || loading.value || loadError.value || current.value?.status === 'ON_SALE') return
  let details
  try {
    details = detailsFor(props.kind, draft, dishes)
  } catch (failure) {
    error.value = new ApiProblem(400, failure instanceof Error ? failure.message : '请检查商品资料')
    return
  }
  await command.run(async () => {
    if (current.value) {
      const { id, version } = revision(current.value)
      await catalogApi.update(id, details, version)
    } else await catalogApi.create(props.kind, details)
    baseline.value = JSON.stringify(draft)
    await queryClient.invalidateQueries({ queryKey: ['catalog-products'] })
    await queryClient.invalidateQueries({ queryKey: ['catalog-picker'] })
  }, '商品已保存，仍为下架状态')
  // 保存完成后再导航，避免待请求守卫把成功页面留在原地。
  if (!error.value && !pending.value && !dirty.value) await router.push(path.value)
}
/** 本地验证格式与尺寸是提前反馈，真实文件格式仍由服务器解码判定。 */
async function upload(file: File) {
  if (busy.value) return false
  uploadError.value = null
  uploading.value = true
  const epoch = generation,
    session = sessionBridge.snapshot().generation
  try {
    if (!['image/png', 'image/jpeg'].includes(file.type) || file.size > 5 * 1024 * 1024)
      throw new ApiProblem(400, '请选择不超过5MiB的PNG或JPEG图片')
    const bitmap = await createImageBitmap(file)
    const valid =
      bitmap.width <= 4096 && bitmap.height <= 4096 && bitmap.width * bitmap.height <= 16_000_000
    bitmap.close()
    if (!valid) throw new ApiProblem(400, '图片边长最多4096像素，总像素最多1600万')
    const result = await catalogApi.upload(file)
    if (!result.id) throw new ApiProblem(502, '上传响应缺少图片标识')
    if (epoch === generation && sessionBridge.isCurrent(session)) draft.imageId = result.id
  } catch (failure) {
    if (epoch === generation)
      uploadError.value =
        failure instanceof ApiProblem
          ? failure
          : new ApiProblem(400, '图片无法读取，请选择有效PNG或JPEG文件')
  } finally {
    if (epoch === generation) uploading.value = false
  }
  return false
}
function choose(dish: Product) {
  if (!dish.id || draft.components.some((c) => c.dishId === dish.id)) return
  dishes[dish.id] = dish
  draft.components.push({ dishId: dish.id, quantity: 1, selections: {} })
  picker.value = false
}
function selectFlavor(index: number, name: string, value?: string) {
  const item = draft.components[index]
  if (item) {
    if (value) item.selections[name] = value
    else delete item.selections[name]
  }
}
const rules = {
  name: [{ required: true, whitespace: true, max: 100, message: '请输入商品名称' }],
  categoryId: [{ required: true, message: '请选择分类' }],
  price: [{ required: true, message: '请输入售价' }],
}
onMounted(load)
onBeforeUnmount(() => generation++)
</script>
<template>
  <Button type="link" class="back-link" :disabled="busy" @click="cancel"
    >返回{{ title }}列表</Button
  >
  <div class="page-heading">
    <h1>
      {{ current ? '编辑' : '新增' }}{{ title }}
      <Tag>{{ current?.status === 'ON_SALE' ? '在售，只读' : '已下架，可编辑' }}</Tag>
    </h1>
    <p class="muted">保存后仍为下架状态，确认资料后再上架。</p>
  </div>
  <ProblemAlert
    :error="loadError || categories.error.value"
    retry
    @retry="
      () => {
        reload()
        categories.refetch()
      }
    "
  /><WriteFeedback :error="error" :needs-reload="needsReload" :pending="busy" @reload="reload" />
  <Skeleton v-if="loading" active :paragraph="{ rows: 8 }" />
  <Alert
    v-else-if="current?.status === 'ON_SALE'"
    type="info"
    title="请先返回列表下架商品，再编辑资料。"
  />
  <Form
    v-else-if="!loadError"
    :model="draft"
    :rules="rules"
    layout="vertical"
    :disabled="busy"
    class="product-editor"
    @finish="save"
  >
    <div class="editor-columns">
      <section>
        <h2>基本信息</h2>
        <FormItem label="商品名称" name="name"
          ><Input v-model:value="draft.name" :maxlength="100"
        /></FormItem>
        <div class="two-fields">
          <FormItem label="所属分类" name="categoryId"
            ><Select
              v-model:value="draft.categoryId"
              :options="options"
              placeholder="请选择分类" /></FormItem
          ><FormItem label="售价（元）" name="price"
            ><Input
              v-model:value="draft.price"
              inputmode="decimal"
              placeholder="0.00"
              :maxlength="9"
          /></FormItem>
        </div>
        <p class="muted">商品类型：{{ title }}（创建后不可更改）</p>
        <FormItem label="商品描述"
          ><TextArea v-model:value="draft.description" :rows="4" :maxlength="1000" show-count
        /></FormItem>
      </section>
      <section class="editor-image">
        <h2>商品图片</h2>
        <ProductImage :id="draft.imageId" large /><ProblemAlert :error="uploadError" />
        <div class="row-actions top-space">
          <Upload
            accept="image/png,image/jpeg"
            :show-upload-list="false"
            :before-upload="upload"
            :disabled="busy"
            ><Button :loading="uploading">{{
              draft.imageId ? '更换图片' : '上传图片'
            }}</Button></Upload
          ><Button v-if="draft.imageId" :disabled="busy" @click="draft.imageId = undefined"
            >移除关联</Button
          >
        </div>
        <p class="muted top-space">PNG / JPEG，最大5MiB<br />边长最多4096像素</p>
      </section>
    </div>
    <section v-if="kind === 'DISH'" class="spec-section">
      <h2>口味设置</h2>
      <p class="muted">每组为单选；最多10组，每组1—20个选项。</p>
      <div v-for="(flavor, index) in draft.flavors" :key="index" class="flavor-editor">
        <div class="row-actions">
          <Input
            v-model:value="flavor.name"
            :aria-label="`口味组${index + 1}名称`"
            placeholder="口味组名称"
            :maxlength="30"
          /><span>必选</span
          ><Switch
            v-model:checked="flavor.required"
            :aria-label="`口味组${index + 1}必选`"
          /><Button danger @click="draft.flavors.splice(index, 1)">移除组</Button>
        </div>
        <div class="flavor-options">
          <div v-for="(_option, o) in flavor.options" :key="o" class="row-actions">
            <Input
              v-model:value="flavor.options[o]"
              :aria-label="`口味组${index + 1}选项${o + 1}`"
              :maxlength="30"
            /><Button :disabled="flavor.options.length <= 1" @click="flavor.options.splice(o, 1)"
              >移除选项</Button
            >
          </div>
        </div>
        <Button :disabled="flavor.options.length >= 20" @click="flavor.options.push('')"
          >添加选项</Button
        >
      </div>
      <Button
        :disabled="draft.flavors.length >= 10"
        @click="draft.flavors.push({ name: '', options: [''], required: true })"
        >添加口味组</Button
      >
    </section>
    <section v-else class="spec-section">
      <h2>组成菜品</h2>
      <p class="muted">套餐包含1—50个不重复菜品，口味在此固定。</p>
      <div v-for="(part, index) in draft.components" :key="part.dishId" class="flavor-editor">
        <div class="row-actions">
          <strong>{{ dishes[part.dishId]?.name || part.dishId }}</strong
          ><FormItem :label="`数量${index + 1}`"
            ><InputNumber
              v-model:value="part.quantity"
              :min="1"
              :max="99"
              :precision="0" /></FormItem
          ><Button danger @click="draft.components.splice(index, 1)">移除菜品</Button>
        </div>
        <div class="two-fields">
          <FormItem
            v-for="flavor in dishes[part.dishId]?.flavors || []"
            :key="flavor.name"
            :label="`${flavor.name}${flavor.required ? '（必选）' : ''}`"
            ><Select
              :value="part.selections[flavor.name!]"
              :aria-label="`${dishes[part.dishId]?.name}-${flavor.name}`"
              :options="flavor.options?.map((o) => ({ label: o, value: o }))"
              allow-clear
              @change="(value) => selectFlavor(index, flavor.name!, value as string | undefined)"
          /></FormItem>
        </div>
      </div>
      <Button :disabled="draft.components.length >= 50" @click="picker = true">添加组成菜品</Button>
    </section>
    <footer class="editor-footer">
      <span class="muted">{{ dirty ? '修改尚未保存' : '资料已读取' }}</span>
      <div class="row-actions">
        <Button :disabled="busy" @click="cancel">取消</Button
        ><Button
          type="primary"
          html-type="submit"
          :loading="pending"
          :disabled="uploading || needsReload || categories.isError.value"
          >保存{{ title }}</Button
        >
      </div>
    </footer>
  </Form>
  <DishPicker
    :open="picker"
    :selected="draft.components.map((c) => c.dishId)"
    @close="picker = false"
    @choose="choose"
  />
</template>
