<template>
  <div class="pane">
    <header class="hd"><h2>会员卡</h2><span class="sub">余额条按 600 元封顶画；停卡的整张发灰</span>
      <button class="prime" @click="openNew">办卡</button></header>
    <div class="cards">
      <article v-for="c in items" :key="c.id" class="mc" :class="{ off: c.cardState !== '正常' }">
        <div class="mc-top"><span class="mc-no">{{ c.cardNo }}</span><span class="mc-level">{{ c.cardLevel || '普通卡' }}</span></div>
        <div class="mc-name">{{ c.holderName }}</div>
        <div class="mc-phone">{{ c.phone || '未留电话' }}</div>
        <div class="mc-balance">¥{{ c.balance ?? 0 }}</div>
        <div class="mc-bar"><div class="mc-fill" :style="{ width: balancePct(c) + '%' }"></div></div>
        <div class="mc-foot"><span>{{ c.cardState }}</span>
          <button class="ghost" @click="openEdit(c)">修改</button></div>
      </article>
    </div>
    <el-dialog v-model="dialog" :title="form.id ? '修改会员卡' : '办一张会员卡'" width="430px">
      <div class="fr"><label>卡号</label><el-input v-model="form.cardNo" /></div>
      <div class="fr"><label>持卡人</label><el-input v-model="form.holderName" /></div>
      <div class="fr"><label>手机号</label><el-input v-model="form.phone" /></div>
      <div class="fr"><label>余额</label><el-input v-model="form.balance" /></div>
      <div class="fr"><label>卡等级</label><el-input v-model="form.cardLevel" placeholder="普通卡 / 银卡 / 金卡" /></div>
      <div class="fr"><label>状态</label><el-input v-model="form.cardState" placeholder="正常 / 已停卡" /></div>
      <template #footer><el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script>
import { cardApi } from '../api'

export default {
  name: 'Cards',
  data() {
    return { items: [], dialog: false, form: {}, TOP: 600 }
  },
  methods: {
    balancePct(c) {
      return Math.min(100, Math.round((Number(c.balance || 0) * 100) / this.TOP))
    },
    async load() {
      this.items = await cardApi.list()
    },
    openNew() {
      this.form = { cardState: '正常' }
      this.dialog = true
    },
    openEdit(row) {
      this.form = { ...row }
      this.dialog = true
    },
    async submit() {
      try {
        if (this.form.id) await cardApi.save(this.form.id, this.form)
        else await cardApi.add(this.form)
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
.cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(236px, 1fr)); gap: 14px; }
.mc { background: #fff; border: 1px solid #e9edef; border-radius: 12px; padding: 16px; }
.mc.off { opacity: .6; background: #fafbfb; }
.mc-top { display: flex; justify-content: space-between; font-size: 12px; }
.mc-no { color: #99a1a6; }
.mc-level { color: var(--el-color-primary-dark-2); }
.mc-name { font-size: 16px; font-weight: 600; margin: 8px 0 3px; }
.mc-phone { font-size: 12px; color: #99a1a6; }
.mc-balance { font-size: 24px; font-weight: 700; color: var(--el-color-primary-dark-2); margin: 12px 0 8px; }
.mc-bar { height: 8px; background: #f2f5f6; border-radius: 4px; overflow: hidden; }
.mc-fill { height: 100%; background: var(--el-color-primary); border-radius: 4px; }
.mc-foot { display: flex; justify-content: space-between; align-items: center; margin-top: 12px;
  font-size: 12px; color: #99a1a6; }
.ghost { background: #fff; border: 1px solid var(--el-color-primary-light-7); color: var(--el-color-primary-dark-2);
  border-radius: 6px; padding: 4px 12px; font-size: 12px; cursor: pointer; }
.fr { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.fr label { width: 66px; text-align: right; font-size: 13px; color: #647077; }
</style>
