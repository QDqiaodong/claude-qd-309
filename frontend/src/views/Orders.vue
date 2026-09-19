<template>
  <div class="pane">
    <header class="hd"><h2>洗车单</h2><span class="sub">按状态分页签；「推进」把单子往前推一步，结账可以从会员卡扣</span>
      <button class="prime" @click="openNew">开单</button></header>
    <div class="tabs">
      <div class="tab" :class="{ on: tab === 'all' }" @click="tab = 'all'">全部 {{ items.length }}</div>
      <div v-for="s in STATES" :key="s" class="tab" :class="{ on: tab === s }" @click="tab = s">
        {{ s }} {{ countOf(s) }}
      </div>
    </div>
    <div class="table">
      <div class="row head"><span>单号</span><span>车牌</span><span>工位</span><span>服务</span>
        <span class="r">金额</span><span>日期</span><span>状态</span><span>操作</span></div>
      <div v-for="o in shown" :key="o.id" class="row">
        <span class="mono">{{ o.orderNo }}</span>
        <span>{{ o.plateNo }}</span>
        <span>{{ bayName(o.bayId) }}</span>
        <span>{{ o.serviceType || '—' }}</span>
        <span class="r">¥{{ o.price ?? 0 }}</span>
        <span class="dim">{{ o.orderDate }}</span>
        <span class="st">{{ o.washState }}</span>
        <span>
          <button v-if="nextOf(o)" class="ghost" @click="advance(o)">推进</button>
          <button v-if="o.washState === '已完成'" class="ghost" @click="payWithCard(o)">卡结账</button>
        </span>
      </div>
    </div>
    <el-dialog v-model="dialog" title="开一张洗车单" width="440px">
      <div class="fr"><label>单号</label><el-input v-model="form.orderNo" /></div>
      <div class="fr"><label>车牌</label><el-input v-model="form.plateNo" /></div>
      <div class="fr"><label>工位</label>
        <el-select v-model="form.bayId" style="flex:1">
          <el-option v-for="b in usableBays" :key="b.id" :label="b.bayName" :value="b.id" />
        </el-select></div>
      <div class="fr"><label>服务</label><el-input v-model="form.serviceType" /></div>
      <div class="fr"><label>金额</label><el-input v-model="form.price" /></div>
      <template #footer><el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script>
import { bayApi, cardApi, orderApi } from '../api'

export default {
  name: 'Orders',
  data() {
    return { items: [], bays: [], cards: [], tab: 'all', dialog: false, form: {}, STATES: ['待洗', '清洗中', '已完成'] }
  },
  computed: {
    shown() {
      return this.tab === 'all' ? this.items : this.items.filter((o) => o.washState === this.tab)
    },
    usableBays() {
      return this.bays.filter((b) => b.bayState !== '停用')
    }
  },
  methods: {
    countOf(state) {
      return this.items.filter((o) => o.washState === state).length
    },
    bayName(id) {
      const b = this.bays.find((x) => x.id === id)
      return b ? b.bayCode : '未排'
    },
    nextOf(o) {
      const i = this.STATES.indexOf(o.washState)
      return i >= 0 && i < this.STATES.length - 1 ? this.STATES[i + 1] : null
    },
    async load() {
      this.items = await orderApi.list()
      this.bays = await bayApi.list()
      this.cards = await cardApi.list()
    },
    openNew() {
      this.form = {}
      this.dialog = true
    },
    async submit() {
      try {
        await orderApi.add(this.form)
        this.dialog = false
        await this.load()
        this.$message.success('开好了')
      } catch (e) { this.$message.error(e.message) }
    },
    async advance(o) {
      try {
        await orderApi.save(o.id, { washState: this.nextOf(o) })
        await this.load()
        this.$message.success('已推进')
      } catch (e) { this.$message.error(e.message) }
    },
    async payWithCard(o) {
      const card = this.cards.find((c) => c.cardState === '正常')
      if (!card) {
        this.$message.warning('没有可用的会员卡')
        return
      }
      try {
        await orderApi.pay(card.id, o.price || 0)
        await this.load()
        this.$message.success('已从 ' + card.cardNo + ' 扣款')
      } catch (e) { this.$message.error(e.message) }
    }
  },
  mounted() { this.load() }
}
</script>

<style scoped>
.hd { display: flex; align-items: center; gap: 14px; margin-bottom: 14px; }
.hd h2 { margin: 0; font-size: 20px; }
.sub { flex: 1; color: #99a1a6; font-size: 12px; }
.prime { background: var(--el-color-primary); color: #fff; border: none; border-radius: 8px;
  padding: 8px 18px; font-size: 13px; cursor: pointer; }
.tabs { display: flex; gap: 8px; margin-bottom: 12px; }
.tab { padding: 6px 16px; border-radius: 16px; background: #fff; border: 1px solid #e9edef;
  font-size: 12px; color: #647077; cursor: pointer; }
.tab.on { background: var(--el-color-primary); color: #fff; border-color: var(--el-color-primary); }
.table { background: #fff; border: 1px solid #e9edef; border-radius: 12px; overflow: hidden; }
.row { display: grid; grid-template-columns: 96px 100px 76px 92px 70px 100px 78px 120px; gap: 8px;
  align-items: center; padding: 11px 14px; border-bottom: 1px solid #f3f6f7; font-size: 13px; }
.row.head { background: #f7f9fa; color: #99a1a6; font-size: 12px; }
.mono { font-family: ui-monospace, Menlo, monospace; color: #99a1a6; font-size: 12px; }
.r { text-align: right; }
.dim { color: #99a1a6; font-size: 12px; }
.st { color: var(--el-color-primary-dark-2); font-size: 12px; }
.ghost { background: #fff; border: 1px solid var(--el-color-primary-light-7); color: var(--el-color-primary-dark-2);
  border-radius: 6px; padding: 3px 10px; font-size: 12px; cursor: pointer; margin-right: 6px; }
.fr { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.fr label { width: 62px; text-align: right; font-size: 13px; color: #647077; }
</style>
