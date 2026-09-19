<template>
  <div class="pane">
    <header class="hd"><h2>工位</h2><span class="sub">左边大牌是当前占用中的工位，右边一路排下去；占用数把在洗的和回炉中的都算进去</span>
      <button class="prime" @click="openNew">新增工位</button></header>
    <div class="layout">
      <section class="board">
        <div class="b-title">正在洗</div>
        <div class="b-code">{{ busy[0] ? busy[0].bayCode : '—' }}</div>
        <div class="b-name">{{ busy[0] ? busy[0].bayName : '暂时没有在洗的工位' }}</div>
        <div class="b-count">共 {{ busy.length }} 个工位在占用 · 在洗 {{ totalWash }} 车 · 回炉 {{ totalRework }} 车</div>
      </section>
      <section class="queue">
        <div class="q-row head"><span>编号</span><span>名称</span><span>同时容纳</span><span>占用</span><span>状态</span><span>操作</span></div>
        <div v-for="b in sorted" :key="b.id" class="q-row">
          <span class="mono">{{ b.bayCode }}</span>
          <span>{{ b.bayName }}</span>
          <span class="r">{{ b.seatCount ?? '-' }}</span>
          <span class="occ">
            {{ loadOf(b) }}/{{ b.seatCount ?? '不限' }}
            <em v-if="(b.reworkCount || 0) > 0" class="rw">含回炉 {{ b.reworkCount }}</em>
          </span>
          <span class="st">{{ b.bayState }}</span>
          <span><button class="ghost" @click="openEdit(b)">改</button></span>
        </div>
      </section>
    </div>
    <el-dialog v-model="dialog" :title="form.id ? '修改工位' : '新增工位'" width="420px">
      <div class="fr"><label>编号</label><el-input v-model="form.bayCode" /></div>
      <div class="fr"><label>名称</label><el-input v-model="form.bayName" /></div>
      <div class="fr"><label>同时容纳</label><el-input v-model="form.seatCount" /></div>
      <div class="fr"><label>状态</label><el-input v-model="form.bayState" placeholder="空闲 / 占用 / 停用" /></div>
      <template #footer><el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script>
import { bayApi } from '../api'

export default {
  name: 'Bays',
  data() {
    return { items: [], dialog: false, form: {} }
  },
  computed: {
    busy() {
      return this.items.filter((b) => this.loadOf(b) > 0 || b.bayState === '占用')
    },
    totalWash() {
      return this.items.reduce((s, b) => s + (b.washCount || 0), 0)
    },
    totalRework() {
      return this.items.reduce((s, b) => s + (b.reworkCount || 0), 0)
    },
    sorted() {
      const order = { 占用: 0, 空闲: 1, 停用: 2 }
      return [...this.items].sort((a, b) => (order[a.bayState] ?? 9) - (order[b.bayState] ?? 9))
    }
  },
  methods: {
    loadOf(b) {
      return (b.washCount || 0) + (b.reworkCount || 0)
    },
    async load() {
      this.items = await bayApi.list()
    },
    openNew() {
      this.form = { bayState: '空闲' }
      this.dialog = true
    },
    openEdit(row) {
      this.form = { ...row }
      this.dialog = true
    },
    async submit() {
      try {
        if (this.form.id) await bayApi.save(this.form.id, this.form)
        else await bayApi.add(this.form)
        this.dialog = false
        await this.load()
        this.$message.success('保存好了')
      } catch (e) { this.$message.error(e.message) }
    }
  },
  mounted() { this.load() }
}
</script>

<style scoped>
.hd { display: flex; align-items: center; gap: 14px; margin-bottom: 18px; }
.hd h2 { margin: 0; font-size: 20px; }
.sub { flex: 1; color: #99a1a6; font-size: 12px; }
.prime { background: var(--el-color-primary); color: #fff; border: none; border-radius: 8px;
  padding: 8px 18px; font-size: 13px; cursor: pointer; }
.layout { display: grid; grid-template-columns: 280px 1fr; gap: 16px; }
.board { background: linear-gradient(150deg, var(--el-color-primary), #263238); color: #fff;
  border-radius: 16px; padding: 24px; text-align: center; height: fit-content; }
.b-title { font-size: 12px; opacity: .8; }
.b-code { font-size: 46px; font-weight: 800; letter-spacing: 2px; margin: 10px 0 4px; }
.b-name { font-size: 13px; opacity: .9; margin-bottom: 16px; }
.b-count { font-size: 12px; opacity: .7; }
.queue { background: #fff; border: 1px solid #e9edef; border-radius: 12px; overflow: hidden; }
.q-row { display: grid; grid-template-columns: 90px 1fr 92px 130px 78px 66px; gap: 8px; align-items: center;
  padding: 11px 14px; border-bottom: 1px solid #f3f6f7; font-size: 13px; }
.q-row.head { background: #f7f9fa; color: #99a1a6; font-size: 12px; }
.mono { font-family: ui-monospace, Menlo, monospace; color: #99a1a6; }
.r { text-align: right; }
.occ { font-size: 12px; color: #647077; }
.rw { display: inline-block; font-style: normal; margin-left: 6px; padding: 1px 8px; border-radius: 10px;
  background: #fdecec; color: #c45656; font-size: 11px; }
.st { color: var(--el-color-primary-dark-2); }
.ghost { background: #fff; border: 1px solid var(--el-color-primary-light-7); color: var(--el-color-primary-dark-2);
  border-radius: 7px; padding: 4px 12px; font-size: 12px; cursor: pointer; }
.fr { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.fr label { width: 72px; text-align: right; font-size: 13px; color: #647077; }
</style>
