<template>
  <div class="pane">
    <header class="hd"><h2>回炉台</h2><span class="sub">回炉只挂在已完成的单上；「推进」按 待回炉 → 回炉中 → 已验收 走，回炉中算工位占用</span></header>
    <div class="tabs">
      <div class="tab" :class="{ on: tab === 'all' }" @click="tab = 'all'">全部 {{ items.length }}</div>
      <div v-for="s in STATES" :key="s" class="tab" :class="{ on: tab === s }" @click="tab = s">
        {{ s }} {{ countOf(s) }}
      </div>
    </div>
    <div class="table">
      <div class="row head"><span>回炉单号</span><span>原单号</span><span>车牌</span><span>工位</span>
        <span>原因</span><span>状态</span><span>操作</span></div>
      <div v-for="r in shown" :key="r.id" class="row">
        <span class="mono">{{ r.reworkNo }}</span>
        <span class="mono">{{ r.orderNo || orderNoOf(r.orderId) }}</span>
        <span>{{ r.plateNo || plateOf(r.orderId) }}</span>
        <span>{{ r.bayCode || bayCodeOf(r.bayId) }}</span>
        <span class="dim">{{ r.reason || '—' }}</span>
        <span class="st">{{ r.reworkState }}</span>
        <span>
          <button v-if="nextOf(r)" class="ghost" @click="advance(r)">推进</button>
          <button v-if="r.reworkState !== '已验收'" class="ghost" @click="openReassign(r)">改派</button>
        </span>
      </div>
      <div class="blank" v-if="!shown.length">这一类下暂时没有</div>
    </div>
    <el-dialog v-model="reassignDialog" title="改派到别的空闲工位" width="420px">
      <div class="fr"><label>回炉单</label><span class="mono">{{ target.reworkNo }}</span></div>
      <div class="fr"><label>新工位</label>
        <el-select v-model="reassignBayId" style="flex:1" placeholder="选一个空闲工位">
          <el-option v-for="b in freeBays" :key="b.id" :label="b.bayName + '（占用 ' + loadOf(b) + '/' + (b.seatCount ?? '不限') + '）'" :value="b.id" />
        </el-select>
      </div>
      <div class="warn" v-if="!freeBays.length">现在没有能改派的空闲工位</div>
      <template #footer><el-button @click="reassignDialog = false">取消</el-button>
        <el-button type="primary" :disabled="!reassignBayId" @click="submitReassign">改派</el-button></template>
    </el-dialog>
  </div>
</template>

<script>
import { bayApi, orderApi, reworkApi } from '../api'

export default {
  name: 'Reworks',
  data() {
    return {
      items: [], orders: [], bays: [], tab: 'all',
      STATES: ['待回炉', '回炉中', '已验收'],
      reassignDialog: false, target: {}, reassignBayId: null
    }
  },
  computed: {
    shown() {
      return this.tab === 'all' ? this.items : this.items.filter((r) => r.reworkState === this.tab)
    },
    freeBays() {
      return this.bays.filter((b) => b.bayState !== '停用' && !this.bayFull(b))
    }
  },
  methods: {
    countOf(state) {
      return this.items.filter((r) => r.reworkState === state).length
    },
    nextOf(r) {
      const i = this.STATES.indexOf(r.reworkState)
      return i >= 0 && i < this.STATES.length - 1 ? this.STATES[i + 1] : null
    },
    loadOf(b) {
      return (b.washCount || 0) + (b.reworkCount || 0)
    },
    bayFull(b) {
      return b.seatCount != null && b.seatCount > 0 && this.loadOf(b) >= b.seatCount
    },
    orderNoOf(id) {
      const o = this.orders.find((x) => x.id === id)
      return o ? o.orderNo : '—'
    },
    plateOf(id) {
      const o = this.orders.find((x) => x.id === id)
      return o ? o.plateNo : '—'
    },
    bayCodeOf(id) {
      const b = this.bays.find((x) => x.id === id)
      return b ? b.bayCode : '—'
    },
    async load() {
      this.items = await reworkApi.list()
      this.orders = await orderApi.list()
      this.bays = await bayApi.list()
    },
    async advance(r) {
      try {
        await reworkApi.save(r.id, { reworkState: this.nextOf(r) })
        await this.load()
        this.$message.success('已推进到「' + this.STATES[this.STATES.indexOf(r.reworkState) + 1] + '」')
      } catch (e) { this.$message.error(e.message) }
    },
    openReassign(r) {
      this.target = r
      this.reassignBayId = null
      this.reassignDialog = true
    },
    async submitReassign() {
      try {
        await reworkApi.reassign(this.target.id, this.reassignBayId)
        this.reassignDialog = false
        await this.load()
        this.$message.success('已改派')
      } catch (e) { this.$message.error(e.message); await this.load() }
    }
  },
  mounted() { this.load() }
}
</script>

<style scoped>
.hd { display: flex; align-items: center; gap: 14px; margin-bottom: 14px; }
.hd h2 { margin: 0; font-size: 20px; }
.sub { flex: 1; color: #99a1a6; font-size: 12px; }
.tabs { display: flex; gap: 8px; margin-bottom: 12px; }
.tab { padding: 6px 16px; border-radius: 16px; background: #fff; border: 1px solid #e9edef;
  font-size: 12px; color: #647077; cursor: pointer; }
.tab.on { background: var(--el-color-primary); color: #fff; border-color: var(--el-color-primary); }
.table { background: #fff; border: 1px solid #e9edef; border-radius: 12px; overflow: hidden; }
.row { display: grid; grid-template-columns: 130px 90px 100px 70px 1fr 78px 130px; gap: 8px;
  align-items: center; padding: 11px 14px; border-bottom: 1px solid #f3f6f7; font-size: 13px; }
.row.head { background: #f7f9fa; color: #99a1a6; font-size: 12px; }
.mono { font-family: ui-monospace, Menlo, monospace; color: #99a1a6; font-size: 12px; }
.dim { color: #647077; font-size: 12px; }
.st { color: var(--el-color-primary-dark-2); font-size: 12px; }
.blank { color: #bbb; padding: 30px; text-align: center; }
.ghost { background: #fff; border: 1px solid var(--el-color-primary-light-7); color: var(--el-color-primary-dark-2);
  border-radius: 6px; padding: 3px 10px; font-size: 12px; cursor: pointer; margin-right: 6px; }
.fr { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.fr label { width: 62px; text-align: right; font-size: 13px; color: #647077; }
.warn { color: #c45656; font-size: 12px; padding-left: 72px; }
</style>
