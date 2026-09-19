import axios from 'axios'

const http = axios.create({ baseURL: '/api', timeout: 10000 })

http.interceptors.response.use(
  (res) => res.data,
  (err) => Promise.reject(new Error(err?.response?.data?.message || err.message || '接口没通'))
)

export const bayApi = {
  list: (params) => http.get('/bays', { params }),
  add: (b) => http.post('/bays', b),
  save: (id, b) => http.put(`/bays/${id}`, b)
}
export const orderApi = {
  list: (params) => http.get('/orders', { params }),
  add: (b) => http.post('/orders', b),
  save: (id, b) => http.put(`/orders/${id}`, b),
  pay: (cardId, amount) => http.post(`/orders/pay/${cardId}`, { amount })
}
export const reworkApi = {
  list: (params) => http.get('/reworks', { params }),
  create: (b) => http.post('/reworks', b),
  advance: (id) => http.post(`/reworks/${id}/advance`),
  reassign: (id, bayId) => http.put(`/reworks/${id}/bay`, { bayId })
}
export const supplyApi = {
  list: (params) => http.get('/supplies', { params }),
  add: (b) => http.post('/supplies', b),
  save: (id, b) => http.put(`/supplies/${id}`, b),
  consume: (id, quantity) => http.post(`/supplies/${id}/consume`, { quantity })
}
export const cardApi = {
  list: (params) => http.get('/cards', { params }),
  add: (b) => http.post('/cards', b),
  save: (id, b) => http.put(`/cards/${id}`, b)
}

export default http
