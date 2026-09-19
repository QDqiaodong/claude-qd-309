<template>
  <div class="pane">
    <header class="hd"><h2>洗车单</h2><span class="sub">按状态分页签；「推进」把单子往前推一步，结账可以从会员卡扣；已完成的单可以挂回炉</span>
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
        <span class="st">{{ o.washState }}<em v-if="o.reworkOpen" class="rw-tag">回炉未结·{{ o.reworkState }}</em></span>
        <span>
          <button v-if="nextOf(o)" class="ghost" @click="advance(o)">推进</button>
          <button v-if="o.washState === '已完成'" class="ghost" @click="payWithCard(o)">卡结账</button>
          <button v-if="o.washState === '已完成'" class="ghost" :disabled="!!o.reworkOpen"
                  :title="o.reworkOpen ? '有未验收的回炉，不能再挂' : '挂一张回炉'" @click="openRework(o)">挂回炉</button>
          <button class="ghost" @click="openDetail(o)">详情</button>
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
    <el-dialog v-model="reworkDialog" title="挂一张回炉" width="460px">
      <div class="fr"><label>原单</label><span class="mono">{{ reworkOrder.orderNo }} · {{ reworkOrder.plateNo }}</span></div>
      <div class="fr" v-if="origBayUsable"><label>回炉工位</label>
        <span>先认原工位「{{ origBay ? origBay.bayName : '—' }}」</span></div>
      <template v-else>
        <div class="fr"><label>原工位</label><span class="warn">{{ origBayProblem }}</span></div>
        <div class="fr"><label>改派到</label>
          <el-select v-model="reworkForm.bayId" style="flex:1" placeholder="选一个空闲工位">
            <el-option v-for="b in freeBays" :key="b.id" :label="b.bayName + '（占用 ' + loadOf(b) + '/' + (b.seatCount ?? '不限') + '）'" :value="b.id" />
          </el-select>
        </div>
        <div class="warn indent" v-if="!freeBays.length">现在没有能改派的空闲工位，回炉先挂不上</div>
      </template>
      <div class="fr"><label>原因</label><el-input v-model="reworkForm.reason" placeholder="客人挑了什么毛病" /></div>
      <template #footer><el-button @click="reworkDialog = false">取消</el-button>
        <el-button type="primary" :disabled="!origBayUsable && !reworkForm.bayId" @click="submitRework">挂回炉</el-button></template>
    </el-dialog>
    <el-dialog v-model="detailDialog" title="洗车单详情" width="440px">
      <div class="fr"><label>单号</label><span class="mono">{{ detail.orderNo }}</span></div>
      <div class="fr"><label>车牌</label><span>{{ detail.plateNo }}</span></div>
      <div class="fr"><label>工位</label><span>{{ bayName(detail.bayId) }}</span></div>
      <div class="fr"><label>服务</label><span>{{ detail.serviceType || '—' }}</span></div>
      <div class="fr"><label>金额</label><span>¥{{ detail.price ?? 0 }}</span></div>
      <div class="fr"><label>日期</label><span>{{ detail.orderDate }}</span></div>
      <div class="fr"><label>状态</label><span>{{ detail.washState }}</span></div>
      <div class="fr"><label>回炉</label>
        <span v-if="detail.reworkOpen" class="warn">回炉未结（{{ detail.reworkState }}），验收前不能再挂、也不能把原单退回去</span>
        <span v-else>没有未结的回炉</span></div>
      <template #footer><el-button @click="detailDialog = false">知道了</el-button></template>
    </el-dialog>
  </div>
</template>

<script>
import { bayApi, cardApi, orderApi, reworkApi } from '../api'

export default {
  name: 'Orders',
  data() {
    return {
      items: [], bays: [], cards: [], reworks: [], tab: 'all',
      dialog: false, form: {},
      reworkDialog: false, reworkOrder: {}, reworkForm: {},
      detailDialog: false, detail: {},
      STATES: ['待洗', '清洗中', '已完成']
    }
  },
  computed: {
    shown() {
      return this.tab === 'all' ? this.items : this.items.filter((o) => o.washState === this.tab)
    },
    usableBays() {
      return this.bays.filter((b) => b.bayState !== '停用' && !this.bayFull(b))
    },
    freeBays() {
      return this.bays.filter((b) => b.bayState !== '停用' && !this.bayFull(b))
    },
    origBay() {
      return this.bays.find((b) => b.id === this.reworkOrder.bayId) || null
    },
    origBayUsable() {
      const b = this.origBay
      return !!b && b.bayState !== '停用' && !this.bayFull(b)
    },
    origBayProblem() {
      const b = this.origBay
      if (!b) return '原单没排过工位，得改派一个空闲工位'
      if (b.bayState === '停用') return '原工位「' + b.bayName + '」停用了，挂不回去'
      return '原工位「' + b.bayName + '」占满了（还有没洗完的车或回炉中的单），挂不回去'
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
    loadOf(b) {
      return (b.washCount || 0) + (b.reworkCount || 0)
    },
    bayFull(b) {
      return b.seatCount != null && b.seatCount > 0 && this.loadOf(b) >= b.seatCount
    },
    nextOf(o) {
      const i = this.STATES.indexOf(o.washState)
      return i >= 0 && i < this.STATES.length - 1 ? this.STATES[i + 1] : null
    },
    async load() {
      this.items = await orderApi.list()
      this.bays = await bayApi.list()
      this.cards = await cardApi.list()
      this.reworks = await reworkApi.list()
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
    },
    openRework(o) {
      this.reworkOrder = o
      this.reworkForm = { orderId: o.id, bayId: null, reason: '' }
      this.reworkDialog = true
    },
    async submitRework() {
      try {
        const payload = { orderId: this.reworkForm.orderId, reason: this.reworkForm.reason }
        if (!this.origBayUsable) payload.bayId = this.reworkForm.bayId
        await reworkApi.add(payload)
        this.reworkDialog = false
        await this.load()
        this.$message.success('回炉挂上了')
      } catch (e) { this.$message.error(e.message) }
    },
    async openDetail(o) {
      try {
        this.detail = await orderApi.get(o.id)
        this.detailDialog = true
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
.row { display: grid; grid-template-columns: 84px 96px 66px 84px 64px 96px 150px 1fr; gap: 8px;
  align-items: center; padding: 11px 14px; border-bottom: 1px solid #f3f6f7; font-size: 13px; }
.row.head { background: #f7f9fa; color: #99a1a6; font-size: 12px; }
.mono { font-family: ui-monospace, Menlo, monospace; color: #99a1a6; font-size: 12px; }
.r { text-align: right; }
.dim { color: #99a1a6; font-size: 12px; }
.st { color: var(--el-color-primary-dark-2); font-size: 12px; }
.rw-tag { display: inline-block; font-style: normal; margin-left: 6px; padding: 1px 8px; border-radius: 10px;
  background: #fdecec; color: #c45656; font-size: 11px; white-space: nowrap; }
.ghost { background: #fff; border: 1px solid var(--el-color-primary-light-7); color: var(--el-color-primary-dark-2);
  border-radius: 6px; padding: 3px 10px; font-size: 12px; cursor: pointer; margin-right: 6px; }
.ghost:disabled { border-color: #e4e7ed; color: #c0c4cc; cursor: not-allowed; }
.fr { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.fr label { width: 62px; text-align: right; font-size: 13px; color: #647077; flex-shrink: 0; }
.warn { color: #c45656; font-size: 12px; }
.indent { padding-left: 72px; margin-bottom: 12px; }
</style>
